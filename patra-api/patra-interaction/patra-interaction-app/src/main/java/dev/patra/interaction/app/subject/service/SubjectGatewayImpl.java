package dev.patra.interaction.app.subject.service;

import dev.patra.interaction.domain.subject.exception.SubjectCapabilityNotSupportedException;
import dev.patra.interaction.domain.subject.exception.SubjectNotFoundException;
import dev.patra.interaction.domain.subject.model.aggregate.SubjectAggregate;
import dev.patra.interaction.domain.subject.model.enums.SourceState;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SourceRecord;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.domain.subject.port.repository.SubjectRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/// 对象登记：外部对象首次互动前的解析与登记；本地对象的登记与更新（必须在调用方的事务里）。
///
/// 顺序：能力 → key 格式 → 本地按来源 ID 查（命中且正常就返回，不调来源，所以来源不可用不影响已登记对象；
/// 命中但失效就拒绝并带原因码 `SUBJECT_UNAVAILABLE`，复活交给每日对账）→ 本地对象没有来源，未命中即不存在
/// → 外部对象未命中才向来源解析（事务外，不占连接）→ 不存在、没有身份锚都拒绝，已软删拒绝并带
/// `SUBJECT_UNAVAILABLE`，都不写库 → 事务里按身份锚 upsert（换库后同一身份锚复用原 `subjectId`）。
@Component
@RequiredArgsConstructor
public class SubjectGatewayImpl implements SubjectGateway {

  private final SubjectRepository repository;
  private final SubjectSources sources;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 已登记就返回，未登记就解析并登记。
  ///
  /// @param type 对象种类
  /// @param key 对外 key
  /// @param capability 要做的互动
  /// @return 本地 ID
  @Override
  public SubjectId resolveForInteraction(
      SubjectType type, String key, SubjectCapability capability) {
    if (!type.supports(capability)) {
      throw new SubjectCapabilityNotSupportedException(type, capability);
    }
    long sourceId = type.parseKey(key);
    Optional<SubjectAggregate> local = repository.findBySourceId(type, sourceId);
    if (local.isPresent()) {
      if (!local.get().isActive()) {
        throw SubjectNotFoundException.unavailable(type, key);
      }
      return local.get().getSubjectId();
    }
    if (type.isLocal()) {
      throw SubjectNotFoundException.notFound(type, key);
    }
    SourceRecord record =
        sources
            .of(type)
            .resolveByKey(sourceId)
            .orElseThrow(() -> SubjectNotFoundException.notFound(type, key));
    if (record.state() != SourceState.ACTIVE) {
      throw SubjectNotFoundException.unavailable(type, key);
    }
    SubjectAggregate saved =
        transactions.execute(status -> repository.upsertResolved(type, record, clock.instant()));
    return saved.getSubjectId();
  }

  /// 登记本地对象，快照时间取时钟。
  ///
  /// @param type 本地对象种类
  /// @param localId 所属子域里的 ID
  /// @param owner 所有者
  /// @param snapshot 展示快照
  /// @return 本地 ID
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public SubjectId registerLocal(
      SubjectType type, long localId, SubjectOwner owner, SubjectSnapshot snapshot) {
    requireLocal(type);
    Objects.requireNonNull(owner, "owner 不能为 null");
    Objects.requireNonNull(snapshot, "snapshot 不能为 null");
    return repository.insertLocal(type, localId, owner, snapshot, clock.instant()).getSubjectId();
  }

  /// 改本地对象的状态与快照。
  ///
  /// @param type 本地对象种类
  /// @param localId 所属子域里的 ID
  /// @param state 新状态
  /// @param snapshot 新快照
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void updateLocal(
      SubjectType type, long localId, SubjectState state, SubjectSnapshot snapshot) {
    requireLocal(type);
    Objects.requireNonNull(state, "state 不能为 null");
    Objects.requireNonNull(snapshot, "snapshot 不能为 null");
    if (!repository.updateLocal(type, localId, state, snapshot, clock.instant())) {
      throw new IllegalStateException("本地对象没有登记：" + type + ":" + localId);
    }
  }

  /// 本地对象的方法只接受本地类型。
  ///
  /// @param type 对象种类
  /// @throws IllegalArgumentException 外部类型
  private static void requireLocal(SubjectType type) {
    if (!type.isLocal()) {
      throw new IllegalArgumentException("不是本地对象类型：" + type);
    }
  }
}
