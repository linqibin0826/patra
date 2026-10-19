package dev.patra.catalog.app.usecase.publication.update.command;

import dev.patra.catalog.app.usecase.publication.update.dto.PubmedUpdateSyncResult;
import dev.patra.catalog.domain.exception.CatalogScheduleParameterException;
import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import dev.patra.tool.cqrs.Command;
import lombok.Builder;

/// PubMed updatefiles 增量同步命令。
///
/// `updateUrl`、`filePrefix`、`startSeq`、`scope` 由任务入口从配置补齐（配置类在 adapter 模块，
/// app 层依赖不到）；`fromFile`、`maxFiles` 来自任务参数，可空。
///
/// @param updateUrl updatefiles 目录地址
/// @param filePrefix 文件名前缀，如 `pubmed26n`
/// @param startSeq 进度表为空时的起始文件号
/// @param scope 导入范围
/// @param fromFile 从指定文件号开始（重放用），可空
/// @param maxFiles 本次最多处理几个文件，可空
/// @author linqibin
/// @since 0.11.0
@Builder
public record PubmedUpdateSyncCommand(
    String updateUrl,
    String filePrefix,
    int startSeq,
    PubmedUpdateScope scope,
    Integer fromFile,
    Integer maxFiles)
    implements Command<PubmedUpdateSyncResult> {

  /// 校验参数。
  public PubmedUpdateSyncCommand {
    if (updateUrl == null || updateUrl.isBlank()) {
      throw new CatalogScheduleParameterException("updateUrl 不能为空");
    }
    if (filePrefix == null || filePrefix.isBlank()) {
      throw new CatalogScheduleParameterException("filePrefix 不能为空");
    }
    if (startSeq < 1) {
      throw new CatalogScheduleParameterException("startSeq 必须不小于 1，当前值：" + startSeq);
    }
    if (scope == null) {
      throw new CatalogScheduleParameterException("scope 不能为空");
    }
    if (fromFile != null && fromFile < 1) {
      throw new CatalogScheduleParameterException("fromFile 必须不小于 1，当前值：" + fromFile);
    }
    if (maxFiles != null && maxFiles < 1) {
      throw new CatalogScheduleParameterException("maxFiles 必须不小于 1，当前值：" + maxFiles);
    }
  }
}
