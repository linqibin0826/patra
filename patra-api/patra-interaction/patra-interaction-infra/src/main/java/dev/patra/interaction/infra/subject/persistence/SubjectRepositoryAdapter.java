package dev.patra.interaction.infra.subject.persistence;

import dev.patra.interaction.domain.subject.model.aggregate.SubjectAggregate;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.source.SourceRecord;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.repository.SubjectRepository;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `SubjectRepository` 的原生 SQL 实现。表只有一张、查询都走索引，不建 JPA 实体。所有者两列只在登记本地对象时
/// 写入，之后没有任何语句改它们。
///
/// 写方法不开事务，由 app 层的事务包住（`upsertResolved` 的两条语句、`saveReconciled` 的一页）。
@Component
@RequiredArgsConstructor
public class SubjectRepositoryAdapter implements SubjectRepository {

  /// 别的行还占着这个来源 ID：它是过期缓存，清掉并置为失效，下一次对账按它自己的身份锚补回。
  private static final String RELEASE_SOURCE_ID =
      """
      UPDATE subject.registry SET source_id = NULL, state = 'UNAVAILABLE'
       WHERE target_type = :type AND source_id = :sourceId AND subject_id <> :subjectId
      """;

  /// 同一身份锚的 upsert 用事务级 advisory lock 串行化，键为 `subject.registry:{类型编码}:{身份锚}`。
  ///
  /// 只靠 `ON CONFLICT (target_type, natural_key)` 不够：两个事务都通过它的预检查后，非判定索引
  /// `uk_registry_source_id` 会直接报唯一冲突，走不到 `DO UPDATE`（两个人同时首次收藏同一篇）。
  private static final String LOCK_PREFIX = "subject.registry:";

  private final JdbcClient jdbc;
  private final SubjectRows rows;

  /// 按来源内部 ID 查本地登记（走部分唯一索引）。
  ///
  /// @param type 对象种类
  /// @param sourceId 来源内部 ID
  /// @return 登记对象
  @Override
  public Optional<SubjectAggregate> findBySourceId(SubjectType type, long sourceId) {
    return jdbc.sql(
            "SELECT "
                + SubjectRows.COLUMNS
                + " FROM subject.registry WHERE target_type = :type AND source_id = :sourceId")
        .param("type", type.code())
        .param("sourceId", sourceId)
        .query(rows.aggregateMapper())
        .optional();
  }

