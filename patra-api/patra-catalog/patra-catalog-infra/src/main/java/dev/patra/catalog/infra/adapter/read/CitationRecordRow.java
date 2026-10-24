package dev.patra.catalog.infra.adapter.read;

/// [dev.patra.catalog.infra.persistence.dao.CitationRecordDao#findRecords] 的接口投影。
///
/// getter 名与 SQL 列别名一一对应（别名在 SQL 中加双引号保持 camelCase）。
///
/// @author linqibin
/// @since 0.1.0
public interface CitationRecordRow {

  /// 文献主键
  ///
  /// @return 文献主键
  Long getId();

  /// PubMed ID，可空
  ///
  /// @return PubMed ID，可空
  String getPmid();

  /// PMCID，可空
  ///
  /// @return PMCID，可空
  String getPmcid();

  /// PII，可空
  ///
  /// @return PII，可空
  String getPii();

  /// DOI，可空
  ///
  /// @return DOI，可空
  String getDoi();

  /// 标题原文（含内联标记）
  ///
  /// @return 标题原文（含内联标记）
  String getTitle();

  /// 期刊全称，可空
  ///
  /// @return 期刊全称，可空
  String getJournalTitle();

  /// 期刊 ISO 缩写，可空
  ///
  /// @return 期刊 ISO 缩写，可空
  String getIsoAbbreviation();

  /// 期刊 MedlineTA，可空
  ///
  /// @return 期刊 MedlineTA，可空
  String getMedlineAbbreviation();

  /// 卷，可空
  ///
  /// @return 卷，可空
  String getVolume();

  /// 期，可空
  ///
  /// @return 期，可空
  String getIssue();

  /// MEDLINE 页码原文，可空
  ///
  /// @return MEDLINE 页码原文，可空
  String getMedlinePgn();

  /// 首页，可空
  ///
  /// @return 首页，可空
  String getStartPage();

  /// 尾页，可空
  ///
  /// @return 尾页，可空
  String getEndPage();

  /// 出版年，可空
  ///
  /// @return 出版年，可空
  Integer getPublicationYear();

  /// 出版月，可空
  ///
  /// @return 出版月，可空
  Integer getPublicationMonth();

  /// 出版日，可空
  ///
  /// @return 出版日，可空
  Integer getPublicationDay();

  /// 语言代码，可空
  ///
  /// @return 语言代码，可空
  String getLanguage();
}
