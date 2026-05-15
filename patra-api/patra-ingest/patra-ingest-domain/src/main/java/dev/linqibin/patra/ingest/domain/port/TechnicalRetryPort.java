package dev.linqibin.patra.ingest.domain.port;

import java.util.Map;
import lombok.Builder;

/// 技术重试端口(六边形架构 - Domain → Infrastructure)。
///
/// **职责**: 将失败的操作持久化到 Outbox 以进行重试。基础设施适配器使用此端口委托重试逻辑, 而不是直接操作 OutboxMessageRepository。这确保通过
/// Outbox 模式统一处理技术故障, 具有统一的指标、日志记录和批处理。
///
/// ### 设计理由
///
/// - **关注点分离**: 基础设施层专注于技术操作(RPC、I/O),将重试编排委托给应用层
///   - **框架一致性**: 所有 Outbox 操作都通过 AbstractOutboxPublisher 进行,以实现统一行为
///   - **依赖倒置**: Infra 依赖于 Domain 端口,App 实现端口,保持正确的依赖方向
///
/// ### 使用示例
///
/// ```java
/// @Component
/// public class ExternalServiceAdapter {
///   private final TechnicalRetryPort retryPort;
///
///   public void callExternalService(Request request) {
///     try {
///       externalClient.call(request); catch (Exception e) {
///       // 委托给重试发布器,而不是直接操作 Outbox
///       retryPort.publishRetry(
///         RetryContext.builder()
///           .operationType("EXTERNAL_SERVICE_CALL")
///           .aggregateId(request.getId())
///           .payload(serializeRequest(request))
///           .metadata(Map.of("traceId", MDC.get("traceId")))
///           .build()
///       );
/// ```
///
/// **端口语义**: 此接口是六边形架构中的 **输出端口(Output Port)**,定义在 Domain
/// 层,由应用层(Application)实现,确保基础设施层能够委托重试逻辑而不引入循环依赖。
///
/// @author linqibin
/// @since 0.1.0
public interface TechnicalRetryPort {

  /// 将技术重试请求发布到 Outbox 以进行异步处理。
  ///
  /// **业务含义**: 重试请求将被持久化并最终由 Outbox 转发机制处理。
  ///
  /// @param context 重试上下文,包含操作详情
  void publishRetry(RetryContext context);

  /// 技术重试上下文,封装失败操作的详情。
  ///
  /// @param operationType 操作类型标识符(例如 "METADATA_RECORD", "RPC_CALL")
  /// @param aggregateId 聚合根标识符(用于分区和关联)
  /// @param payload 序列化的操作负载(推荐 JSON)
  /// @param metadata 头部的额外元数据(traceId、provenanceCode 等)
  @Builder
  record RetryContext(
      String operationType, Long aggregateId, String payload, Map<String, Object> metadata) {}
}
