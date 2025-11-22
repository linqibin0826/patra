package com.patra.ingest.infra.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.query.QuerySession;

/// 数据源参数映射器接口。
///
/// **职责**：将通用的 Batch 模型映射为数据源特定的请求参数。
///
/// **架构定位**：
///
/// - 位于 Infrastructure 层（适配器）
///   - 封装数据源的技术细节（参数名、分页机制）
///   - 将领域模型（offset/limit）转换为技术协议（retstart/retmax、cursorMark 等）
///
/// **设计原则**：
///
/// - 每个数据源一个 Mapper 实现
///   - Mapper 负责理解数据源的分页机制
///   - 通过 {@link ProviderParameterMapperRegistry} 注册和路由
///
/// **示例**：
///
/// ```java
/// // PubmedParameterMapper 将 offset/limit 映射为 retstart/retmax
/// public class PubmedParameterMapper implements ProviderParameterMapper {
///     @Override
///     public ProvenanceCode getSupportedProvenance() {
///         return ProvenanceCode.PUBMED;
///
///     @Override
///     public JsonNode mapParameters(Batch batch, JsonNode baseParams, QuerySession session) {
///         ObjectNode params = ...;
///         params.put("retstart", batch.offset());
///         params.put("retmax", batch.limit());
///         return params;
/// ```
///
/// @author Patra Architecture Team
/// @since 0.3.0
/// @see ProviderParameterMapperRegistry
public interface ProviderParameterMapper {

  /// 获取此 Mapper 支持的数据源代码。
  ///
  /// 用于注册表自动注册和路由。
  ///
  /// @return 数据源代码枚举（如 PUBMED、EPMC、DOAJ）
  ProvenanceCode getSupportedProvenance();

  /// 将通用 Batch 映射为数据源特定的请求参数。
  ///
  /// **映射逻辑**：
  ///
  /// **参数说明**：
  ///
  /// - `batch`: 纯领域模型，包含 batchNo、query、offset、limit
  ///   - `baseParams`: 基础查询参数（来自 ExecutionContext.compiledParams()），如 datetype、sort 等
  ///   - `session`: 查询会话，包含总记录数、会话令牌等
  ///
  /// **返回值**：完整的请求参数 JsonNode，包含基础参数 + 分页参数 + 会话令牌（如有）
  ///
  /// @param batch 批次（纯领域模型）
  /// @param baseParams 基础查询参数（可能为 null）
  /// @param session 查询会话
  /// @return 完整的请求参数
  JsonNode mapParameters(Batch batch, JsonNode baseParams, QuerySession session);
}
