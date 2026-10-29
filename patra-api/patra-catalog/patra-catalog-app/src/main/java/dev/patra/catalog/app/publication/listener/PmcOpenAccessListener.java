package dev.patra.catalog.app.publication.listener;

import dev.patra.catalog.domain.fulltext.event.PmcFullTextFetchedEvent;
import dev.patra.catalog.domain.publication.model.vo.PublicationOaLocation;
import dev.patra.catalog.domain.publication.port.repository.PublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/// 订阅全文已抓取事件，把文献标为 PMC 开放获取。
///
/// 同步 `@EventListener`：与发布方同一线程、同一事务；抛出异常时全文记录与开放获取标记一起回滚。
/// 不加 `@Transactional`，也不能用 `@TransactionalEventListener`（提交后才执行，标记会丢）。
///
/// @author linqibin
/// @since 0.1.0
@Component
@RequiredArgsConstructor
public class PmcOpenAccessListener {

  private final PublicationRepository publicationRepository;

  /// 全文已抓取：只替换该文献的 PMC 位置，并把文献标为开放获取。
  ///
  /// @param event 事件
  @EventListener
  public void on(PmcFullTextFetchedEvent event) {
    publicationRepository.markPmcOpenAccess(
        event.publicationId(),
        PublicationOaLocation.ofPmc(event.pmcid(), event.licenseCode(), event.manuscript()));
  }
}
