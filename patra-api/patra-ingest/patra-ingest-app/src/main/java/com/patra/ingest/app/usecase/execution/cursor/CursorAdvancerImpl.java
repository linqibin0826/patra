package com.patra.ingest.app.usecase.execution.cursor;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.domain.model.entity.CursorEvent;
import com.patra.ingest.domain.model.enums.CursorDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.vo.cursor.CursorLineage;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import com.patra.ingest.domain.model.vo.shared.NamespaceKey;
import com.patra.ingest.domain.port.CursorEventRepository;
import com.patra.ingest.domain.port.CursorRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 游标推进器实现。
 *
 * <p>职责:根据批次结果推进游标水位线,使用乐观锁避免并发冲突。
 *
 * <p>设计要点:
 *
 * <ul>
 *   <li>通过 provenanceCode/operationCode/cursorKey/namespace 查询游标
 *   <li>根据 WindowSpec 策略计算新水位线(TIME 策略使用 windowTo)
 *   <li>通过 Cursor.advanceTo() 更新游标;保存时检查版本号
 *   <li>捕获 OptimisticLockingFailureException;返回 false 表示需要重试
 *   <li>首次推进时不存在游标则创建新游标
 * </ul>
 *
 * <p>命名空间策略:
 *
 * <ul>
 *   <li>GLOBAL: 跨任务共享的全局游标
 *   <li>TASK: 按任务隔离的游标(通过 taskId 隔离)
 *   <li>PLAN: 按计划隔离的游标(通过 planId 隔离)
 * </ul>
 *
 * <p>日志记录:
 *
 * <ul>
 *   <li>INFO: 推进成功(from/to)
 *   <li>WARN: 乐观锁冲突(重试)
 *   <li>DEBUG: 游标查找、创建
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CursorAdvancerImpl implements CursorAdvancer {

  private final CursorRepository cursorRepository;
  private final CursorEventRepository cursorEventRepository;

  /** 推进游标水位线。 */
  @Override
  public boolean advance(ExecutionContext context, Long taskId, Long runId, Long batchId) {
    // 1) 提取游标参数
    ProvenanceCode provenanceCode = context.provenanceCode();
    String operationCode = context.operationCode();

    WindowSpec windowSpec = context.windowSpec();
    if (windowSpec == null) {
      log.debug("跳过游标推进: 无窗口规范 taskId={} runId={}", taskId, runId);
      return true; // 无窗口规范,跳过推进
    }

    // 2) 根据策略提取水位线和窗口边界
    Instant newWatermark = extractWatermark(windowSpec, taskId, runId);
    if (newWatermark == null) {
      log.debug(
          "跳过游标推进: 非 TIME 策略或无水位线 strategy={} taskId={} runId={}",
          windowSpec.strategy(),
          taskId,
          runId);
      return true; // 非 TIME 策略当前不推进水位线
    }

    // 提取窗口边界(仅用于 TIME/DATE 策略)
    Instant windowFrom = null;
    Instant windowTo = null;
    if (windowSpec instanceof WindowSpec.Time timeSpec) {
      windowFrom = timeSpec.from();
      windowTo = timeSpec.to();
    }

    // 3) 确定游标键和命名空间
    String cursorKey = determineCursorKey(windowSpec);
    String namespaceScope = "GLOBAL";
    String namespaceKey = NamespaceKey.global().key();

    log.debug(
        "推进游标 provenanceCode={} operationCode={} cursorKey={} newWatermark={} taskId={} runId={}",
        provenanceCode,
        operationCode,
        cursorKey,
        newWatermark,
        taskId,
        runId);

    try {
      // 4) 从执行上下文和参数构建血缘上下文
      CursorLineage lineage =
          new CursorLineage(
              context.scheduleInstanceId(), // 来自 TaskAggregate 通过 ExecutionContext
              context.planId(),
              context.sliceId(),
              context.taskId(),
              context.runId(),
              batchId); // 来自方法参数(最后成功的批次)

      // 5) 查找当前游标
      Optional<Cursor> cursorOpt =
          cursorRepository.find(
              provenanceCode, operationCode, cursorKey, namespaceScope, namespaceKey);

      Cursor cursor;
      Instant prevWatermark = null;
      String prevValue = null;

      if (cursorOpt.isPresent()) {
        // 5.1 游标存在:更新水位线和血缘
        cursor = cursorOpt.get();
        Instant oldWatermark = cursor.getCurrentWatermark();
        prevWatermark = oldWatermark;
        prevValue = oldWatermark != null ? oldWatermark.toString() : null;

        if (log.isDebugEnabled()) {
          log.debug(
              "找到游标 provenanceCode={} endpointName={} currentWatermark={}",
              provenanceCode,
              operationCode,
              oldWatermark);
        }

        // 推进水位线并跟踪表达式哈希(领域层确保单调性)
        Cursor.AdvancementResult result =
            cursor.advanceTo(newWatermark, lineage, context.exprHash());

        // 处理表达式哈希变化:重置游标到初始位置
        if (result == Cursor.AdvancementResult.EXPRESSION_CHANGED) {
          log.info(
              "游标 [{}] 的表达式已变化: {} -> {}, 重置游标到初始位置",
              cursorKey,
              cursor.getExprHash(),
              context.exprHash());

          // 使用新的表达式哈希创建新游标
          cursor =
              Cursor.create(
                  provenanceCode,
                  operationCode,
                  cursorKey,
                  namespaceScope,
                  namespaceKey,
                  newWatermark,
                  lineage,
                  context.exprHash());

          // 更新跟踪变量用于事件创建
          prevWatermark = null;
          prevValue = null;
        }

        log.info(
            "游标已推进 provenanceCode={} endpointName={} from={} to={} taskId={} runId={} planId={} sliceId={}",
            provenanceCode,
            operationCode,
            oldWatermark,
            newWatermark,
            taskId,
            runId,
            context.planId(),
            context.sliceId());
      } else {
        // 5.2 游标缺失:使用血缘和表达式哈希创建(首次推进)
        cursor =
            Cursor.create(
                provenanceCode,
                operationCode,
                cursorKey,
                namespaceScope,
                namespaceKey,
                newWatermark,
                lineage,
                context.exprHash());

        prevWatermark = null;
        prevValue = null;

        log.info(
            "游标已创建 provenanceCode={} endpointName={} watermark={} exprHash={} taskId={} runId={} planId={} sliceId={}",
            provenanceCode,
            operationCode,
            newWatermark,
            context.exprHash(),
            taskId,
            runId,
            context.planId(),
            context.sliceId());
      }

      // 6) 保存游标(乐观锁检查)
      cursorRepository.save(cursor);

      // 7) 生成幂等键用于事件去重
      String idempotentKey =
          generateIdempotentKey(
              provenanceCode != null ? provenanceCode.getCode() : null,
              operationCode,
              cursorKey,
              namespaceScope,
              namespaceKey != null ? namespaceKey : "",
              prevValue,
              newWatermark.toString(),
              runId,
              batchId);

      // 8) 确定推进方向
      CursorDirection direction = determineDirection(operationCode);

      // 9) 创建并保存游标推进事件
      CursorEvent event =
          CursorEvent.create(
              provenanceCode,
              operationCode,
              cursorKey,
              namespaceScope,
              namespaceKey,
              CursorType.TIME,
              prevValue,
              newWatermark.toString(),
              prevWatermark,
              newWatermark,
              direction,
              idempotentKey,
              lineage,
              context.exprHash(), // 使用上下文中的新 expr_hash
              windowFrom, // 窗口开始(非 TIME 策略为 null)
              windowTo); // 窗口结束(非 TIME 策略为 null)

      cursorEventRepository.save(event);

      if (log.isDebugEnabled()) {
        log.debug(
            "游标事件已记录 idempotentKey={} direction={} taskId={} runId={}",
            idempotentKey,
            direction,
            taskId,
            runId);
      }

      return true;

    } catch (OptimisticLockingFailureException e) {
      // 乐观锁冲突(版本不匹配)
      log.warn(
          "游标推进冲突 provenanceCode={} endpointName={} taskId={} runId={}",
          provenanceCode,
          operationCode,
          taskId,
          runId);
      return false; // 表示需要重试

    } catch (Exception e) {
      log.error(
          "游标推进失败 provenanceCode={} endpointName={} taskId={} runId={}",
          provenanceCode,
          operationCode,
          taskId,
          runId,
          e);
      throw new IllegalStateException("游标推进失败", e);
    }
  }

  /**
   * 从 WindowSpec 提取水位线(策略感知)。
   *
   * <p>当前仅 TIME 策略支持基于时间戳的水位线推进。
   *
   * @param windowSpec 窗口规范
   * @param taskId 任务 ID(用于日志)
   * @param runId 运行 ID(用于日志)
   * @return 水位线时间戳,如果策略不支持则返回 null
   */
  private Instant extractWatermark(WindowSpec windowSpec, Long taskId, Long runId) {
    return switch (windowSpec.strategy()) {
      case TIME, DATE -> {
        // TIME 和 DATE 策略都使用基于时间的窗口
        WindowSpec.Time timeSpec = (WindowSpec.Time) windowSpec;
        yield timeSpec.to(); // 使用窗口结束时间作为水位线
      }
      case ID_RANGE, CURSOR_LANDMARK, VOLUME_BUDGET, SINGLE -> {
        // 这些策略当前不使用基于时间的水位线
        // 未来: ID_RANGE 可能使用基于数字 ID 的水位线
        yield null;
      }
      case HYBRID -> {
        // 未来: 从 HYBRID 规范提取时间组件
        log.warn("HYBRID 策略水位线提取尚未实现 taskId={} runId={}", taskId, runId);
        yield null;
      }
    };
  }

  /**
   * 根据窗口策略确定游标键。
   *
   * @param windowSpec 窗口规范
   * @return 游标键标识符
   */
  private String determineCursorKey(WindowSpec windowSpec) {
    return switch (windowSpec.strategy()) {
      case TIME, DATE -> "TIME"; // TIME 和 DATE 都使用基于时间的游标键
      case ID_RANGE -> "ID";
      case CURSOR_LANDMARK -> "CURSOR";
      case VOLUME_BUDGET, SINGLE, HYBRID -> "GLOBAL";
    };
  }

  /**
   * 生成幂等键用于游标事件去重。
   *
   * <p>格式: SHA256(provenance|operation|cursorKey|nsScope|nsKey|prev|new|runId|batchId)
   *
   * <p>确保相同的推进(相同上下文和水位线转换)生成相同的幂等键,防止重复的事件记录。
   *
   * @param provenanceCode 来源代码
   * @param operationCode 操作代码
   * @param cursorKey 游标键
   * @param namespaceScopeCode 命名空间范围代码
   * @param namespaceKey 命名空间键(如果为 null 则为空字符串)
   * @param prevValue 上一个水位线值(如果为 null 则为 NULL 字符串)
   * @param newValue 新水位线值
   * @param runId 运行标识符
   * @param batchId 批次标识符
   * @return SHA256 哈希作为幂等键(64 字符十六进制字符串)
   */
  private String generateIdempotentKey(
      String provenanceCode,
      String operationCode,
      String cursorKey,
      String namespaceScopeCode,
      String namespaceKey,
      String prevValue,
      String newValue,
      Long runId,
      Long batchId) {

    String composite =
        String.format(
            "%s|%s|%s|%s|%s|%s|%s|%s|%s",
            provenanceCode,
            operationCode,
            cursorKey,
            namespaceScopeCode,
            namespaceKey != null ? namespaceKey : "",
            prevValue != null ? prevValue : "NULL",
            newValue,
            runId,
            batchId);

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(composite.getBytes(StandardCharsets.UTF_8));

      // Convert byte array to hex string
      StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
      for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 算法是保证可用的,不应该发生此异常
      throw new IllegalStateException("SHA-256 算法不可用", e);
    }
  }

  /**
   * 根据操作代码确定游标推进方向。
   *
   * <p>BACKFILL 操作将游标向后移动(历史数据采集),而所有其他操作将游标向前移动(增量采集)。
   *
   * @param operationCode 操作代码(HARVEST/BACKFILL/UPDATE/METRICS)
   * @return 如果操作是 backfill 则返回 BACKFILL,否则返回 FORWARD
   */
  private CursorDirection determineDirection(String operationCode) {
    return "BACKFILL".equalsIgnoreCase(operationCode)
        ? CursorDirection.BACKFILL
        : CursorDirection.FORWARD;
  }
}
