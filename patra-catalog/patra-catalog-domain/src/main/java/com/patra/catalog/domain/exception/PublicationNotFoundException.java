package com.patra.catalog.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// Publication 资源未找到异常。
///
/// 当按 ID 查询 Publication 但目标不存在时抛出。
/// 携带 [StandardErrorTrait#NOT_FOUND] 语义特征，
/// 由 `DefaultErrorResolutionEngine` 自动映射为 HTTP 404。
///
/// @author linqibin
/// @since 0.1.0
public class PublicationNotFoundException extends CatalogException {

  /// 使用 Publication ID 创建未找到异常。
  ///
  /// @param publicationId 不存在的 Publication ID
  public PublicationNotFoundException(Long publicationId) {
    super("Publication not found with id: " + publicationId, StandardErrorTrait.NOT_FOUND);
  }
}
