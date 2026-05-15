package dev.linqibin.patra.ingest.app.usecase.execution.coordination;

import dev.linqibin.patra.ingest.domain.port.PublicationStoragePort;
import dev.linqibin.patra.ingest.domain.port.StorageMetadataPort;
import dev.linqibin.patra.ingest.domain.port.TechnicalRetryPort;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.common.model.CanonicalPublication;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/// 出版物发布器。
///
/// 在六边形架构+DDD中的角色: Handler 内部协调组件，负责协调出版物发布工作流。
///
/// 设计理念: 通过在应用层显式编排两个不同的操作，使跨服务集成变得清晰:
///
/// - 通过 {@link PublicationStoragePort} 上传到对象存储（技术基础设施）
/// - 通过 {@link StorageMetadataPort} 记录元数据（与 patra-object-storage 服务的业务集成）
///
/// 主要职责:
///
/// - 编排存储和元数据记录的顺序
/// - 处理跨服务集成失败
/// - 将失败的元数据记录委托给重试机制
/// - 为应用层调用者提供统一的发布结果
///
/// 这种设计遵循六边形架构原则，在应用层显式编排，而不是隐藏在基础设施适配器中。
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicationPublisher {

  private static final String BUSINESS_TYPE = "publication-batch";

  private final PublicationStoragePort publicationStoragePort;
  private final StorageMetadataPort storageMetadataPort;
  private final TechnicalRetryPort technicalRetryPort;
  private final ObjectMapper objectMapper;

  /// 发布标准化出版物
  ///
  /// 业务流程:
  ///
  /// @param publication 领域标准化的出版物列表
  /// @param context 发布上下文(包含执行元数据)
  /// @return 发布结果(包含存储位置)
  public PublishResult publish(List<CanonicalPublication> publication, PublishContext context) {
    List<CanonicalPublication> safePublication = publication == null ? List.of() : publication;

    // 步骤1: 存储到对象存储
    PublicationStoragePort.StorageContext storageContext = toStorageContext(context);
    PublicationStoragePort.StorageResult storageResult =
        publicationStoragePort.store(safePublication, storageContext);

    log.info(
        "出版物已存储 bucket={} key={} size={} bytes count={}",
        storageResult.bucketName(),
        storageResult.objectKey(),
        storageResult.fileSize(),
        storageResult.publicationCount());

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

    } catch (StorageMetadataPort.StorageMetadataException e) {
      // 元数据记录失败（远程调用失败），委托给重试机制
      log.warn("元数据记录失败,委托给重试机制 storageKey={}", storageResult.storageKey(), e);
      delegateToRetry(storageResult, context, e);
    }

    return PublishResult.of(storageResult.storageKey(), storageResult.publicationCount());
  }

  private PublicationStoragePort.StorageContext toStorageContext(PublishContext context) {
    return PublicationStoragePort.StorageContext.builder()
        .runId(context.runId())
        .batchNo(context.batchNo())
        .provenanceCode(context.provenanceCode())
        .build();
  }

  private StorageMetadataPort.MetadataRequest buildMetadataRequest(
      PublicationStoragePort.StorageResult storageResult, PublishContext context) {
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
      PublicationStoragePort.StorageResult storageResult, PublishContext context) {
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

  /// 将失败的元数据记录委托给技术重试机制
  ///
  /// 使用{@link TechnicalRetryPort}通过outbox发布者框架确保一致的重试处理。
  ///
  /// @param storageResult 成功上传的存储结果
  /// @param context 发布上下文(用于可追溯性)
  /// @param error 导致失败的异常
  private void delegateToRetry(
      PublicationStoragePort.StorageResult storageResult, PublishContext context, Exception error) {
    try {
      // 构建元数据请求用于重试
      StorageMetadataPort.MetadataRequest metadataRequest =
          buildMetadataRequest(storageResult, context);

      String payloadJson = objectMapper.writeValueAsString(metadataRequest);
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

  /// 发布结果
  ///
  /// 包含存储位置信息。
  ///
  /// @param storageKey 完整的存储标识符
  /// @param publishedCount 已发布的出版物数量
  public record PublishResult(String storageKey, int publishedCount) {

    /// 创建发布结果。
    ///
    /// @param storageKey 存储标识符
    /// @param publishedCount 发布数量
    /// @return 发布结果
    public static PublishResult of(String storageKey, int publishedCount) {
      return new PublishResult(storageKey, publishedCount);
    }
  }

  /// 发布上下文
  ///
  /// 包含执行元数据。
  ///
  /// @param runId 任务运行标识符
  /// @param batchNo 执行批次编号
  /// @param provenanceCode 标准化的数据源标识符
  public record PublishContext(Long runId, int batchNo, ProvenanceCode provenanceCode) {

    /// 创建发布上下文。
    ///
    /// @param runId 运行 ID
    /// @param batchNo 批次号
    /// @param provenanceCode 数据源代码
    /// @return 发布上下文
    public static PublishContext of(Long runId, int batchNo, ProvenanceCode provenanceCode) {
      return new PublishContext(runId, batchNo, provenanceCode);
    }
  }
}
