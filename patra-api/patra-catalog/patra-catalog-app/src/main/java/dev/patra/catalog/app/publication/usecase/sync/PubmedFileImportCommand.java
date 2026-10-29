package dev.patra.catalog.app.publication.usecase.sync;

import dev.patra.catalog.domain.kernel.exception.CatalogScheduleParameterException;
import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.tool.cqrs.Command;
import lombok.Builder;

/// 处理一段 PubMed 文件的命令（spec 第 5.8 节）。
///
/// - `kind = BASELINE`：必须给 `from`，`to` 缺省等于 `from`
/// - `kind = UPDATE`：不给 `from` 时从进度续跑（只看 UPDATE 的进度行），`to` 缺省为最新
/// - `reprocess = false` 跳过已完成的文件；`true` 不跳过，已完成的文件换新的作业 generation
///
/// @param kind 文件种类
/// @param baseUrl 该种类文件的下载目录
/// @param filePrefix 文件名前缀（如 `pubmed26n`）
/// @param startSeq UPDATE 进度表为空时的起点
/// @param scope 导入范围
/// @param from 起始序号，可空
/// @param to 结束序号（含），可空
/// @param maxFiles 最多处理的文件数，可空
/// @param reprocess 是否重处理已完成的文件
/// @author linqibin
/// @since 0.1.0
@Builder
public record PubmedFileImportCommand(
    PubmedFileKind kind,
    String baseUrl,
    String filePrefix,
    int startSeq,
    PubmedImportScope scope,
    Integer from,
    Integer to,
    Integer maxFiles,
    boolean reprocess)
    implements Command<PubmedFileImportResult> {

  /// 紧凑构造器：校验参数组合。
  ///
  /// @throws CatalogScheduleParameterException 参数不合法时
  public PubmedFileImportCommand {
    if (kind == null) {
      throw new CatalogScheduleParameterException("kind 不能为空");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new CatalogScheduleParameterException("baseUrl 不能为空");
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
    if (kind == PubmedFileKind.BASELINE && from == null) {
      throw new CatalogScheduleParameterException("BASELINE 必须指定 from");
    }
    requirePositive("from", from);
    requirePositive("to", to);
    requirePositive("maxFiles", maxFiles);
    if (from != null && to != null && to < from) {
      throw new CatalogScheduleParameterException("to 不能小于 from：from=" + from + "，to=" + to);
    }
  }

  /// 可空参数非空时必须 ≥ 1。
  ///
  /// @param name 参数名
  /// @param value 值，可空
  private static void requirePositive(String name, Integer value) {
    if (value != null && value < 1) {
      throw new CatalogScheduleParameterException(name + " 必须不小于 1，当前值：" + value);
    }
  }
}
