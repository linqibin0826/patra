package dev.patra.catalog.infra.publication.batch.update;

/// 增量同步作业在 ExecutionContext 里使用的键。
///
/// 值只放 `java.*` 类型：默认的 `DefaultExecutionContextSerializer` 写入要求 `Serializable`，
/// 读回只放行 `java.*` 与 `org.springframework.batch.**`，领域 record 写不进也读不回。
///
/// @author linqibin
/// @since 0.11.0
public final class PubmedImportContextKeys {

  /// Reader 断点：已读记录数（Step 上下文）。
  public static final String CURRENT_INDEX = "pubmed.update.current.index";

  /// `DeleteCitation` 里的 PMID 列表，`ArrayList<String>`（Step 上下文，提升到 Job 上下文）。
  public static final String DELETED_PMIDS = "pubmed.update.deleted.pmids";

  /// 新建数（Job 上下文）。
  public static final String COUNT_CREATED = "pubmed.update.count.created";

  /// 覆盖更新数（Job 上下文）。
  public static final String COUNT_UPDATED = "pubmed.update.count.updated";

  /// 软删除数（Job 上下文）。
  public static final String COUNT_DELETED = "pubmed.update.count.deleted";

  /// 库里没有而跳过数（Job 上下文）。
  public static final String COUNT_SKIPPED_ABSENT = "pubmed.update.count.skipped-absent";

  /// 无效记录跳过数（Job 上下文）。
  public static final String COUNT_SKIPPED_INVALID = "pubmed.update.count.skipped-invalid";

  /// DOI 冲突数（Job 上下文）。
  public static final String COUNT_DOI_CONFLICTS = "pubmed.update.count.doi-conflicts";

  /// 写入出错跳过数（Job 上下文）。
  public static final String COUNT_SKIP_ERRORS = "pubmed.update.count.skip-errors";

  /// 比库里版本旧而跳过的记录数。
  public static final String COUNT_SKIPPED_STALE = "pubmed.update.count.skipped-stale";

  private PubmedImportContextKeys() {}
}
