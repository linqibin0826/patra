package dev.patra.catalog.infra.publication.batch.update;

import dev.patra.catalog.domain.publication.model.vo.PublicationSyncTarget;
import dev.patra.catalog.domain.publication.model.vo.SourcePosition;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult;

/// 一条待写入的记录：组装结果、同步目标（为空表示新增）、这篇的原文与来源位置。
///
/// @param result 组装结果
/// @param target 库里的同步目标；为 null 表示新增
/// @param rawXml 这篇 `<PubmedArticle>` 原文，写进来源表
/// @param position 来源位置
/// @author linqibin
/// @since 0.1.0
public record PublicationSyncItem(
    PublicationImportResult result,
    PublicationSyncTarget target,
    String rawXml,
    SourcePosition position) {

  /// 创建写入项。
  ///
  /// @param result 组装结果
  /// @param target 同步目标，新增时为 null
  /// @param rawXml 原文
  /// @param position 来源位置
  /// @return 写入项
  public static PublicationSyncItem of(
      PublicationImportResult result,
      PublicationSyncTarget target,
      String rawXml,
      SourcePosition position) {
    return new PublicationSyncItem(result, target, rawXml, position);
  }
}
