package com.patra.ingest.infra.mapper.impl;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.provenance.api.params.DoajParamKeys;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.query.QuerySession;
import com.patra.ingest.infra.mapper.ProviderParameterMapper;
import dev.linqibin.commons.json.JsonMapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/// DOAJ 参数映射器。
///
/// 将通用的 offset/limit 映射为 DOAJ 特定的分页参数。
///
/// **DOAJ 分页机制**：
///
/// - **page**: 页码（从 1 开始）
///   - **pageSize**: 每页记录数
///
/// **映射规则**：
///
/// ```java
/// batch.offset() / batch.limit() + 1 → page（计算页码）
/// batch.limit()  → pageSize
/// ```
///
/// **注意**：DOAJ 使用页码分页（从1开始），需要从 offset 计算出 page。
///
/// @author linqibin
/// @since 0.1.0
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
