package com.patra.ingest.app.usecase.execution.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.model.vo.ExecutionWindow;
import com.patra.ingest.domain.model.vo.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.ExprCompilationResult;
import com.patra.ingest.domain.port.ExpressionCompilerPort;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 执行上下文加载器实现。
 * <p>
 * 职责：从 Task → Slice → Plan 还原配置快照与表达式快照，校验哈希，编译表达式。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>读取 Task：获取 sliceId、exprHash、paramsJson、provenanceCode、operationCode。</li>
 *   <li>读取 Slice：获取 planId、窗口信息（executionWindow）。</li>
 *   <li>读取 Plan：获取 provenanceConfigSnapshotJson 配置快照。</li>
 *   <li>调用 ExpressionCompilerPort 编译表达式，获取 query/params/normalizedExpression。</li>
 *   <li>校验 exprHash：确保配置未被篡改。</li>
 *   <li>返回 ExecutionContext，包含所有执行所需的上下文信息。</li>
 * </ul>
 * </p>
 * <p>
 * 异常处理：
 * <ul>
 *   <li>Task/Slice/Plan 不存在：抛出 IllegalArgumentException。</li>
 *   <li>表达式编译失败：抛出 IllegalStateException。</li>
 *   <li>exprHash 不匹配：抛出 IllegalStateException（配置被篡改）。</li>
 * </ul>
 * </p>
 * <p>
 * 日志策略：INFO 记录上下文加载关键信息；WARN 记录哈希校验失败。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionContextLoaderImpl implements ExecutionContextLoader {

    private final TaskRepository taskRepository;
    private final PlanSliceRepository sliceRepository;
    private final PlanRepository planRepository;
    private final ExpressionCompilerPort expressionCompiler;
    private final ObjectMapper objectMapper;

    /**
     * 加载执行上下文（配置还原 + 表达式编译）。
     *
     * @param taskId 任务ID
     * @param runId 运行ID
     * @return 执行上下文
     */
    @Override
    public ExecutionContext loadContext(Long taskId, Long runId) {
        // Query task and delegate to overloaded method
        TaskAggregate task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在 taskId=" + taskId));

        return loadContext(task, runId);
    }

    /**
     * 加载执行上下文（配置还原 + 表达式编译）- 优化版本，避免重复查询Task。
     *
     * @param task 任务聚合（已查询）
     * @param runId 运行ID
     * @return 执行上下文
     */
    @Override
    public ExecutionContext loadContext(TaskAggregate task, Long runId) {
        Long taskId = task.getId();

        // 2. 读取 Slice
        PlanSliceAggregate slice = sliceRepository.findById(task.getSliceId())
            .orElseThrow(() -> new IllegalArgumentException("切片不存在 sliceId=" + task.getSliceId()));

        // 3. 读取 Plan
        PlanAggregate plan = planRepository.findById(slice.getPlanId())
            .orElseThrow(() -> new IllegalArgumentException("计划不存在 planId=" + slice.getPlanId()));

        // 4. 解析配置快照为 ProvenanceConfigSnapshot 对象
        ProvenanceConfigSnapshot configSnapshot = parseConfigSnapshot(plan.getProvenanceConfigSnapshotJson());

        JsonNode paramsJson = parseJson(task.getParamsJson());

        // 5. Compile expression
        // Expression compilation is delegated to ExpressionCompilerPort (implemented in infra layer)
        // The port implementation converts JSON expression snapshot to Expr object and invokes ExprCompiler
        // Use slice's expression snapshot (after plan_slice), not plan's original snapshot
        String exprSnapshotJson = slice.getExprSnapshotJson();
        if (exprSnapshotJson == null || exprSnapshotJson.isBlank()) {
            log.warn("[INGEST][APP] slice exprSnapshotJson is null, fallback to plan's original snapshot sliceId={}", slice.getId());
            exprSnapshotJson = plan.getExprProtoSnapshotJson();
        }

        ExprCompilationRequest compilationRequest = new ExprCompilationRequest(
            task.getProvenanceCode(),
            task.getOperationCode(),
            exprSnapshotJson  // Use slice's expression snapshot
        );

        // Compile expression and validate result
        ExprCompilationResult compilationResult = expressionCompiler.compile(compilationRequest);

        if (!compilationResult.isValid()) {
            throw new IllegalStateException(
                "表达式编译失败 taskId=" + taskId
                + " reason=" + compilationResult.validationMessage()
            );
        }

        // 6. 校验 exprHash：确保表达式未被篡改
        // Compare task's exprHash with slice's exprHash (not plan's)
        if (!task.getExprHash().equals(slice.getExprHash())) {
            throw new IllegalStateException(
                String.format(
                    "表达式哈希不匹配，拒绝执行 taskId=%d expected=%s actual=%s",
                    taskId, slice.getExprHash(), task.getExprHash()
                )
            );
        }

        // 7. TODO 解析执行窗口（从 sliceSpecJson 中提取, 应该使用 com.patra.ingest.domain.model.vo.SliceSpecDefinition, 不能写死时间窗口。
        ExecutionWindow executionWindow =
            parseExecutionWindow(slice.getSliceSpecJson());

        // 8. 构建 ExecutionContext
        log.info("[INGEST][APP] execution context loaded taskId={} runId={} provenanceCode={} operationCode={}",
                 taskId, runId, task.getProvenanceCode(), task.getOperationCode());

        return new ExecutionContext(
            taskId,
            runId,
            task.getProvenanceCode(),
            task.getOperationCode(),
            configSnapshot,
            task.getExprHash(),
            compilationResult.query(),
            compilationResult.params(),
            compilationResult.normalizedExpression(),
            executionWindow
        );
    }

    /**
     * 从 sliceSpecJson 中解析执行窗口。
     */
    private ExecutionWindow parseExecutionWindow(String sliceSpecJson) {
        if (sliceSpecJson == null || sliceSpecJson.isBlank()) {
            return ExecutionWindow.empty();
        }
        try {
            JsonNode spec = objectMapper.readTree(sliceSpecJson);
            java.time.Instant windowFrom = spec.has("windowFrom") && !spec.get("windowFrom").isNull()
                ? Instant.parse(spec.get("windowFrom").asText())
                : null;
            java.time.Instant windowTo = spec.has("windowTo") && !spec.get("windowTo").isNull()
                ? java.time.Instant.parse(spec.get("windowTo").asText())
                : null;
            return new ExecutionWindow(windowFrom, windowTo);
        } catch (Exception e) {
            log.error("[INGEST][APP] failed to parse execution window from sliceSpecJson: {}", sliceSpecJson, e);
            return ExecutionWindow.empty();
        }
    }

    /**
     * 解析 JSON 字符串为 JsonNode。
     */
    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("[INGEST][APP] failed to parse json: {}", json, e);
            throw new IllegalStateException("JSON 解析失败", e);
        }
    }

    /**
     * 解析 JSON 字符串为 ProvenanceConfigSnapshot 对象。
     */
    private ProvenanceConfigSnapshot parseConfigSnapshot(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("配置快照 JSON 不能为空");
        }
        try {
            return objectMapper.readValue(json, ProvenanceConfigSnapshot.class);
        } catch (Exception e) {
            log.error("[INGEST][APP] failed to parse config snapshot from json: {}", json, e);
            throw new IllegalStateException("配置快照解析失败", e);
        }
    }
}
