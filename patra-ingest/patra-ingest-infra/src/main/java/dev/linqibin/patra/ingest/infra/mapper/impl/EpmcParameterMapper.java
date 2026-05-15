package dev.linqibin.patra.ingest.infra.mapper.impl;

import dev.linqibin.commons.json.JsonMapperHolder;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.common.provenance.api.params.EpmcParamKeys;
import dev.linqibin.patra.ingest.domain.model.vo.batch.Batch;
import dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession;
import dev.linqibin.patra.ingest.infra.mapper.ProviderParameterMapper;
import dev.linqibin.patra.ingest.infra.mapper.StateTokenKeys;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/// EPMC 参数映射器。
///
/// 将通用的 offset/limit 映射为 EPMC 特定的游标分页参数。
///
/// **EPMC 分页机制**：
///
/// - **pageSize**: 每页记录数
///   - **cursorMark**: Solr 风格的游标令牌
///
/// - 首次请求：cursorMark="*"
///         - 后续请求：使用上次返回的 nextCursorMark
///         - 结束标志：nextCursorMark 等于当前 cursorMark
///
/// **映射规则**：
///
/// ```java
/// batch.limit()  → pageSize
/// session.stateToken("cursorMark") → cursorMark（如果有）
/// ```
///
/// **注意**：EPMC 使用游标分页，不使用 offset。每次请求返回 nextCursorMark 用于下次请求。
///
/// @author linqibin
/// @since 0.1.0
@Component
@Slf4j
public class EpmcParameterMapper implements ProviderParameterMapper {

  private static final String DEFAULT_CURSOR = "*";

  @Override
  public ProvenanceCode getSupportedProvenance() {
    return ProvenanceCode.EPMC;
  }

  @Override
  public JsonNode mapParameters(Batch batch, JsonNode baseParams, QuerySession session) {
    ObjectMapper mapper = JsonMapperHolder.getObjectMapper();

    // 1. 复制基础参数或创建新的 ObjectNode
    ObjectNode params =
        baseParams != null && baseParams.isObject()
            ? ((ObjectNode) baseParams).deepCopy()
            : mapper.createObjectNode();

    // 2. 添加 EPMC 分页参数（只使用 pageSize，不使用 offset）
    params.put(EpmcParamKeys.PAGE_SIZE, batch.limit());

    // 3. 添加游标令牌
    String cursorMark = DEFAULT_CURSOR; // 默认使用 "*"

    if (session.hasStateToken()) {
      Map<String, String> stateToken = session.stateToken().orElseThrow();
      String storedCursor = stateToken.get(StateTokenKeys.EPMC_CURSOR_MARK);

      if (storedCursor != null && !storedCursor.isBlank()) {
        cursorMark = storedCursor;
        log.debug("使用已有的 cursorMark: batchNo={}, cursorMark={}", batch.batchNo(), cursorMark);
      }
    }

    params.put(EpmcParamKeys.CURSOR_MARK, cursorMark);

    log.debug(
        "EPMC 参数映射完成: batchNo={}, pageSize={}, cursorMark={}",
        batch.batchNo(),
        batch.limit(),
        cursorMark);

    return params;
  }
}