  /// 拿身份锚的事务锁，先释放别的行上的同一来源 ID，再按身份锚 upsert。
  ///
  /// @param type 对象种类
  /// @param record 来源记录
  /// @param now 快照时间
  /// @return 写入后的登记对象
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public SubjectAggregate upsertResolved(SubjectType type, SourceRecord record, Instant now) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
        .param("lockKey", LOCK_PREFIX + type.code() + ":" + record.naturalKey())
        .query((rs, rowNum) -> 1)
        .single();
    jdbc.sql(
            """
            UPDATE subject.registry SET source_id = NULL, state = 'UNAVAILABLE'
             WHERE target_type = :type AND source_id = :sourceId AND natural_key <> :naturalKey
            """)
        .param("type", type.code())
        .param("sourceId", record.sourceId())
        .param("naturalKey", record.naturalKey().toString())
        .update();
    return jdbc.sql(
            """
            INSERT INTO subject.registry
                   (subject_id, target_type, natural_key, source_id, state, snapshot, snapshot_at)
            VALUES (:subjectId, :type, :naturalKey, :sourceId, 'ACTIVE', CAST(:snapshot AS jsonb),
                    :snapshotAt)
            ON CONFLICT (target_type, natural_key) DO UPDATE
               SET source_id = EXCLUDED.source_id, state = 'ACTIVE',
                   snapshot = EXCLUDED.snapshot, snapshot_at = EXCLUDED.snapshot_at
            RETURNING\s"""
                + SubjectRows.COLUMNS)
        .param("subjectId", SnowflakeIdGenerator.getId())
        .param("type", type.code())
        .param("naturalKey", record.naturalKey().toString())
        .param("sourceId", record.sourceId())
        .param("snapshot", rows.writeSnapshot(record.snapshot()))
        .param("snapshotAt", SubjectRows.utc(now))
        .query(rows.aggregateMapper())
        .single();
  }

  /// 按 `subject_id` 升序取一页。
  ///
  /// @param type 对象种类
  /// @param afterSubjectId 游标
  /// @param limit 页大小
  /// @return 一页
  @Override
  public List<SubjectAggregate> findPage(SubjectType type, long afterSubjectId, int limit) {
    return jdbc.sql(
            "SELECT "
                + SubjectRows.COLUMNS
                + " FROM subject.registry WHERE target_type = :type AND subject_id > :after"
                + " ORDER BY subject_id LIMIT :limit")
        .param("type", type.code())
        .param("after", afterSubjectId)
        .param("limit", limit)
        .query(rows.aggregateMapper())
        .list();
  }

  /// 逐行写回：有来源 ID 的行先释放别的行上的同值，再更新本行。
  ///
  /// @param subjects 对账后的登记对象
  @Override
  public void saveReconciled(List<SubjectAggregate> subjects) {
    for (SubjectAggregate subject : subjects) {
      if (subject.getSourceId() != null) {
        jdbc.sql(RELEASE_SOURCE_ID)
            .param("type", subject.getType().code())
            .param("sourceId", subject.getSourceId())
            .param("subjectId", subject.getSubjectId().value())
            .update();
      }
      jdbc.sql(
              """
              UPDATE subject.registry
                 SET source_id = :sourceId, state = :state, snapshot = CAST(:snapshot AS jsonb),
                     snapshot_at = :snapshotAt
               WHERE subject_id = :subjectId
              """)
          .param("sourceId", subject.getSourceId())
          .param("state", subject.getState().name())
          .param("snapshot", rows.writeSnapshot(subject.getSnapshot()))
          .param("snapshotAt", SubjectRows.utc(subject.getSnapshotAt()))
          .param("subjectId", subject.getSubjectId().value())
          .update();
    }
  }

  /// 插入本地对象的登记行：锚由类型的前缀与 `localId` 拼成（同时校验 `localId` 的写法），来源 ID 就是 `localId`。
  ///
  /// @param type 本地对象种类
  /// @param localId 所属子域里的 ID
  /// @param owner 所有者
  /// @param snapshot 展示快照
  /// @param now 快照时间
  /// @return 写入后的登记对象
  @Override
  public SubjectAggregate insertLocal(
      SubjectType type, long localId, SubjectOwner owner, SubjectSnapshot snapshot, Instant now) {
    NaturalKey naturalKey = NaturalKey.of(type.naturalKeyScheme(), Long.toString(localId));
    return jdbc.sql(
            """
            INSERT INTO subject.registry
                   (subject_id, target_type, natural_key, source_id, state, snapshot, snapshot_at,
                    owner_user_id, owner_anonymous)
            VALUES (:subjectId, :type, :naturalKey, :sourceId, 'ACTIVE', CAST(:snapshot AS jsonb),
                    :snapshotAt, :ownerUserId, :ownerAnonymous)
            RETURNING\s"""
                + SubjectRows.COLUMNS)
        .param("subjectId", SnowflakeIdGenerator.getId())
        .param("type", type.code())
        .param("naturalKey", naturalKey.toString())
        .param("sourceId", localId)
        .param("snapshot", rows.writeSnapshot(snapshot))
        .param("snapshotAt", SubjectRows.utc(now))
        .param("ownerUserId", owner.userId())
        .param("ownerAnonymous", owner.anonymous())
        .query(rows.aggregateMapper())
        .single();
  }

  /// 按类型与来源 ID 改本地对象的状态与快照，不碰所有者两列。
  ///
  /// @param type 本地对象种类
  /// @param localId 所属子域里的 ID
  /// @param state 新状态
  /// @param snapshot 新快照
  /// @param now 快照时间
  /// @return 有这一行时为 `true`
  @Override
  public boolean updateLocal(
      SubjectType type, long localId, SubjectState state, SubjectSnapshot snapshot, Instant now) {
    return jdbc.sql(
                """
                UPDATE subject.registry
                   SET state = :state, snapshot = CAST(:snapshot AS jsonb),
                       snapshot_at = :snapshotAt
                 WHERE target_type = :type AND source_id = :sourceId
                """)
            .param("state", state.name())
            .param("snapshot", rows.writeSnapshot(snapshot))
            .param("snapshotAt", SubjectRows.utc(now))
            .param("type", type.code())
            .param("sourceId", localId)
            .update()
        == 1;
  }
}
