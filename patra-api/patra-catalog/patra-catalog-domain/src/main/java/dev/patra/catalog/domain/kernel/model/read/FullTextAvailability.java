package dev.patra.catalog.domain.kernel.model.read;

import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;

/// 一篇文献的全文是否可读、PDF 是否可下载（工程 spec 第 7.1 节，读端共用）。
///
/// 撤稿判定放在读端：增量同步会把 PubMed 的撤稿类型带进来，已抓取的全文在文献被撤稿后自动停止展示。
///
/// @param readable 可读全文：全文记录 `PARSED` 且不是撤稿文献
/// @param pdfDownloadable PDF 可下载：有 PDF 对象且不是撤稿文献（不要求 `PARSED`）
/// @author linqibin
/// @since 0.11.0
public record FullTextAvailability(boolean readable, boolean pdfDownloadable) {

  /// 撤稿文献的 MeSH 发表类型 UI（Retracted Publication）。`D016440` 是撤稿声明本身，不算。
  public static final String RETRACTED_PUBLICATION_TYPE_ID = "D016441";

  /// 按全文状态、是否有 PDF 对象、是否撤稿判定。
  ///
  /// @param status 全文状态，没有全文记录时为 `null`
  /// @param hasPdfObject 是否有 PDF 对象
  /// @param retracted 是否撤稿文献
  /// @return 判定结果
  public static FullTextAvailability of(
      FullTextStatus status, boolean hasPdfObject, boolean retracted) {
    return new FullTextAvailability(
        status == FullTextStatus.PARSED && !retracted, hasPdfObject && !retracted);
  }
}
