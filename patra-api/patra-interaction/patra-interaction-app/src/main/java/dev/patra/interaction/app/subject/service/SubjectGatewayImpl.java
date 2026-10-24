package dev.patra.interaction.app.subject.service;

import dev.patra.interaction.domain.subject.exception.SubjectCapabilityNotSupportedException;
import dev.patra.interaction.domain.subject.exception.SubjectNotFoundException;
import dev.patra.interaction.domain.subject.model.aggregate.SubjectAggregate;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.source.SourceRecord;
import dev.patra.interaction.domain.subject.model.source.SourceState;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.domain.subject.port.repository.SubjectRepository;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 首次互动前的对象登记。
///
/// 顺序：能力 → key 格式 → 本地按来源 ID 查（命中且正常就返回，不调来源，所以来源不可用不影响已登记对象；
/// 命中但失效就拒绝，复活交给每日对账）→ 未命中才向来源解析（事务外，不占连接）→ 不存在、没有 PMID、
/// 已软删都拒绝且不写库 → 事务里按身份锚 upsert（换库后同一 PMID 复用原 `subjectId`）。
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
        throw new SubjectNotFoundException(type, key);
      }
      return local.get().getSubjectId();
    }
    SourceRecord record =
        sources
            .of(type)
            .resolveByKey(sourceId)
            .filter(r -> r.state() == SourceState.ACTIVE)
            .orElseThrow(() -> new SubjectNotFoundException(type, key));
    SubjectAggregate saved =
        transactions.execute(status -> repository.upsertResolved(type, record, clock.instant()));
    return saved.getSubjectId();
  }
}
