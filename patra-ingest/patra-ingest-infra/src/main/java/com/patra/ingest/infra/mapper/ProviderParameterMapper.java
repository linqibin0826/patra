package com.patra.ingest.infra.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.fetch.FetchMetadata;

/**
 * 数据源参数映射器接口。
 *
 * <p><strong>职责</strong>：将通用的 Batch 模型映射为数据源特定的请求参数。
 *
 * <p><strong>架构定位</strong>：
 *
 * <ul>
 *   <li>位于 Infrastructure 层（适配器）
 *   <li>封装数据源的技术细节（参数名、分页机制）
 *   <li>将领域模型（offset/limit）转换为技术协议（retstart/retmax、cursorMark 等）
 * </ul>
 *
 * <p><strong>设计原则</strong>：
 *
 * <ul>
 *   <li>每个数据源一个 Mapper 实现
 *   <li>Mapper 负责理解数据源的分页机制
 *   <li>通过 {@link ProviderParameterMapperRegistry} 注册和路由
 * </ul>
 *
 * <p><strong>示例</strong>：
 *
 * <pre>{@code
 * // PubmedParameterMapper 将 offset/limit 映射为 retstart/retmax
 * public class PubmedParameterMapper implements ProviderParameterMapper {
 *     @Override
 *     public ProvenanceCode getSupportedProvenance() {
 *         return ProvenanceCode.PUBMED;
 *     }
 *
 *     @Override
 *     public JsonNode mapParameters(Batch batch, JsonNode baseParams, FetchMetadata metadata) {
 *         ObjectNode params = ...;
 *         params.put("retstart", batch.offset());
 *         params.put("retmax", batch.limit());
 *         return params;
 *     }
 * }
 * }</pre>
 *
 * @author Patra Architecture Team
 * @since 0.3.0
 * @see ProviderParameterMapperRegistry
 */
public interface ProviderParameterMapper {

  /**
   * 获取此 Mapper 支持的数据源代码。
   *
   * <p>用于注册表自动注册和路由。
   *
   * @return 数据源代码枚举（如 PUBMED、EPMC、DOAJ）
   */
  ProvenanceCode getSupportedProvenance();

  /**
   * 将通用 Batch 映射为数据源特定的请求参数。
   *
   * <p><strong>映射逻辑</strong>：
   *
   * <ol>
   *   <li>复制或创建基础参数 JsonNode
   *   <li>添加分页参数（将 batch.offset()/batch.limit() 映射为数据源特定的参数名）
   *   <li>如果 metadata.hasStateToken()，添加会话令牌（如 PubMed 的 webEnv/queryKey）
   *   <li>返回完整的参数 JsonNode
   * </ol>
   *
   * <p><strong>参数说明</strong>：
   *
   * <ul>
   *   <li>{@code batch}: 纯领域模型，包含 batchNo、query、offset、limit
   *   <li>{@code baseParams}: 基础查询参数（来自 ExecutionContext.compiledParams()），如 datetype、sort 等
   *   <li>{@code metadata}: 抓取元数据，包含总记录数、会话令牌等
   * </ul>
   *
   * <p><strong>返回值</strong>：完整的请求参数 JsonNode，包含基础参数 + 分页参数 + 会话令牌（如有）
   *
   * @param batch 批次（纯领域模型）
   * @param baseParams 基础查询参数（可能为 null）
   * @param metadata 抓取元数据
   * @return 完整的请求参数
   */
  JsonNode mapParameters(Batch batch, JsonNode baseParams, FetchMetadata metadata);
}
