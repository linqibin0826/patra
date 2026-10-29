package dev.patra.catalog.infra.publication.persistence;

import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedFileStatus;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileRecord;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileRef;
import dev.patra.catalog.domain.publication.port.repository.PubmedFileRepository;
import dev.patra.catalog.infra.publication.persistence.dao.PubmedFileDao;
import dev.patra.catalog.infra.publication.persistence.entity.PubmedFileEntity;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `PubmedFileRepository` 的 JPA 实现。
///
/// 写方法要求调用方的事务（`MANDATORY`）：增量同步 Handler 用 `TransactionOperations` 为每次写入开短事务，
/// Spring Batch 作业在事务外启动；读方法各是一条查询，不需要事务。
///
/// @author linqibin
/// @since 0.11.0
@Component
@RequiredArgsConstructor
public class PubmedFileRepositoryAdapter implements PubmedFileRepository {

  private static final int MAX_ERROR_LENGTH = 2000;

  private final PubmedFileDao dao;

  /// 返回最后一个成功文件的文件号。
  ///
  /// @return 文件号，没有成功文件时为空
  @Override
  public OptionalInt findLastCompletedSeq(PubmedFileKind kind) {
    Integer max = dao.findMaxSeqByKindAndStatus(kind, PubmedFileStatus.COMPLETED);
    return max == null ? OptionalInt.empty() : OptionalInt.of(max);
  }

  /// 返回最小的未完成（失败或中断）文件的文件号。
  ///
  /// @return 文件号，没有未完成文件时为空
  @Override
  public OptionalInt findFirstUnfinishedSeq(PubmedFileKind kind) {
    Integer min = dao.findMinSeqByKindAndStatusNot(kind, PubmedFileStatus.COMPLETED);
    return min == null ? OptionalInt.empty() : OptionalInt.of(min);
  }

  /// 按文件名查记录。
  ///
  /// @param fileName 文件名
  /// @return 记录
  @Override
  public Optional<PubmedFileRecord> findByFileName(String fileName) {
    return dao.findByFileName(fileName).map(PubmedFileRepositoryAdapter::toRecord);
  }

  /// 标记文件开始处理；已有记录时覆盖为 RUNNING 并清空计数、结束时间与错误。
  ///
  /// @param ref 文件
  /// @param scope 导入范围
  /// @param generation 这次所用的作业 generation，可空
  /// @param startedAt 开始时间
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void markRunning(
      PubmedFileRef ref, PubmedImportScope scope, String generation, Instant startedAt) {
    PubmedFileEntity entity =
        dao.findByFileName(ref.fileName()).orElseGet(PubmedFileRepositoryAdapter::newEntity);
    entity.setFileName(ref.fileName());
    entity.setFileSeq(ref.seq());
    entity.setKind(ref.kind());
    entity.setStatus(PubmedFileStatus.RUNNING);
    entity.setScope(scope);
    applyCounts(entity, PubmedFileCounts.empty());
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
  @Transactional(propagation = Propagation.MANDATORY)
  public void markCompleted(String fileName, PubmedFileCounts counts, Instant finishedAt) {
    PubmedFileEntity entity = requireEntity(fileName);
    entity.setStatus(PubmedFileStatus.COMPLETED);
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
  @Transactional(propagation = Propagation.MANDATORY)
  public void markFailed(String fileName, String error, Instant finishedAt) {
    PubmedFileEntity entity = requireEntity(fileName);
    entity.setStatus(PubmedFileStatus.FAILED);
    entity.setFinishedAt(finishedAt);
    entity.setLastError(truncate(error));
  }

  /// 取已标记 RUNNING 的记录，不存在说明调用顺序错了。
  private PubmedFileEntity requireEntity(String fileName) {
    return dao.findByFileName(fileName)
        .orElseThrow(() -> new IllegalStateException("进度表里没有文件 " + fileName + "，应先 markRunning"));
  }

  /// 新建实体并分配雪花 ID。
  private static PubmedFileEntity newEntity() {
    PubmedFileEntity entity = new PubmedFileEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    return entity;
  }

  /// 把计数写到实体上。
  private static void applyCounts(PubmedFileEntity entity, PubmedFileCounts counts) {
    entity.setCreatedCount(counts.created());
    entity.setUpdatedCount(counts.updated());
    entity.setDeletedCount(counts.deleted());
    entity.setSkippedAbsentCount(counts.skippedAbsent());
    entity.setSkippedInvalidCount(counts.skippedInvalid());
    entity.setDoiConflictCount(counts.doiConflicts());
    entity.setSkipErrorCount(counts.skipErrors());
    entity.setSkippedStaleCount(counts.skippedStale());
  }

  /// 截断错误信息到列宽。
  private static String truncate(String error) {
    if (error == null || error.length() <= MAX_ERROR_LENGTH) {
      return error;
    }
    return error.substring(0, MAX_ERROR_LENGTH);
  }

  /// 实体转领域记录。
  private static PubmedFileRecord toRecord(PubmedFileEntity e) {
    return PubmedFileRecord.builder()
        .fileName(e.getFileName())
        .fileSeq(e.getFileSeq())
        .kind(e.getKind())
        .status(e.getStatus())
        .scope(e.getScope())
        .counts(
            new PubmedFileCounts(
                e.getCreatedCount(),
                e.getUpdatedCount(),
                e.getDeletedCount(),
                e.getSkippedAbsentCount(),
                e.getSkippedInvalidCount(),
                e.getDoiConflictCount(),
                e.getSkipErrorCount(),
                e.getSkippedStaleCount()))
        .startedAt(e.getStartedAt())
        .finishedAt(e.getFinishedAt())
        .lastError(e.getLastError())
        .generation(e.getJobGeneration())
        .build();
  }
}
