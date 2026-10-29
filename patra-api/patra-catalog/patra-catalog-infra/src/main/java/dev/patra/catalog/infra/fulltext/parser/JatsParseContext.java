package dev.patra.catalog.infra.fulltext.parser;

import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// 一篇文章的解析上下文：警告计数、会输出的 id、生成的 id、图片清单。
///
/// @author linqibin
/// @since 0.11.0
final class JatsParseContext {

  private final Map<String, FullTextMedia> mediaByName = new HashMap<>();
  private final Map<String, FullTextMedia> mediaByBaseName = new HashMap<>();
  private final Set<String> knownIds = new HashSet<>();
  private final Set<String> usedIds = new HashSet<>();
  private int warningCount;
  private int generatedId;

  /// 创建上下文。
  ///
  /// @param media 该篇的图片清单
  JatsParseContext(List<FullTextMedia> media) {
    for (FullTextMedia m : media) {
      mediaByName.put(m.name(), m);
      mediaByBaseName.putIfAbsent(baseName(m.name()), m);
    }
  }

  /// 计一条解析警告。
  void warn() {
    warningCount++;
  }

  /// 已计的警告数。
  ///
  /// @return 警告数
  int warningCount() {
    return warningCount;
  }

  /// 登记一个会出现在结果里的目标 id（交叉引用只认这些）。
  ///
  /// @param id id
  void registerTarget(String id) {
    if (id != null) {
      knownIds.add(id);
      usedIds.add(id);
    }
  }

  /// 是否已登记的目标 id。
  ///
  /// @param id id
  /// @return 是否
  boolean isKnownTarget(String id) {
    return knownIds.contains(id);
  }

  /// 原 id 有值就用它，否则生成一个不重复的 id。
  ///
  /// @param id 原 id，可空
  /// @param prefix 生成 id 的前缀
  /// @return id
  String idOrGenerate(String id, String prefix) {
    if (id != null) {
      return id;
    }
    String generated;
    do {
      generated = prefix + "-" + (++generatedId);
    } while (!usedIds.add(generated));
    return generated;
  }

  /// 按 href 匹配图片：先全名；再把 href 当作不带扩展名的名字（如 PLOS 的 `pcbi.e001`）；
  /// 最后两边都去扩展名后同名。
  ///
  /// @param href `<graphic xlink:href>`
  /// @return 匹配到的图片
  Optional<FullTextMedia> matchMedia(String href) {
    if (href == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(mediaByName.get(href))
        .or(() -> Optional.ofNullable(mediaByBaseName.get(href)))
        .or(() -> Optional.ofNullable(mediaByBaseName.get(baseName(href))));
  }

  /// 去掉最后一个扩展名。
  private static String baseName(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }
}
