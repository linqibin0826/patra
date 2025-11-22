package com.patra.starter.expr.compiler.transform;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 列表连接变换:将多个值用逗号分隔符连接,用于 MULTI std_keys。
///
/// 当 MULTI std_keys 需要合并为单个提供者参数值时使用。
///
/// 该变换期望接收一个特殊分隔符分隔的值字符串(来自渲染器的内部格式),并将其转换为适合提供者使用的逗号分隔列表。
///
/// 示例:
///
/// ```
///
/// 内部格式: "value1||value2||value3"
/// 输出:     "value1,value2,value3"
///
/// ```
///
/// 参见: docs/expr/03-compiler-bridge-internals.md §3.8 (MULTI Join Strategy)
///
/// @since 0.1.0
public class ListJoinTransform implements ValueTransform {

  private static final Logger log = LoggerFactory.getLogger(ListJoinTransform.class);
  private static final String CODE = "LIST_JOIN";
  private static final String INTERNAL_DELIMITER = "||";
  private static final String OUTPUT_SEPARATOR = ",";

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public String apply(String stdKey, String value, ProvenanceSnapshot snapshot) {
    if (value == null || value.isBlank()) {
      log.debug("LIST_JOIN: stdKey={}, 值为 null 或空白,原样返回", stdKey);
      return value;
    }

    // 按内部分隔符分割,然后用输出分隔符连接
    String result =
        Arrays.stream(value.split(Pattern.quote(INTERNAL_DELIMITER)))
            .filter(s -> !s.isBlank())
            .collect(Collectors.joining(OUTPUT_SEPARATOR));

    log.debug("LIST_JOIN: stdKey={}, 输入长度={}, 输出长度={}", stdKey, value.length(), result.length());
    return result;
  }
}
