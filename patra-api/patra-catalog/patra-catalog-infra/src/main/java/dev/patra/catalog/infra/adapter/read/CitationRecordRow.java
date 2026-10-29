package dev.patra.catalog.infra.adapter.read;

/// [CitationRecordReadAdapter] 的 SQL 查询行，组件名与列别名一一对应。
///
/// @param id 文献主键
/// @param pmid PubMed ID，可空
/// @param pmcid PMCID，可空
/// @param pii PII，可空
/// @param doi DOI，可空
/// @param title 标题原文（含内联标记）
/// @param journalTitle 期刊全称，可空
/// @param isoAbbreviation 期刊 ISO 缩写，可空
/// @param medlineAbbreviation 期刊 MedlineTA，可空
/// @param volume 卷，可空
/// @param issue 期，可空
/// @param medlinePgn MEDLINE 页码原文，可空
/// @param startPage 首页，可空
/// @param endPage 尾页，可空
/// @param publicationYear 出版年，可空
/// @param publicationMonth 出版月，可空
/// @param publicationDay 出版日，可空
/// @param language 语言代码，可空
/// @author linqibin
/// @since 0.1.0
record CitationRecordRow(
    Long id,
    String pmid,
    String pmcid,
    String pii,
    String doi,
    String title,
    String journalTitle,
    String isoAbbreviation,
    String medlineAbbreviation,
    String volume,
    String issue,
    String medlinePgn,
    String startPage,
    String endPage,
    Integer publicationYear,
    Integer publicationMonth,
    Integer publicationDay,
    String language) {}
