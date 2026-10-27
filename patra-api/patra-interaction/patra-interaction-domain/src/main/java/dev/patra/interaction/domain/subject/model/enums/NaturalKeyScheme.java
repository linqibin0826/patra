package dev.patra.interaction.domain.subject.model.enums;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/// 外部身份锚的种类：落库为 `{前缀}:{值}`。
public enum NaturalKeyScheme {
  /// PubMed ID，与 catalog 的 PMID 校验一致。
  PMID("pmid", "[1-9][0-9]{0,14}"),

  /// NLM Unique ID，与 catalog 的校验一致，区分大小写。按字符串存：mini 上有前导零（`0404401`）、`R` / `A`
  /// 后缀（`2985213R`）和 16 位的新式 ID（`9918523075306676`）。
  NLM("nlm", "[0-9]{1,19}[A-Z]?"),

  /// 帖子 ID（本地对象，Snowflake）。
  POST("post", "[1-9][0-9]{0,18}");

  private final String prefix;
  private final Pattern format;

  /// 定义一种身份锚。
  ///
  /// @param prefix 落库前缀
  /// @param format 值的格式
  NaturalKeyScheme(String prefix, String format) {
    this.prefix = prefix;
    this.format = Pattern.compile(format);
  }

  /// 落库前缀。
  ///
  /// @return 前缀，如 `pmid`
  public String prefix() {
    return prefix;
  }

  /// 值是否合法。
  ///
  /// @param value 值
  /// @return 合法时为 `true`
  public boolean accepts(String value) {
    return value != null && format.matcher(value).matches();
  }

  /// 按前缀查找。
  ///
  /// @param prefix 前缀
  /// @return 对应的种类；未知时为空
  public static Optional<NaturalKeyScheme> fromPrefix(String prefix) {
    return Arrays.stream(values()).filter(s -> s.prefix.equals(prefix)).findFirst();
  }
}
