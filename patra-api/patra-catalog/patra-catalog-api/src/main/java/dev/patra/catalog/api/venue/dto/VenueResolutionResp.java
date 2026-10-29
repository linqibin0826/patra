package dev.patra.catalog.api.venue.dto;

import lombok.Builder;

/// 一个请求键的期刊解析结果。
///
/// 按 NLM 解析时，请求的若是副 NLM，`nlmId`（主 NLM）与 `key` 不同：调用方按 `key` 对回结果。
///
/// @param key 请求里的键原文（按 ID 时是 ID 的十进制字符串）
/// @param exists 库里是否有这个期刊（软删也算有；非期刊类型的载体算没有）
/// @param state 状态；不存在时为 null
/// @param id 期刊主键（经 MVC 转换器输出为 JSON 字符串）；不存在时为 null
/// @param nlmId 主 NLM（字符串，保留前导零）；不存在或库里为空时为 null
/// @param title 期刊全称；不存在时为 null
/// @param abbreviatedTitle MEDLINE 刊名缩写；不存在或未知时为 null
/// @author linqibin
/// @since 0.1.0
@Builder
public record VenueResolutionResp(
    String key,
    boolean exists,
    VenueStateResp state,
    Long id,
    String nlmId,
    String title,
    String abbreviatedTitle) {

  /// 查不到的键。
  ///
  /// @param key 请求键
  /// @return 只有 `key`、`exists = false` 的结果
  public static VenueResolutionResp missing(String key) {
    return VenueResolutionResp.builder().key(key).exists(false).build();
  }
}
