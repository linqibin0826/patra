package com.patra.catalog.domain.exception;

import com.patra.common.error.trait.StandardErrorTrait;

/// MeSH 数据持久化异常。
///
/// 用于 MeSH 限定词、描述符等数据批量插入或更新失败的场景。
/// 携带 `DEP_UNAVAILABLE` 特征表示依赖（数据库）不可用。
///
/// @author linqibin
/// @since 0.1.0
public class MeshPersistenceException extends CatalogException {

  /// 使用消息构造 MeSH 持久化异常。
  ///
  /// @param message 详细消息
  public MeshPersistenceException(String message) {
    super(message, StandardErrorTrait.DEP_UNAVAILABLE);
  }

  /// 使用消息和根本原因构造 MeSH 持久化异常。
  ///
  /// @param message 详细消息
  /// @param cause 根本原因
  public MeshPersistenceException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
