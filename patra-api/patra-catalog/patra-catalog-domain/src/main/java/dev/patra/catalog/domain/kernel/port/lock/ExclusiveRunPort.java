package dev.patra.catalog.domain.kernel.port.lock;

import java.util.Optional;
import java.util.function.Supplier;

/// 同一类任务同一时刻只允许一个实例运行的端口。
///
/// XXL-Job 的子任务与每小时兜底任务是两条任务，会撞车；任务入口用它包住整次运行。
///
/// @author linqibin
/// @since 0.11.0
public interface ExclusiveRunPort {

  /// PubMed 文件导入与本地重算共用的锁名（两者不能并发，否则重算可能用旧原文写回较新的数据）。
  String PUBMED_IMPORT = "catalog.pubmed-import";

  /// 全文抓取任务的锁名。
  String FULLTEXT_FETCH = "catalog.fulltext-fetch";

  /// 全文解析任务的锁名。
  String FULLTEXT_PARSE = "catalog.fulltext-parse";

  /// 取到锁就执行并返回结果，取不到返回空。
  ///
  /// @param lockName 锁名
  /// @param work 要执行的工作；抛出的异常原样抛出，抛出前锁已释放
  /// @param <T> 结果类型
  /// @return 结果；锁被别的实例持有时为空
  <T> Optional<T> runExclusively(String lockName, Supplier<T> work);
}
