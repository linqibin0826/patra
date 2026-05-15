package com.patra.ingest.app.usecase.execution.session;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.expression.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.expression.ExprCompilationResult;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import com.patra.ingest.domain.port.ExpressionCompilerPort;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import dev.linqibin.patra.common.model.DataType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// 执行上下文加载器实现。
///
/// 核心职责: 恢复配置和表达式快照(Task → Slice → Plan),验证哈希值,并编译表达式。
///
/// 设计要点:
///
/// - 读取 Task: 获取 sliceId、exprHash、paramsJson、provenanceCode、endpointName
///   - 读取 Slice: 获取 planId 和执行窗口信息
///   - 读取 Plan: 获取 provenanceConfigSnapshotJson
///   - 通过 ExpressionCompilerPort 编译表达式 → query/params/normalizedExpression
///   - 验证 exprHash 以确保配置完整性
///   - 返回包含所有必要执行信息的 ExecutionContext
///
/// 错误处理:
///
/// - Task/Slice/Plan 缺失 → IllegalArgumentException
///   - 表达式编译失败 → IllegalStateException
///   - exprHash 不匹配 → IllegalStateException(完整性违规)
///
/// 日志策略: 成功加载上下文时记录 INFO;哈希验证失败时记录 WARN。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionContextLoaderImpl implements ExecutionContextLoader {

  private final TaskRepository taskRepository;
  private final PlanSliceRepository sliceRepository;
  private final PlanRepository planRepository;
  private final ExpressionCompilerPort expressionCompiler;
  private final ObjectMapper objectMapper;

  /// 加载执行上下文(配置恢复 + 表达式编译)。
  ///
  /// @param taskId 任务 ID
  /// @param runId 运行 ID
  /// @return 执行上下文
  /// @throws IllegalArgumentException 如果任务未找到
  @Override
  public ExecutionContext loadContext(Long taskId, Long runId) {
    // 查询任务并委托给重载方法
    TaskAggregate task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务未找到 taskId=" + taskId));

    return loadContext(task, runId);
  }

  /// 加载执行上下文(配置恢复 + 表达式编译) — 优化版本,避免重新加载 Task。
  ///
  /// 加载流程:
  ///
  /// @param task 任务聚合根
  /// @param runId 运行 ID
  /// @return 执行上下文
  /// @throws IllegalArgumentException 如果 Slice 或 Plan 未找到
  /// @throws IllegalStateException 如果表达式编译失败或哈希不匹配
  @Override
  public ExecutionContext loadContext(TaskAggregate task, Long runId) {
    Long taskId = task.getId().value();

    // 步骤1: 读取 Slice
    log.debug("为任务加载执行上下文 taskId={} sliceId={}", taskId, task.getSliceId());
    PlanSliceAggregate slice =
        sliceRepository
            .findById(task.getSliceId().value())
            .orElseThrow(() -> new IllegalArgumentException("切片未找到 sliceId=" + task.getSliceId()));

    // 步骤2: 读取 Plan
    log.debug("为切片加载计划 sliceId={} planId={}", slice.getId(), slice.getPlanId());
    PlanAggregate plan =
        planRepository
            .findById(slice.getPlanId().value())
            .orElseThrow(() -> new IllegalArgumentException("计划未找到 planId=" + slice.getPlanId()));

    // 步骤3: 解析配置快照为 ProvenanceConfigSnapshot
    ProvenanceConfigSnapshot configSnapshot =
        parseConfigSnapshot(plan.getProvenanceConfigSnapshotJson());

    // 步骤4: 编译表达式
    // 表达式编译委托给 ExpressionCompilerPort(在 infra 层实现)
    // 端口实现将 JSON 表达式快照转换为 Expr 对象并调用 ExprCompiler
    // 使用 Slice 的表达式快照(经过 plan_slice 后),而非 Plan 的原始快照
    log.debug("为任务编译表达式 taskId={} provenanceCode={}", taskId, task.getProvenanceCode());
    String exprSnapshotJson = slice.getExprSnapshotJson();
    if (exprSnapshotJson == null || exprSnapshotJson.isBlank()) {
      log.warn("切片的 exprSnapshotJson 为空,回退到计划的原始快照 sliceId={}", slice.getId());
      exprSnapshotJson = plan.getExprProtoSnapshotJson();
    }

    // 创建编译请求(不包含 endpointName,默认为 null)
    String provenanceCodeStr =
        task.getProvenanceCode() != null ? task.getProvenanceCode().getCode() : null;
    ExprCompilationRequest compilationRequest =
        new ExprCompilationRequest(
            provenanceCodeStr, exprSnapshotJson // 使用 Slice 的表达式快照
            );

    // 步骤5: 编译表达式并验证结果
    ExprCompilationResult compilationResult = expressionCompiler.compile(compilationRequest);

    if (!compilationResult.isValid()) {
      throw new IllegalStateException(
          "表达式编译失败 taskId=" + taskId + " reason=" + compilationResult.validationMessage());
    }

    // 步骤6: 验证 exprHash 以确保完整性
    // 比较 Task 的 exprHash 与 Slice 的 exprHash(非 Plan 的)
    if (!task.getExprHash().equals(slice.getExprHash())) {
      throw new IllegalStateException(
          String.format(
              "表达式哈希不匹配;中止执行 taskId=%d expected=%s actual=%s",
              taskId, slice.getExprHash(), task.getExprHash()));
    }

    // 步骤7: 从 JSON 解析窗口规格
    WindowSpec windowSpec = parseWindowSpec(slice.getWindowSpecJson());

    // 步骤8: 构建 ExecutionContext
    log.info(
        "执行上下文已加载 taskId={} runId={} provenanceCode={} endpointName={}",
        taskId,
        runId,
        task.getProvenanceCode(),
        task.getOperationCode());

    // TODO: 后续需要从 Provenance 配置中获取实际的 DataType
    // 目前所有 Provenance 都默认使用 PUBLICATION 类型，待注册中心添加 dataType 字段后再修改
    DataType dataType = DataType.PUBLICATION;

    return new ExecutionContext(
        taskId,
        runId,
        plan.getId().value(),
        slice.getId().value(),
        task.getScheduleInstanceId().value(), // 来自 TaskAggregate
        task.getProvenanceCode(),
        task.getOperationCode(),
        dataType,
        configSnapshot,
        task.getExprHash(),
        compilationResult.query(),
        compilationResult.params(),
        compilationResult.normalizedExpression(),
        windowSpec);
  }

  /// 从切片窗口规格 JSON 解析 WindowSpec。
  ///
  /// 该方法使用多态的 WindowSpec 值对象处理不同的窗口类型:
  ///
  /// - TIME - 时间窗口
  ///   - ID_RANGE - ID 范围窗口
  ///   - CURSOR_LANDMARK - 游标地标窗口
  ///   - VOLUME_BUDGET - 容量预算窗口
  ///   - SINGLE - 单次窗口
  ///
  /// @param windowSpecJson 窗口规格 JSON 字符串
  /// @return WindowSpec 实例,如果 JSON 为空则返回 null
  /// @throws IllegalStateException 如果 WindowSpec 解析失败
  private WindowSpec parseWindowSpec(String windowSpecJson) {
    if (windowSpecJson == null || windowSpecJson.isBlank()) {
      return null;
    }
    try {
      JsonNode spec = objectMapper.readTree(windowSpecJson);
      @SuppressWarnings("unchecked")
      Map<String, Object> map = objectMapper.convertValue(spec, Map.class);
      return WindowSpec.fromMap(map);
    } catch (Exception e) {
      log.error("从 JSON 解析 WindowSpec 失败: {}", windowSpecJson, e);
      throw new IllegalStateException("WindowSpec 解析失败,JSON: " + windowSpecJson, e);
    }
  }

  /// 将 JSON 字符串解析为 JsonNode。
  ///
  /// @param json JSON 字符串
  /// @return JsonNode 实例,如果 JSON 为空则返回空对象节点
  /// @throws IllegalStateException 如果 JSON 解析失败
  private JsonNode parseJson(String json) {
    if (json == null || json.isBlank()) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      log.error("解析 JSON 失败: {}", json, e);
      throw new IllegalStateException("JSON 解析失败", e);
    }
  }

  /// 将 JSON 字符串解析为 ProvenanceConfigSnapshot。
  ///
  /// @param json JSON 字符串
  /// @return ProvenanceConfigSnapshot 实例
  /// @throws IllegalStateException 如果 JSON 为空或解析失败
  private ProvenanceConfigSnapshot parseConfigSnapshot(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalStateException("配置快照 JSON 不能为空");
    }
    try {
      return objectMapper.readValue(json, ProvenanceConfigSnapshot.class);
    } catch (Exception e) {
      log.error("从 JSON 解析配置快照失败: {}", json, e);
      throw new IllegalStateException("配置快照解析失败", e);
    }
  }
}
