package com.patra.catalog.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 数据已存在异常。
///
/// 当尝试执行「一次性初始化」导入操作，但目标表中已存在数据时抛出。
///
/// **设计意图**：
///
/// - 导入操作设计为一次性数据初始化，不支持增量或覆盖
/// - 如需重新导入，必须先手动清空数据库表（而非代码触发 TRUNCATE）
/// - 这是一种安全机制，防止意外覆盖生产数据
///
/// **使用场景**：
///
/// - Venue 导入时表中已有数据
/// - MeSH Descriptor 导入时表中已有数据
/// - MeSH Qualifier 导入时表中已有数据
///
/// @author linqibin
/// @since 0.1.0
public class DataAlreadyExistsException extends CatalogException {

  /// 构造数据已存在异常。
  ///
  /// @param entityName 实体名称（如 "Venue"、"MeSH Descriptor"）
  public DataAlreadyExistsException(String entityName) {
    super("表中已存在 " + entityName + " 数据，请先手动清空数据库后再执行导入", StandardErrorTrait.CONFLICT);
  }
}
