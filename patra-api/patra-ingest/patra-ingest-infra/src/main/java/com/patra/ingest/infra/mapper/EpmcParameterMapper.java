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
 * EPMC 参数映射器。
 *
 * <p>将通用的 offset/limit 映射为 EPMC 特定的游标分页参数。
 *
 * <p><strong>EPMC 分页机制</strong>：
 *
 * <ul>
 *   <li><b>pageSize</b>: 每页记录数
 *   <li><b>cursorMark</b>: Solr 风格的游标令牌
 *       <ul>
 *         <li>首次请求：cursorMark="*"
 *         <li>后续请求：使用上次返回的 nextCursorMark
 *         <li>结束标志：nextCursorMark 等于当前 cursorMark
 *       </ul>
 * </ul>
 *
 * <p><strong>映射规则</strong>：
 *
 * <pre>{@code
 * batch.limit()  → pageSize
 * metadata.stateToken("cursorMark") → cursorMark（如果有）
 * }</pre>
 *
 * <p><strong>注意</strong>：EPMC 使用游标分页，不使用 offset。每次请求返回 nextCursorMark 用于下次请求。
 *
 * @author Patra Architecture Team
 * @since 0.3.0
 */
@Component
@Slf4j
public class EpmcParameterMapper implements ProviderParameterMapper {

  private static final String DEFAULT_CURSOR = "*";

  @Override
  public ProvenanceCode getSupportedProvenance() {
    return ProvenanceCode.EPMC;
  }

  @Override
  public JsonNode mapParameters(Batch batch, JsonNode baseParams, FetchMetadata metadata) {
    ObjectMapper mapper = JsonMapperHolder.getObjectMapper();

    // 1. 复制基础参数或创建新的 ObjectNode
    ObjectNode params =
        baseParams != null && baseParams.isObject()
            ? ((ObjectNode) baseParams).deepCopy()
            : mapper.createObjectNode();

    // 2. 添加 EPMC 分页参数（只使用 pageSize，不使用 offset）
    params.put("pageSize", batch.limit());

    // 3. 添加游标令牌
    String cursorMark = DEFAULT_CURSOR; // 默认使用 "*"

    if (metadata.hasStateToken()) {
      Map<String, String> stateToken = metadata.stateToken().orElseThrow();
      String storedCursor = stateToken.get("cursorMark");

      if (storedCursor != null && !storedCursor.isBlank()) {
        cursorMark = storedCursor;
        log.debug("使用已有的 cursorMark: batchNo={}, cursorMark={}", batch.batchNo(), cursorMark);
      }
    }

    params.put("cursorMark", cursorMark);

    log.debug(
        "EPMC 参数映射完成: batchNo={}, pageSize={}, cursorMark={}",
        batch.batchNo(),
        batch.limit(),
        cursorMark);

    return params;
  }
}
