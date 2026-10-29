package dev.patra.catalog.api.publication.dto;

import lombok.Builder;

/// 一个请求键的解析结果。
///
/// @param key 请求里的键原文（按 ID 时是 ID 的十进制字符串）
/// @param exists 库里是否有这篇（软删也算有）
/// @param state 状态；不存在时为 null
/// @param id 文献主键（经 MVC 转换器输出为 JSON 字符串）；不存在时为 null
/// @param pmid PubMed ID；不存在或库里为空时为 null
/// @param title 标题原文（含内联标记）；不存在时为 null
/// @param journal 期刊全称；不存在或没有载体时为 null
/// @param year 出版年份；不存在或未知时为 null
/// @param publishedDate 出版日期，按实际精度输出 `yyyy` / `yyyy-MM` / `yyyy-MM-dd`；不存在或未知时为 null
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationResolutionResp(
    String key,
    boolean exists,
    PublicationStateResp state,
    Long id,
    String pmid,
    String title,
    String journal,
    Integer year,
    String publishedDate) {

  /// 查不到的键。
  ///
  /// @param key 请求键
  /// @return 只有 `key`、`exists = false` 的结果
  public static PublicationResolutionResp missing(String key) {
    return PublicationResolutionResp.builder().key(key).exists(false).build();
  }
}
