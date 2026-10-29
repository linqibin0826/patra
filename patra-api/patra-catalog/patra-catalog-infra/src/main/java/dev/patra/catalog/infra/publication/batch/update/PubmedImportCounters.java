package dev.patra.catalog.infra.publication.batch.update;

import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import java.util.concurrent.atomic.AtomicInteger;

/// 一个 updatefiles 文件处理过程中的计数器。
///
/// 由 Job 配置注册为 `@StepScope` bean，同一 Step 的处理器、写入器、SkipListener 共用一个实例。
/// 注入的是 CGLIB 代理，只能通过方法访问——直接读写字段拿到的是代理自己未初始化的字段。
///
/// @author linqibin
/// @since 0.11.0
public class PubmedImportCounters {

  private final AtomicInteger created = new AtomicInteger();
  private final AtomicInteger updated = new AtomicInteger();
  private final AtomicInteger skippedAbsent = new AtomicInteger();
  private final AtomicInteger skippedInvalid = new AtomicInteger();
  private final AtomicInteger doiConflicts = new AtomicInteger();
  private final AtomicInteger skipErrors = new AtomicInteger();
  private final AtomicInteger skippedStale = new AtomicInteger();

  /// 累加新建数。
  ///
  /// @param count 本次新建条数
  public void addCreated(int count) {
    created.addAndGet(count);
  }

  /// 累加覆盖更新数。
  ///
  /// @param count 本次覆盖条数
  public void addUpdated(int count) {
    updated.addAndGet(count);
  }

  /// `EXISTING_ONLY` 下库里没有的记录 +1。
  public void incrementSkippedAbsent() {
    skippedAbsent.incrementAndGet();
  }

  /// 处理器判为无效的记录 +1。
  public void incrementSkippedInvalid() {
    skippedInvalid.incrementAndGet();
  }

  /// 累加 DOI 冲突数。
  ///
  /// @param count 本次冲突次数
  public void addDoiConflicts(int count) {
    doiConflicts.addAndGet(count);
  }

  /// 比库里版本旧而跳过一条。
  public void incrementSkippedStale() {
    skippedStale.incrementAndGet();
  }

  /// 写入出错被跳过的记录 +1。
  public void incrementSkipErrors() {
    skipErrors.incrementAndGet();
  }

  /// 返回当前计数快照。
  ///
  /// @param deleted 删除数（由删除步骤另行统计）
  /// @return 计数快照
  public PubmedFileCounts snapshot(int deleted) {
    return new PubmedFileCounts(
        created.get(),
        updated.get(),
        deleted,
        skippedAbsent.get(),
        skippedInvalid.get(),
        doiConflicts.get(),
        skipErrors.get(),
        skippedStale.get());
  }
}
