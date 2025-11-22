package com.patra.ingest.infra.mapper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.provenance.api.params.PubMedParamKeys;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.query.QuerySession;
import com.patra.ingest.infra.mapper.ProviderParameterMapper;
import com.patra.ingest.infra.mapper.StateTokenKeys;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed 参数映射器。
///
/// 将通用的 offset/limit 映射为 PubMed 特定的 retstart/retmax 参数。
///
/// **PubMed 分页机制**：
///
/// - **retstart**: 起始偏移量（从 0 开始）
///   - **retmax**: 返回记录数量
///   - **WebEnv + query_key**: History Server 会话令牌（可选）
///
/// **映射规则**：
///
/// ```java
/// batch.offset()  → retstart
/// batch.limit()   → retmax
/// session.stateToken("webEnv")    → WebEnv
/// session.stateToken("queryKey")  → query_key
/// ```
///
/// @author Patra Architecture Team
/// @since 0.3.0
@Component
@Slf4j
public class PubmedParameterMapper implements ProviderParameterMapper {

  @Override
  public ProvenanceCode getSupportedProvenance() {
    return ProvenanceCode.PUBMED;
  }

  @Override
  public JsonNode mapParameters(Batch batch, JsonNode baseParams, QuerySession session) {
    ObjectMapper mapper = JsonMapperHolder.getObjectMapper();

    // 1. 复制基础参数或创建新的 ObjectNode
    ObjectNode params =
        baseParams != null && baseParams.isObject()
            ? ((ObjectNode) baseParams).deepCopy()
            : mapper.createObjectNode();

    // 2. 添加 PubMed 分页参数
    params.put(PubMedParamKeys.RETSTART, batch.offset());
    params.put(PubMedParamKeys.RETMAX, batch.limit());

    // 3. 添加 History Server 会话令牌（如果有）
    if (session.hasStateToken()) {
      Map<String, String> stateToken = session.stateToken().orElseThrow();

      String webEnv = stateToken.get(StateTokenKeys.PUBMED_WEBENV);
      String queryKey = stateToken.get(StateTokenKeys.PUBMED_QUERY_KEY);

      if (webEnv != null) {
        params.put(PubMedParamKeys.WEBENV, webEnv);
        log.debug(
            "添加 PubMed History Server webEnv: batchNo={}, webEnv={}", batch.batchNo(), webEnv);
      }

      if (queryKey != null) {
        params.put(PubMedParamKeys.QUERY_KEY, queryKey);
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
        session.hasStateToken());

    return params;
  }
}
