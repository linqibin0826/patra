package com.patra.ingest.app.usecase.execution.coordination;

import com.patra.common.model.StandardLiterature;
import com.patra.ingest.app.usecase.execution.converter.ProvenanceConfigConverter;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchResult;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.starter.provenance.common.adapter.AdapterRegistry;
import com.patra.starter.provenance.common.adapter.AdapterRequest;
import com.patra.starter.provenance.common.adapter.AdapterResult;
import com.patra.starter.provenance.common.adapter.AdapterResult.ErrorType;
import com.patra.starter.provenance.common.adapter.BatchExecutionParams;
import com.patra.starter.provenance.common.adapter.BatchMetadata;
import com.patra.starter.provenance.common.adapter.DataSourceAdapter;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 通用批次执行器
 *
 * <p>在六边形架构+DDD中的角色:应用层执行器,负责将批次获取工作委托给{@link DataSourceAdapter}实现。
 *
 * <p>主要职责:
 *
 * <ul>
 *   <li>通过{@link AdapterRegistry}解析正确的适配器
 *   <li>将注册表快照转换为运行时配置
 *   <li>通过{@link LiteraturePublisherOrchestrator}发布标准化文献
 *   <li>将适配器结果转换为领域层{@link BatchResult}实例
 *   <li>处理重试逻辑和失败情况
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenericBatchExecutor {

  private static final int DEFAULT_MAX_RETRY_TIMES = 3;
  private static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 1_000L;
  private static final long MAX_BACKOFF_MILLIS = 30_000L;

  private final AdapterRegistry adapterRegistry;
  private final LiteraturePublisherOrchestrator literaturePublisherOrchestrator;
  private final ProvenanceConfigConverter configConverter;

  /**
   * 执行单个批次
   *
   * <p>业务流程:
   *
   * <ol>
   *   <li>解析适配器并转换配置
   *   <li>构建请求并调用适配器获取数据
   *   <li>处理重试逻辑(指数退避)
   *   <li>发布标准化文献
   *   <li>返回批次执行结果
   * </ol>
   *
   * @param context 执行上下文
   * @param batch 批次定义
   * @return 批次执行结果
   */
  public BatchResult execute(ExecutionContext context, Batch batch) {
    Objects.requireNonNull(context, "执行上下文不能为空");
    Objects.requireNonNull(batch, "批次定义不能为空");

    long startAt = System.currentTimeMillis();
    int batchNo = batch.batchNo();
    String provenanceCode = context.provenanceCode();
    String operationCode = context.operationCode();

    log.info(
        "批次执行开始 provenanceCode={} operationCode={} batchNo={} runId={}",
        provenanceCode,
        operationCode,
        batchNo,
        context.runId());

    try {
      DataSourceAdapter adapter = adapterRegistry.getAdapter(provenanceCode);
      ProvenanceConfig runtimeConfig =
          configConverter.convert(provenanceCode, context.configSnapshot());

      // 构建批次执行参数(查询条件 + 完整参数,包含分页信息)
      BatchExecutionParams executionParams =
          new BatchExecutionParams(batch.query(), batch.params());

      // 构建批次元数据(批次编号 + 游标令牌)
      BatchMetadata metadata = new BatchMetadata(batch.batchNo(), batch.cursorToken());

      AdapterRequest request =
          AdapterRequest.builder()
              .operationCode(operationCode)
              .config(runtimeConfig)
              .executionParams(executionParams)
              .metadata(metadata)
              .build();

      int maxAttempts = resolveMaxAttempts(runtimeConfig);
      long initialDelayMillis = resolveInitialDelayMillis(runtimeConfig);
      AdapterResult adapterResult = null;
      int attempt = 0;

      while (true) {
        attempt++;
        adapterResult = adapter.fetchData(request);
        if (adapterResult.success()) {
          break;
        }

        boolean retriable = adapterResult.errorType() == ErrorType.RETRIABLE;
        if (!retriable || attempt >= maxAttempts) {
          if (retriable) {
            log.warn(
                "适配器重试次数耗尽 provenanceCode={} batchNo={} attemptsUsed={}/{}",
                provenanceCode,
                batchNo,
                attempt,
                maxAttempts);
          }
          return handleFailure(context, batch, adapterResult, System.currentTimeMillis() - startAt);
        }

        long backoffMillis = calculateBackoffDelay(initialDelayMillis, attempt);
        log.warn(
            "适配器可重试失败 provenanceCode={} batchNo={} attempt={} of {} retryDelay={}ms message={}",
            provenanceCode,
            batchNo,
            attempt,
            maxAttempts,
            backoffMillis,
            safeMessage(adapterResult.errorMessage()));
        try {
          TimeUnit.MILLISECONDS.sleep(backoffMillis);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          long duration = System.currentTimeMillis() - startAt;
          log.error(
              "批次执行在重试期间被中断 provenanceCode={} batchNo={} duration={}ms",
              provenanceCode,
              batchNo,
              duration,
              interrupted);
          return BatchResult.failure(batchNo, "适配器重试被中断");
        }
      }

      LiteraturePublisherOrchestrator.PublishResult publishResult =
          publishLiterature(context, batchNo, adapterResult.literatures());
      logAdapterWarnings(adapterResult, provenanceCode, batchNo);

      long duration = System.currentTimeMillis() - startAt;
      log.info(
          "批次执行成功 provenanceCode={} operationCode={} batchNo={} fetchedCount={} duration={}ms attempts={}",
          provenanceCode,
          operationCode,
          batchNo,
          adapterResult.fetchedCount(),
          duration,
          attempt);

      int publishedCount = publishResult.publishedCount();
      return BatchResult.success(
          batchNo, publishedCount, adapterResult.nextCursorToken(), publishResult.storageKey());

    } catch (Exception ex) {
      long duration = System.currentTimeMillis() - startAt;
      log.error(
          "批次执行意外失败 provenanceCode={} batchNo={} duration={}ms",
          provenanceCode,
          batchNo,
          duration,
          ex);
      return BatchResult.failure(batchNo, safeMessage(ex.getMessage()));
    }
  }

  /**
   * 处理批次执行失败
   *
   * @param context 执行上下文
   * @param batch 批次定义
   * @param result 适配器结果
   * @param durationMillis 执行耗时(毫秒)
   * @return 失败的批次结果
   */
  private BatchResult handleFailure(
      ExecutionContext context, Batch batch, AdapterResult result, long durationMillis) {
    String provenanceCode = context.provenanceCode();
    int batchNo = batch.batchNo();
    ErrorType errorType = result.errorType();
    String reason = safeMessage(result.errorMessage());
    log.warn(
        "批次执行失败 provenanceCode={} batchNo={} errorType={} duration={}ms message={}",
        provenanceCode,
        batchNo,
        errorType,
        durationMillis,
        reason);
    return BatchResult.failure(batchNo, buildFailureMessage(errorType, reason));
  }

  /**
   * 记录适配器警告信息
   *
   * @param adapterResult 适配器结果
   * @param provenanceCode Provenance代码
   * @param batchNo 批次编号
   */
  private void logAdapterWarnings(AdapterResult adapterResult, String provenanceCode, int batchNo) {
    if (adapterResult.errorMessage() != null
        && adapterResult.errorType() == ErrorType.PARTIAL_SUCCESS) {
      log.warn(
          "适配器报告部分成功 provenanceCode={} batchNo={} warning={}",
          provenanceCode,
          batchNo,
          adapterResult.errorMessage());
    }
  }

  private LiteraturePublisherOrchestrator.PublishResult publishLiterature(
      ExecutionContext context, int batchNo, List<StandardLiterature> literatures) {
    List<StandardLiterature> payload = literatures == null ? List.of() : List.copyOf(literatures);
    if (payload.isEmpty()) {
      return LiteraturePublisherOrchestrator.PublishResult.builder()
          .publishedCount(0)
          .storageKey(null)
          .build();
    }
    LiteraturePublisherOrchestrator.PublishContext publishContext =
        LiteraturePublisherOrchestrator.PublishContext.builder()
            .runId(context.runId())
            .batchNo(batchNo)
            .provenanceCode(context.provenanceCode())
            .build();
    return literaturePublisherOrchestrator.publish(payload, publishContext);
  }

  private int resolveMaxAttempts(ProvenanceConfig runtimeConfig) {
    if (runtimeConfig != null && runtimeConfig.retry() != null) {
      Integer configured = runtimeConfig.retry().maxRetryTimes();
      if (configured != null) {
        int sanitized = Math.max(configured, 0);
        if (sanitized >= Integer.MAX_VALUE - 1) {
          return Integer.MAX_VALUE;
        }
        return sanitized + 1;
      }
    }
    return DEFAULT_MAX_RETRY_TIMES + 1;
  }

  private long resolveInitialDelayMillis(ProvenanceConfig runtimeConfig) {
    if (runtimeConfig != null && runtimeConfig.retry() != null) {
      Integer configured = runtimeConfig.retry().initialDelayMillis();
      if (configured != null && configured > 0) {
        return configured.longValue();
      }
    }
    return DEFAULT_INITIAL_BACKOFF_MILLIS;
  }

  private long calculateBackoffDelay(long initialDelayMillis, int attemptIndex) {
    long base = initialDelayMillis > 0 ? initialDelayMillis : DEFAULT_INITIAL_BACKOFF_MILLIS;
    int retryCount = Math.max(attemptIndex, 1);
    long delay = base;
    for (int i = 1; i < retryCount; i++) {
      if (delay >= MAX_BACKOFF_MILLIS) {
        return MAX_BACKOFF_MILLIS;
      }
      long next = delay * 2L;
      if (next <= 0L) {
        return MAX_BACKOFF_MILLIS;
      }
      delay = Math.min(next, MAX_BACKOFF_MILLIS);
    }
    return Math.min(delay, MAX_BACKOFF_MILLIS);
  }

  private String buildFailureMessage(ErrorType type, String reason) {
    if (type == null || type == ErrorType.NONE) {
      return reason;
    }
    return "[%s] %s".formatted(type.name(), reason);
  }

  /**
   * 获取安全的错误消息
   *
   * @param message 原始消息
   * @return 非空的错误消息
   */
  private String safeMessage(String message) {
    return StringUtils.hasText(message) ? message : "未知适配器失败";
  }
}
