package com.patra.ingest.infra.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.fetch.FetchMetadata;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PubMed 参数映射器。
 *
 * <p>将通用的 offset/limit 映射为 PubMed 特定的 retstart/retmax 参数。
 *
 * <p><strong>PubMed 分页机制</strong>：
 *
 * <ul>
 *   <li><b>retstart</b>: 起始偏移量（从 0 开始）
 *   <li><b>retmax</b>: 返回记录数量
 *   <li><b>WebEnv + query_key</b>: History Server 会话令牌（可选）
 * </ul>
 *
 * <p><strong>映射规则</strong>：
 *
 * <pre>{@code
 * batch.offset()  → retstart
 * batch.limit()   → retmax
 * metadata.stateToken("webEnv")    → WebEnv
 * metadata.stateToken("queryKey")  → query_key
 * }</pre>
 *
 * @author Patra Architecture Team
 * @since 0.3.0
 */
@Component
@Slf4j
public class PubmedParameterMapper implements ProviderParameterMapper {

  @Override
  public ProvenanceCode getSupportedProvenance() {
    return ProvenanceCode.PUBMED;
  }

  @Override
  public JsonNode mapParameters(Batch batch, JsonNode baseParams, FetchMetadata metadata) {
    ObjectMapper mapper = JsonMapperHolder.getObjectMapper();

    // 1. 复制基础参数或创建新的 ObjectNode
    ObjectNode params =
        baseParams != null && baseParams.isObject()
            ? ((ObjectNode) baseParams).deepCopy()
            : mapper.createObjectNode();

    // 2. 添加 PubMed 分页参数
    params.put("retstart", batch.offset());
    params.put("retmax", batch.limit());

    // 3. 添加 History Server 会话令牌（如果有）
    if (metadata.hasStateToken()) {
      Map<String, String> stateToken = metadata.stateToken().orElseThrow();

      String webEnv = stateToken.get("webEnv");
      String queryKey = stateToken.get("queryKey");

      if (webEnv != null) {
        params.put("WebEnv", webEnv);
        log.debug(
            "添加 PubMed History Server webEnv: batchNo={}, webEnv={}", batch.batchNo(), webEnv);
      }

      if (queryKey != null) {
        params.put("query_key", queryKey);
        log.debug(
            "添加 PubMed History Server query_key: batchNo={}, queryKey={}",
            batch.batchNo(),
            queryKey);
      }
    }

    log.debug(
        "PubMed 参数映射完成: batchNo={}, offset={}, limit={}, hasSession={}",
        batch.batchNo(),
        batch.offset(),
        batch.limit(),
        metadata.hasStateToken());

    return params;
  }
}
