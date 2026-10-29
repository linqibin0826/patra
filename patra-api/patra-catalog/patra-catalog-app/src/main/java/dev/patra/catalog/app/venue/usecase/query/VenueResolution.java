package dev.patra.catalog.app.venue.usecase.query;

import dev.patra.catalog.domain.venue.model.read.VenueResolveReadModel;

/// 一个请求键的期刊解析结果。
///
/// @param key 请求里的键原文（按 ID 时是 ID 的十进制字符串；按 NLM 时可能是副 NLM）
/// @param found 查到的期刊（含软删）；为 null 表示库里不存在或不是期刊
/// @author linqibin
/// @since 0.1.0
public record VenueResolution(String key, VenueResolveReadModel found) {

  /// 创建解析结果。
  ///
  /// @param key 请求键
  /// @param found 查到的期刊，不存在时为 null
  /// @return 解析结果
  public static VenueResolution of(String key, VenueResolveReadModel found) {
    return new VenueResolution(key, found);
  }
}
