package dev.patra.interaction.domain.collection.policy;

import dev.patra.interaction.domain.collection.model.vo.CollectionName;
import java.text.BreakIterator;
import java.util.Locale;
import java.util.Set;

/// 复制别人的收藏夹时给新夹定名：不重名就用原名，重名时用 `原名 (n)`，`n` 从 2 起取最小的空号；加上后缀超出名称上限时
/// 按字素截短原名。
public final class CopyNaming {

  /// 只有静态方法。
  private CopyNaming() {}

  /// 定名。
  ///
  /// @param source 源收藏夹的名称
  /// @param taken 复制者已有的名称
  /// @return 新名称
  public static CollectionName nameFor(CollectionName source, Set<String> taken) {
    if (!taken.contains(source.value())) {
      return source;
    }
    for (int n = 2; ; n++) {
      String candidate = withSuffix(source.value(), " (" + n + ")");
      if (!taken.contains(candidate)) {
        return CollectionName.parse(candidate);
      }
    }
  }

  /// 原名加后缀，必要时从尾部按字素截短原名，使结果不超过名称的字素与字节上限。
  ///
  /// @param base 原名
  /// @param suffix 后缀
  /// @return 结果
  private static String withSuffix(String base, String suffix) {
    int maxGraphemes = CollectionName.MAX_GRAPHEMES - TextRules.graphemeCount(suffix);
    int maxBytes = CollectionName.MAX_GRAPHEMES * 4 - TextRules.utf8Length(suffix);
    BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
    clusters.setText(base);
    int end = 0;
    int count = 0;
    for (int next = clusters.next(); next != BreakIterator.DONE; next = clusters.next()) {
      if (count + 1 > maxGraphemes || TextRules.utf8Length(base.substring(0, next)) > maxBytes) {
        break;
      }
      end = next;
      count++;
    }
    return TextRules.normalize(base.substring(0, end)) + suffix;
  }
}
