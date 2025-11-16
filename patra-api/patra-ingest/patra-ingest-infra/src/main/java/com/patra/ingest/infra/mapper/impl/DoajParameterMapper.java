package com.patra.ingest.infra.mapper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.provenance.api.params.DoajParamKeys;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.query.QuerySession;
import com.patra.ingest.infra.mapper.ProviderParameterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DOAJ 参数映射器。
 *
 * <p>将通用的 offset/limit 映射为 DOAJ 特定的分页参数。
 *
 * <p><strong>DOAJ 分页机制</strong>：
 *
 * <ul>
 *   <li><b>page</b>: 页码（从 1 开始）
 *   <li><b>pageSize</b>: 每页记录数
 * </ul>
 *
 * <p><strong>映射规则</strong>：
 *
 * <pre>{@code
 * batch.offset() / batch.limit() + 1 → page（计算页码）
 * batch.limit()  → pageSize
 * }</pre>
 *
 * <p><strong>注意</strong>：DOAJ 使用页码分页（从1开始），需要从 offset 计算出 page。
 *
 * @author Patra Architecture Team
 * @since 0.3.0
 */
@Component
@Slf4j
public class DoajParameterMapper implements ProviderParameterMapper {

  @Override
  public ProvenanceCode getSupportedProvenance() {
    return ProvenanceCode.DOAJ;
  }

  @Override
  public JsonNode mapParameters(Batch batch, JsonNode baseParams, QuerySession session) {
    ObjectMapper mapper = JsonMapperHolder.getObjectMapper();

    // 1. 复制基础参数或创建新的 ObjectNode
    ObjectNode params =
        baseParams != null && baseParams.isObject()
            ? ((ObjectNode) baseParams).deepCopy()
            : mapper.createObjectNode();

    // 2. 计算页码（从 offset 和 limit 计算）
    // DOAJ page 从 1 开始，offset=0 → page=1, offset=100 → page=2 (如果 limit=100)
    int page = (batch.offset() / batch.limit()) + 1;

    // 3. 添加 DOAJ 分页参数
    params.put(DoajParamKeys.PAGE, page);
    params.put(DoajParamKeys.PAGE_SIZE, batch.limit());

    log.debug(
        "DOAJ 参数映射完成: batchNo={}, offset={} → page={}, limit={} → pageSize={}",
        batch.batchNo(),
        batch.offset(),
        page,
        batch.limit(),
        batch.limit());

    return params;
  }
}
