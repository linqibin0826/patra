package dev.patra.catalog.domain.event;

/// PMC 全文已抓取：全文子域在写入全文记录的同一事务里发布，文献子域同步订阅，把文献标为 PMC 开放获取。
///
/// @param publicationId 文献 ID
/// @param pmcid PMCID
/// @param licenseCode PMC 元数据里的许可原值，可空
/// @param manuscript 是否作者手稿
/// @author linqibin
/// @since 0.1.0
public record PmcFullTextFetchedEvent(
    long publicationId, String pmcid, String licenseCode, boolean manuscript) {}
