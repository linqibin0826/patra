package dev.patra.catalog.infra.batch.publication.update;

import dev.patra.catalog.domain.model.vo.publication.PublicationSyncTarget;
import dev.patra.catalog.infra.batch.publication.PublicationImportResult;

/// 增量同步处理器的输出：处理结果加上要覆盖的库内文献。
///
/// @param result 与 baseline 相同的处理结果
/// @param target 要覆盖的库内文献，`null` 表示新建（只在 `ALL` 范围出现）
/// @author linqibin
/// @since 0.11.0
public record PublicationSyncItem(PublicationImportResult result, PublicationSyncTarget target) {

  /// 创建同步项。
  ///
  /// @param result 处理结果
  /// @param target 同步目标，新建时为 `null`
  /// @return 同步项
  public static PublicationSyncItem of(
      PublicationImportResult result, PublicationSyncTarget target) {
    return new PublicationSyncItem(result, target);
  }
}
