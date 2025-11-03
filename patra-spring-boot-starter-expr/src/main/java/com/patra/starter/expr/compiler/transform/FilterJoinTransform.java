package com.patra.starter.expr.compiler.transform;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 过滤器连接变换:将多个 filter key:value 对用逗号分隔符连接,用于 MULTI std_keys。
 *
 * <p>用于像 Crossref 这样接受在单个参数中传递多个过滤器的提供者。
 *
 * <p>该变换期望接收一个特殊分隔符分隔的值字符串(来自渲染器的内部格式),并将其转换为适合提供者使用的逗号分隔过滤器列表。
 *
 * <p>示例:
 *
 * <pre>
 * 内部格式: "from-pub-date:2022-01-01||until-pub-date:2022-12-31"
 * 输出:     "from-pub-date:2022-01-01,until-pub-date:2022-12-31"
 * </pre>
 *
 * <p>参见: docs/expr/03-compiler-bridge-internals.md §3.8 (MULTI Join Strategy),
 * docs/expr/06-provider-crossref.md §6.7, docs/expr/99-appendix-sample-expressions.md §B.4
 *
 * @since 1.0.0
 */
public class FilterJoinTransform implements ValueTransform {

  private static final Logger log = LoggerFactory.getLogger(FilterJoinTransform.class);
  private static final String CODE = "FILTER_JOIN";
  private static final String INTERNAL_DELIMITER = "||";
  private static final String OUTPUT_SEPARATOR = ",";

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public String apply(String stdKey, String value, ProvenanceSnapshot snapshot) {
    if (value == null || value.isBlank()) {
      log.debug("FILTER_JOIN: stdKey={}, 值为 null 或空白,原样返回", stdKey);
      return value;
    }

    // 按内部分隔符分割,然后用输出分隔符连接
    // 每个段落预期是一个 filter key:value 对
    String result =
        Arrays.stream(value.split(Pattern.quote(INTERNAL_DELIMITER)))
            .filter(s -> !s.isBlank())
            .collect(Collectors.joining(OUTPUT_SEPARATOR));

    log.debug(
        "FILTER_JOIN: stdKey={}, 输入长度={}, 输出长度={}, 段落数={}",
        stdKey,
        value.length(),
        result.length(),
        value.split(Pattern.quote(INTERNAL_DELIMITER)).length);
    return result;
  }
}
