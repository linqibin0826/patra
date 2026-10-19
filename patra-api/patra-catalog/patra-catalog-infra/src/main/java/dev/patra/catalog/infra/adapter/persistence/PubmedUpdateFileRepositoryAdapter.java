package dev.patra.catalog.infra.adapter.persistence;

import dev.patra.catalog.domain.model.enums.PubmedUpdateFileStatus;
import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileCounts;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileRecord;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileRef;
import dev.patra.catalog.domain.port.repository.PubmedUpdateFileRepository;
import dev.patra.catalog.infra.persistence.dao.PubmedUpdateFileDao;
import dev.patra.catalog.infra.persistence.entity.PubmedUpdateFileEntity;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// `PubmedUpdateFileRepository` 的 JPA 实现。
///
/// 写方法自带事务：调用方（增量同步 Handler）在事务外启动 Spring Batch 作业，本身不开事务。
///
/// @author linqibin
/// @since 0.11.0
@Component
@RequiredArgsConstructor
public class PubmedUpdateFileRepositoryAdapter implements PubmedUpdateFileRepository {

  private static final int MAX_ERROR_LENGTH = 2000;

  private final PubmedUpdateFileDao dao;

  /// 返回最后一个成功文件的文件号。
  ///
  /// @return 文件号，没有成功文件时为空
  @Override
  @Transactional(readOnly = true)
  public OptionalInt findLastCompletedSeq() {
    Integer max = dao.findMaxSeqByStatus(PubmedUpdateFileStatus.COMPLETED);
    return max == null ? OptionalInt.empty() : OptionalInt.of(max);
  }

  /// 按文件名查记录。
  ///
  /// @param fileName 文件名
  /// @return 记录
  @Override
  @Transactional(readOnly = true)
  public Optional<PubmedUpdateFileRecord> findByFileName(String fileName) {
    return dao.findByFileName(fileName).map(PubmedUpdateFileRepositoryAdapter::toRecord);
  }

  /// 标记文件开始处理；已有记录时覆盖为 RUNNING 并清空计数、结束时间与错误。
  ///
  /// @param ref 文件
  /// @param scope 导入范围
  /// @param generation 这次所用的作业 generation，可空
  /// @param startedAt 开始时间
  @Override
  @Transactional
  public void markRunning(
      PubmedUpdateFileRef ref, PubmedUpdateScope scope, String generation, Instant startedAt) {
    PubmedUpdateFileEntity entity =
        dao.findByFileName(ref.fileName()).orElseGet(PubmedUpdateFileRepositoryAdapter::newEntity);
    entity.setFileName(ref.fileName());
    entity.setFileSeq(ref.seq());
    entity.setStatus(PubmedUpdateFileStatus.RUNNING);
    entity.setScope(scope);
    applyCounts(entity, PubmedUpdateFileCounts.empty());
    entity.setStartedAt(startedAt);
    entity.setFinishedAt(null);
    entity.setLastError(null);
    entity.setJobGeneration(generation);
    dao.save(entity);
  }

  /// 标记文件处理成功并写入计数。
  ///
  /// @param fileName 文件名
  /// @param counts 计数
  /// @param finishedAt 结束时间
  @Override
  @Transactional
  public void markCompleted(String fileName, PubmedUpdateFileCounts counts, Instant finishedAt) {
    PubmedUpdateFileEntity entity = requireEntity(fileName);
    entity.setStatus(PubmedUpdateFileStatus.COMPLETED);
    applyCounts(entity, counts);
    entity.setFinishedAt(finishedAt);
    entity.setLastError(null);
  }

  /// 标记文件处理失败，错误信息截断到 2000 字符。
  ///
  /// @param fileName 文件名
  /// @param error 错误信息
  /// @param finishedAt 结束时间
  @Override
  @Transactional
  public void markFailed(String fileName, String error, Instant finishedAt) {
    PubmedUpdateFileEntity entity = requireEntity(fileName);
    entity.setStatus(PubmedUpdateFileStatus.FAILED);
    entity.setFinishedAt(finishedAt);
    entity.setLastError(truncate(error));
  }

  /// 取已标记 RUNNING 的记录，不存在说明调用顺序错了。
  private PubmedUpdateFileEntity requireEntity(String fileName) {
    return dao.findByFileName(fileName)
        .orElseThrow(() -> new IllegalStateException("进度表里没有文件 " + fileName + "，应先 markRunning"));
  }

  /// 新建实体并分配雪花 ID。
  private static PubmedUpdateFileEntity newEntity() {
    PubmedUpdateFileEntity entity = new PubmedUpdateFileEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    return entity;
  }

  /// 把计数写到实体上。
  private static void applyCounts(PubmedUpdateFileEntity entity, PubmedUpdateFileCounts counts) {
    entity.setCreatedCount(counts.created());
    entity.setUpdatedCount(counts.updated());
    entity.setDeletedCount(counts.deleted());
    entity.setSkippedAbsentCount(counts.skippedAbsent());
    entity.setSkippedInvalidCount(counts.skippedInvalid());
    entity.setDoiConflictCount(counts.doiConflicts());
    entity.setSkipErrorCount(counts.skipErrors());
  }

  /// 截断错误信息到列宽。
  private static String truncate(String error) {
    if (error == null || error.length() <= MAX_ERROR_LENGTH) {
      return error;
    }
    return error.substring(0, MAX_ERROR_LENGTH);
  }

  /// 实体转领域记录。
  private static PubmedUpdateFileRecord toRecord(PubmedUpdateFileEntity e) {
    return PubmedUpdateFileRecord.builder()
        .fileName(e.getFileName())
        .fileSeq(e.getFileSeq())
        .status(e.getStatus())
        .scope(e.getScope())
        .counts(
            new PubmedUpdateFileCounts(
                e.getCreatedCount(),
                e.getUpdatedCount(),
                e.getDeletedCount(),
                e.getSkippedAbsentCount(),
                e.getSkippedInvalidCount(),
                e.getDoiConflictCount(),
                e.getSkipErrorCount()))
        .startedAt(e.getStartedAt())
        .finishedAt(e.getFinishedAt())
        .lastError(e.getLastError())
        .generation(e.getJobGeneration())
        .build();
  }
}
