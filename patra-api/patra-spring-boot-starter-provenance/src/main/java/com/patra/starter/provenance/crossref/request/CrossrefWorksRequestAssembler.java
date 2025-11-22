package com.patra.starter.provenance.crossref.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.patra.common.provenance.api.params.CrossrefParamKeys;
import com.patra.starter.provenance.crossref.model.request.CrossrefWorksRequest;

/// Crossref Works 请求组装器
///
/// 从表达式编译器生成的数据源参数中组装 {@link CrossrefWorksRequest} 对象。 负责参数提取、类型转换和验证。
///
/// @author linqibin
/// @since 0.1.0
public class CrossrefWorksRequestAssembler {

  /// 从数据源参数构建 {@link CrossrefWorksRequest}
  ///
  /// @param params 数据源参数映射（JSON格式），包含query、filter、rows等
  /// @return 组装后的请求对象
  public CrossrefWorksRequest build(JsonNode params) {
    String query = text(params, CrossrefParamKeys.QUERY);
    String filter = text(params, CrossrefParamKeys.FILTER);
    Integer rows = integer(params, CrossrefParamKeys.ROWS);
    Integer offset = integer(params, CrossrefParamKeys.OFFSET);
    String sort = text(params, CrossrefParamKeys.SORT);
    String order = text(params, CrossrefParamKeys.ORDER);
    String cursor = text(params, CrossrefParamKeys.CURSOR);
    String select = text(params, CrossrefParamKeys.SELECT);
    String mailto = text(params, CrossrefParamKeys.MAILTO);

    return new CrossrefWorksRequest(
        query, filter, rows, offset, sort, order, cursor, select, mailto);
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
        throw new IllegalArgumentException("Crossref 参数 '" + key + "' 超出整数范围");
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
        throw new IllegalArgumentException("Crossref 参数 '" + key + "' 不是有效的整数: " + text, ex);
      }
    }
    return null;
  }
}
