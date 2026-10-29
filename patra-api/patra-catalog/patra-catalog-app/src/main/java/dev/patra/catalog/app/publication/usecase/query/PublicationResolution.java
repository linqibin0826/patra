package dev.patra.catalog.app.publication.usecase.query;

import dev.patra.catalog.domain.publication.model.read.PublicationResolveReadModel;

/// 一个请求键的解析结果。
///
/// @param key 请求里的键原文（按 ID 时是 ID 的十进制字符串）
/// @param found 查到的文献（含软删）；为 null 表示库里不存在
/// @author linqibin
/// @since 0.1.0
public record PublicationResolution(String key, PublicationResolveReadModel found) {

  /// 创建解析结果。
  ///
  /// @param key 请求键
  /// @param found 查到的文献，不存在时为 null
  /// @return 解析结果
  public static PublicationResolution of(String key, PublicationResolveReadModel found) {
    return new PublicationResolution(key, found);
  }
}
