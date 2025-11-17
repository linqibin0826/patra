package com.patra.ingest.app.usecase.execution.coordination;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.CanonicalPublication;
import com.patra.common.model.DataType;
import com.patra.common.type.TypeReference;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchResult;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.query.QuerySession;
import com.patra.ingest.domain.port.ProvenanceDataPort;
import com.patra.ingest.domain.port.ProvenanceDataPort.DataFetchResult;
import com.patra.ingest.domain.port.ProvenanceDataPort.DataFetchResult.ErrorType;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 通用批次执行器
 *
 * <p>在六边形架构+DDD中的角色:应用层编排器,负责协调批次执行流程。
 *
 * <p>主要职责:
 *
 * <ul>
 *   <li>通过{@link ProvenanceDataPort}获取出版物数据
 *   <li>通过{@link PublicationPublisherOrchestrator}发布标准化出版物
 *   <li>将数据获取结果转换为领域层{@link BatchResult}实例
 *   <li>处理失败情况和异常
 * </ul>
 *
 * <p>注意:重试逻辑、配置转换等技术细节已在基础设施层处理,本执行器聚焦于业务流程编排。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenericBatchExecutor {

  private final ProvenanceDataPort provenanceDataPort;
  private final PublicationPublisherOrchestrator publicationPublisherOrchestrator;

  /**
   * 执行单个批次
   *
   * <p>业务流程:
   *
   * <ol>
   *   <li>调用数据源端口获取出版物数据
   *   <li>处理获取失败的情况
   *   <li>发布标准化出版物
   *   <li>返回批次执行结果
   * </ol>
   *
   * @param context 执行上下文
   * @param batch 批次定义
   * @param querySession 查询会话
   * @return 批次执行结果
   */
  public BatchResult execute(
      ExecutionContext context,
      Batch batch,
      QuerySession querySession) {
    Objects.requireNonNull(context, "执行上下文不能为空");
    Objects.requireNonNull(batch, "批次定义不能为空");

    long startAt = System.currentTimeMillis();
    int batchNo = batch.batchNo();
    ProvenanceCode provenanceCode = context.provenanceCode();
    String operationCode = context.operationCode();

    log.info(
        "批次执行开始 provenanceCode={} operationCode={} batchNo={} runId={}",
        provenanceCode,
        operationCode,
        batchNo,
        context.runId());

    try {
      // 调用数据源端口获取数据（默认获取PUBLICATION类型）
      // TODO: 未来从ExecutionContext或Batch中获取dataType(短期内不做，保持简单. To AGENTS)
      DataType dataType = DataType.PUBLICATION;
      TypeReference<CanonicalPublication> typeRef = new TypeReference<>() {};
      DataFetchResult<CanonicalPublication> fetchResult =
          provenanceDataPort.fetchData(context, dataType, typeRef, batch, querySession);

      if (!fetchResult.success()) {
        return handleFailure(context, batch, fetchResult, System.currentTimeMillis() - startAt);
      }

      PublicationPublisherOrchestrator.PublishResult publishResult =
          publishPublication(context, batchNo, fetchResult.data());
      logDataFetchWarnings(fetchResult, provenanceCode, batchNo);

      long duration = System.currentTimeMillis() - startAt;
      log.info(
          "批次执行成功 provenanceCode={} operationCode={} batchNo={} fetchedCount={} duration={}ms",
          provenanceCode,
          operationCode,
          batchNo,
          fetchResult.fetchedCount(),
          duration);

      int publishedCount = publishResult.publishedCount();
      return BatchResult.success(
          batchNo, publishedCount, fetchResult.nextCursorToken(), publishResult.storageKey());

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
   * @param result 数据获取结果
   * @param durationMillis 执行耗时(毫秒)
   * @return 失败的批次结果
   */
  private BatchResult handleFailure(
      ExecutionContext context, Batch batch, DataFetchResult result, long durationMillis) {
    ProvenanceCode provenanceCode = context.provenanceCode();
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
   * 记录数据获取警告信息
   *
   * @param fetchResult 数据获取结果
   * @param provenanceCode Provenance代码
   * @param batchNo 批次编号
   */
  private void logDataFetchWarnings(
      DataFetchResult fetchResult, ProvenanceCode provenanceCode, int batchNo) {
    if (fetchResult.errorMessage() != null
        && fetchResult.errorType() == ErrorType.PARTIAL_SUCCESS) {
      log.warn(
          "数据获取报告部分成功 provenanceCode={} batchNo={} warning={}",
          provenanceCode,
          batchNo,
          fetchResult.errorMessage());
    }
  }

  /**
   * 发布出版物到下游
   *
   * @param context 执行上下文
   * @param batchNo 批次编号
   * @param publications 出版物列表
   * @return 发布结果
   */
  private PublicationPublisherOrchestrator.PublishResult publishPublication(
      ExecutionContext context, int batchNo, List<CanonicalPublication> publications) {
    List<CanonicalPublication> payload = publications == null ? List.of() : List.copyOf(publications);
    if (payload.isEmpty()) {
      return PublicationPublisherOrchestrator.PublishResult.builder()
          .publishedCount(0)
          .storageKey(null)
          .build();
    }
    ProvenanceCode provenanceCode = context.provenanceCode();
    PublicationPublisherOrchestrator.PublishContext publishContext =
        PublicationPublisherOrchestrator.PublishContext.builder()
            .runId(context.runId())
            .batchNo(batchNo)
            .provenanceCode(provenanceCode)
            .build();
    return publicationPublisherOrchestrator.publish(payload, publishContext);
  }

  /**
   * 构建失败消息
   *
   * @param type 错误类型
   * @param reason 错误原因
   * @return 格式化的失败消息
   */
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
    return StringUtils.hasText(message) ? message : "未知失败";
  }
}
