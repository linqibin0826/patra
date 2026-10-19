package dev.patra.catalog.infra.batch.publication.update;

import dev.patra.catalog.domain.port.repository.PublicationSyncRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/// 按 `DeleteCitation` 软删除文献的 Tasklet，在文章 Step 之后执行。
///
/// 删除列表由文章 Step 提升到 Job 上下文；删除数写回 Job 上下文。软删除幂等，重跑时不重复计数。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@RequiredArgsConstructor
public class PubmedDeleteCitationTasklet implements Tasklet {

  private final PublicationSyncRepository syncRepository;
  private final Clock clock;

  /// 执行软删除。
  ///
  /// @param contribution Step 贡献
  /// @param chunkContext chunk 上下文
  /// @return 完成
  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    ExecutionContext jobContext =
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext();
    List<?> raw =
        jobContext.containsKey(PubmedUpdateContextKeys.DELETED_PMIDS)
            ? (List<?>) jobContext.get(PubmedUpdateContextKeys.DELETED_PMIDS)
            : List.of();
    List<String> pmids = raw.stream().map(String::valueOf).toList();
    int deleted = pmids.isEmpty() ? 0 : syncRepository.softDeleteByPmids(pmids, clock.instant());
    jobContext.putInt(PubmedUpdateContextKeys.COUNT_DELETED, deleted);
    log.info("DeleteCitation 软删除：列表 {} 条，实际删除 {} 条", pmids.size(), deleted);
    return RepeatStatus.FINISHED;
  }
}
