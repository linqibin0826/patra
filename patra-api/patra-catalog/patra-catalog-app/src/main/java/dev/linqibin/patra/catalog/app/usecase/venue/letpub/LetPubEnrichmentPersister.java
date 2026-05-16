package dev.linqibin.patra.catalog.app.usecase.venue.letpub;

import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort;
import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort.PersistStats;
import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubVenueData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// LetPub 富化持久化事务边界 bean。
///
/// **唯一职责**：给 [LetPubEnrichmentPersistPort#persist] 的调用包一层
/// `@Transactional(REQUIRES_NEW)` 事务。自身没有任何业务逻辑。
///
/// **为什么需要这个 bean**：[LetPubEnrichmentWorker#processVenue] 要先做
/// HTTP 爬取和封面下载（耗时以秒计），然后写 DB。老实现把 `@Transactional`
/// 放在 `processVenue` 上会让整个耗时窗口都占用 HikariCP 连接，并发下极易
/// 打满连接池。把事务边界下移到 Persister 这个单方法 bean，Worker 内部不
/// 再持有 DB 连接直到调用 `persister.persist(...)` 那一刻。跨 bean 调用
/// 触发 Spring AOP 代理，事务语义与直接标注在 Worker 上等效但作用域更紧凑。
///
/// **为什么不直接在 PersistPort 上加 `@Transactional`**：项目规则
/// `rules/tech/jpa.md` 规定"只在 Application 层使用 `@Transactional`"。
/// PersistPort 的 domain 定义和 infra 实现都不在 app 层。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class LetPubEnrichmentPersister {

  private final LetPubEnrichmentPersistPort persistPort;

  /// 在独立事务里持久化一个 venue 的 LetPub 富化结果。
  ///
  /// 调用方必须已经完成 HTTP 爬取和封面下载——本方法只做 DB 写入。
  /// `REQUIRES_NEW` 确保即便外层调用方已有事务，本次写入也在独立事务里，
  /// 任何 `Exception` 触发本事务回滚但不影响外层。
  ///
  /// @param venueId 目标 venue 主键
  /// @param data LetPub 爬取数据，不为 null
  /// @param coverObjectKey 新下载的封面对象键，若跳过下载则传 null
  /// @return PersistPort 返回的 [PersistStats]，供调用方记录日志
  /// @throws RuntimeException 任何持久化异常都会向上传播并触发事务回滚
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public PersistStats persist(long venueId, LetPubVenueData data, String coverObjectKey) {
    return persistPort.persist(venueId, data, coverObjectKey);
  }
}
