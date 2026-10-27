package dev.patra.interaction.domain.post.model.enums;

/// 一次编辑怎么落库。
public enum EditKind {
  /// 内容没变：什么都不写。
  NONE,
  /// 发布后 5 分钟内：覆盖内容，不记版本。
  OVERWRITE,
  /// 其余：被替换的旧版本记进修改历史。
  REVISE
}
