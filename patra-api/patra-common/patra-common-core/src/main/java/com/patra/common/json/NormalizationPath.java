package com.patra.common.json;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * JSON 规范化过程中的路径跟踪器，用于错误报告和字段匹配。
 *
 * <p>在递归遍历 JSON 树结构时，维护当前的路径信息（如 "user.address.city"），以便在发生错误时提供准确的上下文。
 *
 * <p><b>路径格式</b>:
 * <ul>
 *   <li>对象字段: {@code $.user.name}
 *   <li>数组元素: {@code $.items[]}
 *   <li>嵌套数组: {@code $.items[].tags[]}
 * </ul>
 *
 * <p><b>线程安全</b>: 此类不是线程安全的，应在单线程上下文中使用。
 *
 * @author Patra Team
 * @since 0.1.0
 */
final class NormalizationPath {
  private final Deque<String> tokens = new ArrayDeque<>();

  /** 进入对象字段，将字段名压入路径栈 */
  void pushField(String field) {
    tokens.addLast(field);
  }

  /** 退出当前字段，弹出路径栈顶元素 */
  void pop() {
    if (!tokens.isEmpty()) {
      tokens.removeLast();
    }
  }

  /** 标记进入数组，在当前字段名后追加 "[]" */
  void markArray() {
    if (tokens.isEmpty()) {
      tokens.addLast("[]");
    } else {
      String last = tokens.removeLast();
      tokens.addLast(last + "[]");
    }
  }

  /** 退出数组，移除路径末尾的 "[]" 标记 */
  void unmarkArray() {
    if (tokens.isEmpty()) {
      return;
    }
    String last = tokens.removeLast();
    if (last.endsWith("[]")) {
      String base = last.substring(0, last.length() - 2);
      if (!base.isEmpty()) {
        tokens.addLast(base);
      }
    } else {
      tokens.addLast(last);
    }
  }

  /**
   * 将路径转换为字符串表示。
   *
   * @param includeRoot 是否包含根符号 "$"
   * @return 路径字符串（例如 "$.user.address" 或 "user.address"）
   */
  String asString(boolean includeRoot) {
    if (tokens.isEmpty()) {
      return includeRoot ? "$" : "";
    }
    String joined = String.join(".", tokens);
    return includeRoot ? "$." + joined : joined;
  }

  /**
   * 获取路径的叶子节点名称（不包含数组标记）。
   *
   * @return 叶子字段名，例如对于路径 "items[].tags[]"，返回 "tags"
   */
  String leaf() {
    if (tokens.isEmpty()) {
      return "";
    }
    String last = tokens.peekLast();
    while (last != null && last.endsWith("[]")) {
      last = last.substring(0, last.length() - 2);
    }
    return last == null ? "" : last;
  }
}
