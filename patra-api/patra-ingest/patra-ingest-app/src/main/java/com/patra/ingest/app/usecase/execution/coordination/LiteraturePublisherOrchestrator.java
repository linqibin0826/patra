package com.patra.ingest.app.usecase.execution.coordination;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.CanonicalLiterature;
import com.patra.ingest.domain.port.LiteratureStoragePort;
import com.patra.ingest.domain.port.StorageMetadataPort;
import com.patra.ingest.domain.port.TechnicalRetryPort;
import feign.FeignException;
import feign.RetryableException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * 文献发布编排器
 *
 * <p>在六边形架构+DDD中的角色:应用层编排器,负责协调文献发布工作流。
 *
 * <p>设计理念:通过在应用层显式编排两个不同的操作,使跨服务集成变得清晰:
 *
 * <ul>
 *   <li>通过{@link LiteratureStoragePort}上传到对象存储(技术基础设施)
 *   <li>通过{@link StorageMetadataPort}记录元数据(与patra-object-storage服务的业务集成)
 * </ul>
 *
 * <p>主要职责:
 *
 * <ul>
 *   <li>编排存储和元数据记录的顺序
 *   <li>处理跨服务集成失败
 *   <li>将失败的元数据记录委托给重试机制
 *   <li>为应用层调用者提供统一的发布结果
 * </ul>
 *
 * <p>这种设计遵循六边形架构原则,在应用层显式编排,而不是隐藏在基础设施适配器中。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiteraturePublisherOrchestrator {

  private static final String BUSINESS_TYPE = "literature-batch";

  private final LiteratureStoragePort literatureStoragePort;
  private final StorageMetadataPort storageMetadataPort;
  private final TechnicalRetryPort technicalRetryPort;

  /**
   * 发布标准化文献
   *
   * <p>业务流程:
   *
   * <ol>
   *   <li>上传文献到对象存储
   *   <li>记录元数据到patra-object-storage服务
   *   <li>处理元数据记录失败(委托给重试机制)
   * </ol>
   *
   * @param literature 领域标准化的文献列表
   * @param context 发布上下文(包含执行元数据)
   * @return 发布结果(包含存储位置)
   */
  public PublishResult publish(List<CanonicalLiterature> literature, PublishContext context) {
    List<CanonicalLiterature> safeLiterature =
        literature == null ? Collections.emptyList() : literature;

    // 步骤1: 存储到对象存储
    LiteratureStoragePort.StorageContext storageContext = toStorageContext(context);
    LiteratureStoragePort.StorageResult storageResult =
        literatureStoragePort.store(safeLiterature, storageContext);

    log.info(
        "文献已存储 bucket={} key={} size={} bytes count={}",
        storageResult.bucketName(),
        storageResult.objectKey(),
        storageResult.fileSize(),
        storageResult.literatureCount());

    // 步骤2: 记录元数据到patra-object-storage服务(带错误处理)
    try {
      StorageMetadataPort.MetadataRequest metadataRequest =
          buildMetadataRequest(storageResult, context);

      StorageMetadataPort.MetadataResult metadataResult =
          storageMetadataPort.recordUpload(metadataRequest);

      log.info(
          "元数据记录成功 storageKey={} metadataId={}",
          storageResult.storageKey(),
          metadataResult.metadataId());

    } catch (FeignException e) {
      handleMetadataRecordFailure(e, storageResult, context);
    } catch (Exception e) {
      if (e instanceof RetryableException) {
        log.warn("元数据记录超时,委托给重试机制 storageKey={}", storageResult.storageKey(), e);
        delegateToRetry(storageResult, context, e);
      } else {
        log.error("记录元数据时发生意外错误,委托给重试机制 storageKey={}", storageResult.storageKey(), e);
        delegateToRetry(storageResult, context, e);
      }
    }

    return PublishResult.builder()
        .storageKey(storageResult.storageKey())
        .publishedCount(storageResult.literatureCount())
        .build();
  }

  private LiteratureStoragePort.StorageContext toStorageContext(PublishContext context) {
    return LiteratureStoragePort.StorageContext.builder()
        .runId(context.runId())
        .batchNo(context.batchNo())
        .provenanceCode(context.provenanceCode())
        .build();
  }

  private StorageMetadataPort.MetadataRequest buildMetadataRequest(
      LiteratureStoragePort.StorageResult storageResult, PublishContext context) {
    Map<String, Object> correlation = buildCorrelationData(storageResult, context);

    return StorageMetadataPort.MetadataRequest.builder()
        .storageKey(storageResult.storageKey())
        .bucketName(storageResult.bucketName())
        .objectKey(storageResult.objectKey())
        .fileSize(storageResult.fileSize())
        .contentType("application/json")
        .md5(storageResult.md5())
        .sha256(storageResult.sha256())
        .serviceName(null) // Will be populated by adapter
        .businessType(BUSINESS_TYPE)
        .businessId(buildBusinessId(context))
        .correlation(correlation)
        .providerType(null) // Will be populated by adapter
        .build();
  }

  private Map<String, Object> buildCorrelationData(
      LiteratureStoragePort.StorageResult storageResult, PublishContext context) {
    Map<String, Object> correlation = new LinkedHashMap<>();
    correlation.put("batchNo", context.batchNo());
    correlation.put("provenanceCode", safeProvenance(context.provenanceCode()));
    if (context.runId() != null) {
      correlation.put("runId", context.runId());
    }
    correlation.put("storageKey", storageResult.storageKey());
    return correlation;
  }

  private String buildBusinessId(PublishContext context) {
    String provenance = safeProvenance(context.provenanceCode());
    String runIdSegment = context.runId() != null ? String.valueOf(context.runId()) : "na";
    return provenance + "-" + context.batchNo() + "-" + runIdSegment;
  }

  /**
   * 处理元数据记录失败
   *
   * @param exception Feign异常
   * @param storageResult 存储结果
   * @param context 发布上下文
   */
  private void handleMetadataRecordFailure(
      FeignException exception,
      LiteratureStoragePort.StorageResult storageResult,
      PublishContext context) {
    int status = exception.status();
    if (status >= 500 || status == 503 || status == -1) {
      log.warn(
          "patra-object-storage服务不可用 (HTTP {}),委托给重试机制 storageKey={}", status, storageResult.storageKey());
      delegateToRetry(storageResult, context, exception);
      return;
    }
    if (status >= 400 && status < 500) {
      log.error(
          "无效的元数据记录请求 (HTTP {}),需要人工调查 StorageKey={} bucket={} key={}",
          status,
          storageResult.storageKey(),
          storageResult.bucketName(),
          storageResult.objectKey(),
          exception);
      return;
    }
    log.error("意外的Feign错误,委托给重试机制 storageKey={}", storageResult.storageKey(), exception);
    delegateToRetry(storageResult, context, exception);
  }

  /**
   * 将失败的元数据记录委托给技术重试机制
   *
   * <p>使用{@link TechnicalRetryPort}通过outbox发布者框架确保一致的重试处理。
   *
   * @param storageResult 成功上传的存储结果
   * @param context 发布上下文(用于可追溯性)
   * @param error 导致失败的异常
   */
  private void delegateToRetry(
      LiteratureStoragePort.StorageResult storageResult, PublishContext context, Exception error) {
    try {
      // 重构元数据请求用于重试
      StorageMetadataPort.MetadataRequest metadataRequest =
          buildMetadataRequest(storageResult, context);

      String payloadJson = metadataRequest.toString();
      Long aggregateId = context.runId() != null ? context.runId() : 0L;

      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("provenanceCode", safeProvenance(context.provenanceCode()));
      metadata.put("batchNo", context.batchNo());
      metadata.put("storageKey", storageResult.storageKey());
      metadata.put("fileSize", storageResult.fileSize());
      String traceId = MDC.get("traceId");
      metadata.put("traceId", traceId != null ? traceId : "");

      TechnicalRetryPort.RetryContext retryContext =
          TechnicalRetryPort.RetryContext.builder()
              .operationType("METADATA_RECORD")
              .aggregateId(aggregateId)
              .payload(payloadJson)
              .metadata(metadata)
              .build();

      technicalRetryPort.publishRetry(retryContext);

      log.info(
          "元数据记录请求已委托给重试机制 runId={} storageKey={}", context.runId(), storageResult.storageKey());

    } catch (Exception e) {
      log.error(
          "严重错误: 委托给重试机制失败 storageKey={} runId={}", storageResult.storageKey(), context.runId(), e);
    }
  }

  private String safeProvenance(ProvenanceCode provenanceCode) {
    return provenanceCode != null ? provenanceCode.getCode().toLowerCase(Locale.ROOT) : "unknown";
  }

  /**
   * 发布结果
   *
   * <p>包含存储位置信息。
   *
   * @param storageKey 完整的存储标识符
   * @param publishedCount 已发布的文献数量
   */
  @Builder
  public record PublishResult(String storageKey, int publishedCount) {}

  /**
   * 发布上下文
   *
   * <p>包含执行元数据。
   *
   * @param runId 任务运行标识符
   * @param batchNo 执行批次编号
   * @param provenanceCode 标准化的数据源标识符
   */
  @Builder
  public record PublishContext(Long runId, int batchNo, ProvenanceCode provenanceCode) {}
}
