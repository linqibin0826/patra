package dev.linqibin.patra.catalog.app.usecase.venue.scopus;

import dev.linqibin.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort;
import dev.linqibin.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort.PersistStats;
import dev.linqibin.patra.catalog.domain.port.enrichment.ScopusVenueData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// Scopus 富化持久化事务边界 bean。
///
/// **唯一职责**：给 [ScopusEnrichmentPersistPort#persist] 的调用包一层
/// `@Transactional(REQUIRES_NEW)` 事务。自身没有任何业务逻辑。
///
/// **为什么需要这个 bean**：[ScopusEnrichmentWorker#processVenue] 要先做
/// Scopus Serial Title API 的 HTTP 调用，再写 DB。老实现把 `@Transactional`
/// 放在 `processVenue` 上会让 HTTP 响应等待期间占用 HikariCP 连接。把事务
/// 边界下移到 Persister，Worker 不再持有 DB 连接直到调用 `persister.persist(...)`。
/// 跨 bean 调用触发 Spring AOP 代理，事务语义不变但作用域更紧凑。
///
/// **为什么不直接在 PersistPort 上加 `@Transactional`**：项目规则
/// `rules/tech/jpa.md` 规定"只在 Application 层使用 `@Transactional`"。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class ScopusEnrichmentPersister {

  private final ScopusEnrichmentPersistPort persistPort;

  /// 在独立事务里持久化一个 venue 的 Scopus 富化结果。
  ///
  /// 调用方必须已经完成 HTTP 爬取——本方法只做 DB 写入。`REQUIRES_NEW` 确保
  /// 即便外层调用方已有事务，本次写入也在独立事务里，任何 `Exception` 触发
  /// 本事务回滚但不影响外层。
  ///
  /// @param venueId 目标 venue 主键
  /// @param data Scopus API 返回的原始数据，不为 null
  /// @return PersistPort 返回的 [PersistStats]，供调用方记录日志
  /// @throws RuntimeException 任何持久化异常都会向上传播并触发事务回滚
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public PersistStats persist(long venueId, ScopusVenueData data) {
    return persistPort.persist(venueId, data);
  }
}
