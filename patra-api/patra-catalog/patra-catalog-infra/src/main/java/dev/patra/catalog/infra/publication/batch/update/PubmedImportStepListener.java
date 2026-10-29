package dev.patra.catalog.infra.publication.batch.update;

import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import dev.patra.catalog.domain.publication.model.vo.PubmedSourceRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/// PubMed 导入文章 Step 的监听器：给每次 skip 计数，Step 结束时把计数直接写进 Job 上下文。
///
/// 计数直接写 Job 上下文、不经 `ExecutionContextPromotionListener`：`afterStep` 按注册逆序调用，
/// 经提升很容易顺序反掉，计数到不了 Job 上。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@RequiredArgsConstructor
public class PubmedImportStepListener
    implements StepExecutionListener, SkipListener<PubmedSourceRecord, PublicationSyncItem> {

  private final PubmedImportCounters counters;

  /// 读取阶段 skip：计数并记 error。
  ///
  /// @param t 原因
  @Override
  public void onSkipInRead(Throwable t) {
    counters.incrementSkipErrors();
    log.error("PubMed 导入读取记录出错被跳过", t);
  }

  /// 处理阶段 skip：计数并记 error。
  ///
  /// @param item 记录
  /// @param t 原因
  @Override
  public void onSkipInProcess(PubmedSourceRecord item, Throwable t) {
    counters.incrementSkipErrors();
    log.error("PubMed 导入处理记录出错被跳过", t);
  }

  /// 写入阶段 skip：计数并记 error。
  ///
  /// @param item 同步项
  /// @param t 原因
  @Override
  public void onSkipInWrite(PublicationSyncItem item, Throwable t) {
    counters.incrementSkipErrors();
    log.error("PubMed 导入写入出错被跳过：pmid={}", item.result().publication().getPmid(), t);
  }

  /// Step 结束时把计数写进 Job 上下文（删除数由删除步骤另写）。
  ///
  /// @param stepExecution Step 执行
  /// @return 原退出状态
  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    PubmedFileCounts counts = counters.snapshot(0);
    ExecutionContext jobContext = stepExecution.getJobExecution().getExecutionContext();
    jobContext.putInt(PubmedImportContextKeys.COUNT_CREATED, counts.created());
    jobContext.putInt(PubmedImportContextKeys.COUNT_UPDATED, counts.updated());
    jobContext.putInt(PubmedImportContextKeys.COUNT_SKIPPED_ABSENT, counts.skippedAbsent());
    jobContext.putInt(PubmedImportContextKeys.COUNT_SKIPPED_INVALID, counts.skippedInvalid());
    jobContext.putInt(PubmedImportContextKeys.COUNT_DOI_CONFLICTS, counts.doiConflicts());
    jobContext.putInt(PubmedImportContextKeys.COUNT_SKIP_ERRORS, counts.skipErrors());
    jobContext.putInt(PubmedImportContextKeys.COUNT_SKIPPED_STALE, counts.skippedStale());
    log.info("PubMed 导入文章 Step 结束：{}", counts);
    return stepExecution.getExitStatus();
  }
}
