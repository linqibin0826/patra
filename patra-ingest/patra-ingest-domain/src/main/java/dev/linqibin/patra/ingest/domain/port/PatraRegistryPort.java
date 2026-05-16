package dev.linqibin.patra.ingest.domain.port;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.ingest.domain.model.enums.OperationCode;
import dev.linqibin.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;

/// Patra Registry 访问端口(六边形架构 - Domain → Infrastructure)。
///
/// **职责**:
///
/// - 为应用服务提供单一入口以调用 Registry
///   - 根据 provenance/operation 组合获取 provenance 配置快照
///   - 隐藏协议/客户端细节(HTTP Interface/gRPC),使 Domain 层保持解耦
///
/// **分层**: 接口定义在 Domain 层;基础设施实现位于 `infra.rpc.registry`。 应用服务仅通过此端口进行编排。
///
/// **错误语义(指导)**:
///
/// - 不可恢复的问题(如 4xx 或缺失数据)应转换为领域/应用异常(例如 `IngestConfigurationException`)
///   - 可恢复的问题(如 5xx 或超时)可以重试/降级(返回最小快照)或传递给调用方进行更高级别处理
///   - 实现应记录 trace id 和远程错误代码以便故障排查;接口将具体异常类型留给调用方
///
/// **线程安全**: 实现必须是无状态的或对并发重用安全;配置依赖由 Spring 管理。
///
/// **端口语义**: 此接口是六边形架构中的 **输出端口(Output Port)**,定义在 Domain
/// 层,由基础设施层(Infrastructure)实现,确保领域逻辑与 RPC 技术解耦。
public interface PatraRegistryPort {

  /// 获取 provenance/operation 对的配置快照。
  ///
  /// **业务含义**: 快照应包含 Registry 集成所需的静态参数(窗口、分页、HTTP 重试、速率限制等)。 实现可以在 Registry
  /// 暂时不可用时返回最小快照,但必须记录降级。
  ///
  /// @param provenanceCode Provenance 标识符(例如 PUBMED/EPMC)
  /// @param operationCode 操作类型(例如 HARVEST/BACKFILL/UPDATE)
  /// @return Registry 配置快照(绝不为 `null`;允许具有缩减范围的回退快照)
  /// @throws RuntimeException 当发生不可恢复的配置问题时
  ProvenanceConfigSnapshot fetchConfig(ProvenanceCode provenanceCode, OperationCode operationCode);
}
