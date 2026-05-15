package com.patra.catalog.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// Venue 资源未找到异常。
///
/// 当按 ID 查询 Venue 但目标不存在时抛出。
/// 携带 {@link StandardErrorTrait#NOT_FOUND} 语义特征，
/// 由 {@code DefaultErrorResolutionEngine} 自动映射为 HTTP 404。
public class VenueNotFoundException extends CatalogException {

  /// 使用 Venue ID 创建未找到异常。
  ///
  /// @param venueId 不存在的 Venue ID
  public VenueNotFoundException(Long venueId) {
    super("Venue not found with id: " + venueId, StandardErrorTrait.NOT_FOUND);
  }
}
