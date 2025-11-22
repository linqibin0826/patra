package com.patra.starter.provenance.common.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/// JSON节点解析防御性工具类
///
/// PubMed/EPMC等数据源的响应载荷包含混合的节点形状（数组、带"#text"字段的对象、单例等）。 这些工具方法将它们规范化为可预测的Java类型，同时容忍缺失或格式错误的数据。
///
/// 设计目标：
///
/// - 统一处理不同数据源的JSON响应差异
///   - 提供防御性解析，避免NPE或类型转换异常
///   - 标准化单例/数组的处理逻辑
///
/// @author linqibin
/// @since 0.1.0
public final class JsonHelpers {

  private JsonHelpers() {}

  /// 从节点中提取文本值，容忍PubMed/EPMC的特殊结构
  ///
  /// 支持以下节点类型：
  ///
  /// - 普通文本节点：直接返回文本
  ///   - 对象节点：尝试提取 {"#text": "value"} 格式的值
  ///   - 缺失/null节点：返回null
  ///
  /// @param node 源节点，可能缺失或形如 {"#text": value}
  /// @return 文本表示，不可用时返回 `null`
  public static String textValue(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    if (node.isTextual()) {
      return node.asText();
    }
    JsonNode textNode = node.get("#text");
    if (textNode != null && textNode.isTextual()) {
      return textNode.asText();
    }
    return node.asText(null);
  }

  /// 将节点转换为不可变字符串列表，跳过空白值
  ///
  /// 处理逻辑：
  ///
  /// - 数组节点：遍历提取每个元素的文本值
  ///   - 标量节点：提取单个文本值
  ///   - 空白值自动过滤
  ///
  /// @param node 代表标量或文本节点数组的节点
  /// @return 非空字符串的不可变列表
  public static List<String> toStringList(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return Collections.emptyList();
    }
    List<String> values = new ArrayList<>();
    if (node.isArray()) {
      for (JsonNode item : node) {
        String text = textValue(item);
        if (text != null && !text.isBlank()) {
          values.add(text);
        }
      }
      return Collections.unmodifiableList(values);
    }
    String single = textValue(node);
    if (single != null && !single.isBlank()) {
      values.add(single);
    }
    return Collections.unmodifiableList(values);
  }

  /// 将节点转换为深拷贝的防御性不可变节点列表
  ///
  /// 通过深拷贝确保下游修改不会影响原节点。
  ///
  /// @param node 源节点，可以是单例或数组
  /// @return 安全的节点不可变列表，支持下游变更
  public static List<JsonNode> toNodeList(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return Collections.emptyList();
    }
    List<JsonNode> nodes = new ArrayList<>();
    if (node.isArray()) {
      node.forEach(item -> nodes.add(item.deepCopy()));
    } else {
      nodes.add(node.deepCopy());
    }
    return Collections.unmodifiableList(nodes);
  }

  /// 便捷访问器：提取子字段并通过 {@link #toNodeList(JsonNode)} 转换
  ///
  /// @param parent 包含目标字段的父节点
  /// @param field 子字段名称
  /// @return 请求字段的深拷贝节点不可变列表
  public static List<JsonNode> toNodeListFromField(JsonNode parent, String field) {
    return parent != null ? toNodeList(parent.get(field)) : Collections.emptyList();
  }

  /// 从对象节点收集所有子值的深拷贝
  ///
  /// @param objectNode 需要物化值的对象节点
  /// @return 值节点的不可变列表
  public static List<JsonNode> nodeValues(JsonNode objectNode) {
    if (objectNode == null || !objectNode.isObject()) {
      return Collections.emptyList();
    }
    List<JsonNode> values = new ArrayList<>();
    Iterator<JsonNode> iterator = objectNode.elements();
    while (iterator.hasNext()) {
      values.add(iterator.next().deepCopy());
    }
    return Collections.unmodifiableList(values);
  }
}
