package dev.patra.interaction.domain.subject.port.repository;

import dev.patra.interaction.domain.subject.model.aggregate.SubjectAggregate;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SourceRecord;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/// 登记表的持久化。写方法须在调用方的事务里调用。
public interface SubjectRepository {

  /// 按来源内部 ID 查本地登记。
  ///
  /// @param type 对象种类
  /// @param sourceId 来源内部 ID
  /// @return 登记对象；未登记时为空
  Optional<SubjectAggregate> findBySourceId(SubjectType type, long sourceId);

  /// 按身份锚 upsert 一次解析结果：同一身份锚已有行时刷新它的来源 ID、状态（ACTIVE）与快照并保留
  /// `subjectId`，否则新建。别的行若还占着同一个来源 ID（过期缓存），先把它清空并置为 UNAVAILABLE。
  ///
  /// @param type 对象种类
  /// @param record 来源记录（ACTIVE）
  /// @param now 快照时间
  /// @return 写入后的登记对象
  SubjectAggregate upsertResolved(SubjectType type, SourceRecord record, Instant now);

  /// 按 `subjectId` 升序取一页（对账用）。
  ///
  /// @param type 对象种类
  /// @param afterSubjectId 游标：只取大于它的
  /// @param limit 页大小
  /// @return 一页登记对象；取完时为空
  List<SubjectAggregate> findPage(SubjectType type, long afterSubjectId, int limit);

  /// 写回对账结果。来源 ID 有变化的行，先清空别的行上同值的来源 ID。
  ///
  /// @param subjects 对账后的登记对象
  void saveReconciled(List<SubjectAggregate> subjects);

  /// 登记一个本地对象：身份锚 `{前缀}:{localId}`，来源 ID 就是 `localId`，ACTIVE。已登记过就违反唯一约束。
  ///
  /// @param type 本地对象种类
  /// @param localId 所属子域里的 ID
  /// @param owner 所有者
  /// @param snapshot 展示快照
  /// @param now 快照时间
  /// @return 写入后的登记对象
  SubjectAggregate insertLocal(
      SubjectType type, long localId, SubjectOwner owner, SubjectSnapshot snapshot, Instant now);

  /// 改一个本地对象的状态与快照；所有者不变。
  ///
  /// @param type 本地对象种类
  /// @param localId 所属子域里的 ID
  /// @param state 新状态
  /// @param snapshot 新快照
  /// @param now 快照时间
  /// @return 有这一行时为 `true`
  boolean updateLocal(
      SubjectType type, long localId, SubjectState state, SubjectSnapshot snapshot, Instant now);
}
