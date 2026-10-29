package dev.patra.catalog.domain.organization.exception;

import dev.patra.catalog.domain.kernel.exception.CatalogException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 按 ROR ID 查机构但目标不存在（或格式不对、非 active）时抛出。
///
/// 携带 [StandardErrorTrait#NOT_FOUND]，由 `DefaultErrorResolutionEngine` 映射为 HTTP 404。
///
/// @author linqibin
/// @since 0.1.0
public class OrganizationNotFoundException extends CatalogException {

  /// 使用 ROR ID 创建未找到异常。
  ///
  /// @param rorId 不存在的 ROR ID
  public OrganizationNotFoundException(String rorId) {
    super("Organization not found with ror id: " + rorId, StandardErrorTrait.NOT_FOUND);
  }
}
