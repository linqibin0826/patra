package dev.patra.catalog.infra.publication.batch.update;

import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.publication.model.vo.SourcePosition;
import dev.patra.catalog.domain.publication.port.repository.PublicationSyncRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
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
        jobContext.containsKey(PubmedImportContextKeys.DELETED_PMIDS)
            ? (List<?>) jobContext.get(PubmedImportContextKeys.DELETED_PMIDS)
            : List.of();
    List<String> pmids = raw.stream().map(String::valueOf).toList();
    // 作业参数里只有字符串：由下载地址的文件名重算来源位置，由范围名决定是否写墓碑
    Map<String, Object> params = chunkContext.getStepContext().getJobParameters();
    String downloadUrl = String.valueOf(params.get("downloadUrl"));
    SourcePosition position =
        SourcePosition.ofFileName(downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1));
    boolean writeTombstones =
        PubmedImportScope.ALL.name().equals(String.valueOf(params.get("scope")));
    int deleted =
        pmids.isEmpty()
            ? 0
            : syncRepository.softDeleteByPmids(pmids, position, writeTombstones, clock.instant());
    jobContext.putInt(PubmedImportContextKeys.COUNT_DELETED, deleted);
    log.info("DeleteCitation 软删除：列表 {} 条，实际删除 {} 条", pmids.size(), deleted);
    return RepeatStatus.FINISHED;
  }
}
