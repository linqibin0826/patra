package dev.patra.identity.domain.account.policy;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/// 昵称里不能出现的词：官方与角色、职业身份，以及站名与数据源名。
///
/// 匹配前先折叠：NFKC、转小写。中文词按**包含**匹配（去掉空白和 `·` 之后）；拉丁词按**整词**匹配
/// （按非字母数字切词，每个词再做 `ReservedUsernames.fold` 的同形归一）。路由段不进这份清单：
/// 昵称不进 URL，叫「Settings」没有危害。
public final class RejectedDisplayNameWords {

  private static final List<String> CJK_WORDS =
      List.of(
          "官方", "官网", "官微", "管理员", "客服", "系统", "运营", "审核", "认证", "医生", "医师", "大夫", "主任", "主治", "教授",
          "副教授", "专家", "院士", "博导", "院长", "护士长", "药师", "研究员");

  private static final List<String> LATIN_WORDS =
      List.of(
          "official",
          "admin",
          "administrator",
          "staff",
          "system",
          "support",
          "moderator",
          "bot",
          "doctor",
          "dr",
          "physician",
          "surgeon",
          "prof",
          "professor",
          "md",
          "expert",
          "specialist",
          "nurse");

  private static final Set<String> FOLDED_LATIN_WORDS = foldedLatinWords();

  private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

  private static final Pattern CJK_NOISE = Pattern.compile("[\\s\\u00B7]+");

  /// 只有静态方法。
  private RejectedDisplayNameWords() {}

  /// 是否命中拒绝词。
  ///
  /// @param normalized 规范化之后的昵称
  /// @return 命中时为 `true`
  public static boolean matches(String normalized) {
    String folded = Normalizer.normalize(normalized, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    String compact = CJK_NOISE.matcher(folded).replaceAll("");
    for (String word : CJK_WORDS) {
      if (compact.contains(word)) {
        return true;
      }
    }
    for (String token : TOKEN_SEPARATOR.split(folded)) {
      if (!token.isEmpty() && FOLDED_LATIN_WORDS.contains(ReservedUsernames.fold(token))) {
        return true;
      }
    }
    return false;
  }

  /// 拉丁词清单折叠后，并入站名与数据源名。
  ///
  /// @return 不可变集合
  private static Set<String> foldedLatinWords() {
    Set<String> words = new HashSet<>(ReservedUsernames.siteAndSourceNames());
    for (String word : LATIN_WORDS) {
      words.add(ReservedUsernames.fold(word));
    }
    return Set.copyOf(words);
  }
}
