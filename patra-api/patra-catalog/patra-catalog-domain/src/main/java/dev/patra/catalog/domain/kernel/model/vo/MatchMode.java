package dev.patra.catalog.domain.kernel.model.vo;

/// 建议查询的匹配模式，由查询文本的文字决定。
///
/// - [#SUBSTRING]：中日韩文字没有词边界，名称里任意位置出现查询文本就算命中。
/// - [#WORD_PREFIX]：拉丁等有词边界的文字，只匹配整串前缀或某个词的开头，不匹配词中间。
///
/// @author linqibin
/// @since 0.1.0
public enum MatchMode {

  /// 任意位置子串匹配。
  SUBSTRING,

  /// 整串前缀或词首匹配。
  WORD_PREFIX
}
