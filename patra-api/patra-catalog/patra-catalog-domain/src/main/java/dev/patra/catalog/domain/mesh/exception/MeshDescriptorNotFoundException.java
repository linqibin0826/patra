package dev.patra.catalog.domain.mesh.exception;

import dev.patra.catalog.domain.kernel.exception.CatalogException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 按 UI 查 MeSH 主题词但目标不存在（或格式不对）时抛出。
///
/// 携带 [StandardErrorTrait#NOT_FOUND]，由 `DefaultErrorResolutionEngine` 映射为 HTTP 404。
///
/// @author linqibin
/// @since 0.1.0
public class MeshDescriptorNotFoundException extends CatalogException {

  /// 使用主题词 UI 创建未找到异常。
  ///
  /// @param ui 不存在的主题词 UI
  public MeshDescriptorNotFoundException(String ui) {
    super("MeSH descriptor not found with ui: " + ui, StandardErrorTrait.NOT_FOUND);
  }
}
