package dev.patra.interaction.domain.collection.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import lombok.Getter;

/// 一次导出超过 5,000 条（422 `EXPORT_TOO_LARGE`）。
@Getter
public final class ExportTooLargeException extends DomainException {

  private final int count;

  /// 创建异常。
  ///
  /// @param count 这个范围里可导出的条数
  public ExportTooLargeException(int count) {
    super(
        "一次最多导出 5,000 篇", StandardErrorTrait.RULE_VIOLATION, CollectionErrorTrait.EXPORT_TOO_LARGE);
    this.count = count;
  }
}
