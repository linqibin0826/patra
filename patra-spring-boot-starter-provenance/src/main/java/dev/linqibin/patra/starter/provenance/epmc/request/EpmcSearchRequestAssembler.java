package dev.linqibin.patra.starter.provenance.epmc.request;

import dev.linqibin.patra.starter.provenance.epmc.model.request.SearchRequest;
import dev.linqibin.patra.common.provenance.api.params.EpmcParamKeys;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;

/// Europe PMC 搜索请求组装器
///
/// 从表达式编译器生成的数据源参数中组装 {@link SearchRequest} 实例。 负责参数提取、类型转换和验证。
///
/// @author linqibin
/// @since 0.1.0
public class EpmcSearchRequestAssembler {

  /// 从数据源参数构建Europe PMC搜索请求
  ///
  /// @param params 数据源命名的参数（如query、pageSize、cursorMark等）
  /// @return 组装后的 {@link SearchRequest}
  public SearchRequest build(JsonNode params) {
    String query = text(params, EpmcParamKeys.QUERY);
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("EPMC 参数必须在编译器映射后包含数据源键 'query'");
    }

    String format = text(params, EpmcParamKeys.FORMAT);
    Integer pageSize = integer(params, EpmcParamKeys.PAGE_SIZE);
    String cursorMark = text(params, EpmcParamKeys.CURSOR_MARK);
    String sort = text(params, EpmcParamKeys.SORT);
    String resultType = text(params, EpmcParamKeys.RESULT_TYPE);
    Boolean synonym = bool(params, EpmcParamKeys.SYNONYM);
    Boolean fromSearchPost = bool(params, EpmcParamKeys.FROM_SEARCH_POST);
    String searchType = text(params, EpmcParamKeys.SEARCH_TYPE);

    return new SearchRequest(
        query, format, pageSize, cursorMark, sort, resultType, synonym, fromSearchPost, searchType);
  }

  private static String text(JsonNode node, String key) {
    if (node == null || node.isNull() || key == null) {
      return null;
    }
    JsonNode value = node.get(key);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.getNodeType() == JsonNodeType.STRING) {
      String text = value.asText();
      return text != null && !text.isBlank() ? text : null;
    }
    return value.isValueNode() ? value.asText() : null;
  }

  private static Integer integer(JsonNode node, String key) {
    if (node == null || node.isNull() || key == null) {
      return null;
    }
    JsonNode value = node.get(key);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isInt()) {
      return value.intValue();
    }
    if (value.isLong()) {
      long v = value.longValue();
      if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
        throw new IllegalArgumentException("EPMC pageSize 超出整数范围: " + v);
      }
      return (int) v;
    }
    if (value.isTextual()) {
      String text = value.asText();
      if (text == null || text.isBlank()) {
        return null;
      }
      try {
        return Integer.parseInt(text);
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("EPMC 参数 '" + key + "' 不是有效的整数: " + text, ex);
      }
    }
    return null;
  }

  private static Boolean bool(JsonNode node, String key) {
    if (node == null || node.isNull() || key == null) {
      return null;
    }
    JsonNode value = node.get(key);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isBoolean()) {
      return value.booleanValue();
    }
    if (value.isTextual()) {
      String text = value.asText();
      if (text == null || text.isBlank()) {
        return null;
      }
      return Boolean.parseBoolean(text);
    }
    return null;
  }
}
