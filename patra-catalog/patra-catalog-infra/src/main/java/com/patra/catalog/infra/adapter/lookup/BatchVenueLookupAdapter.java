package com.patra.catalog.infra.adapter.lookup;

import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.port.lookup.VenueLookupPort;
import com.patra.catalog.domain.port.repository.VenueRepository;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

/// 批处理专用 Venue 查找适配器。
///
/// 为 Spring Batch Step 提供 Step 级别缓存的 VenueLookupPort 实现。
/// 组合 DefaultVenueLookupAdapter + CachingVenueLookupDecorator。
///
/// **生命周期**：`@StepScope` 确保每个 Step 执行时创建新实例，
/// 缓存在 Step 结束后自动销毁，不会跨 Job 污染。
///
/// **使用方式**：
///
/// ```java
/// // 在 Job 配置中通过 @Qualifier 注入
/// @Bean
/// public PubmedArticleItemProcessor processor(
///     @Qualifier("batchVenueLookupAdapter") VenueLookupPort venueLookupPort) {
///   return new PubmedArticleItemProcessor(repo, venueLookupPort, gateway);
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component("batchVenueLookupAdapter")
@StepScope
public class BatchVenueLookupAdapter implements VenueLookupPort {

  private final CachingVenueLookupDecorator cachingDecorator;

  /// 构造函数。
  ///
  /// @param venueRepository Venue 仓储（用于查询和缓存预热）
  public BatchVenueLookupAdapter(VenueRepository venueRepository) {
    this.cachingDecorator = new CachingVenueLookupDecorator(venueRepository);
    log.debug("BatchVenueLookupAdapter 实例已创建（Step 级别缓存）");
  }

  @Override
  public Optional<VenueId> findByNlmId(String nlmId) {
    return cachingDecorator.findByNlmId(nlmId);
  }

  @Override
  public Optional<VenueId> findByIssn(String issn) {
    return cachingDecorator.findByIssn(issn);
  }

  @Override
  public Optional<VenueId> findByPriority(String nlmId, Collection<String> issns) {
    return cachingDecorator.findByPriority(nlmId, issns);
  }

  /// 预热缓存。
  ///
  /// @param nlmIds NLM ID 集合
  public void warmup(Collection<String> nlmIds) {
    cachingDecorator.warmup(nlmIds);
  }

  /// 获取缓存统计。
  ///
  /// @return 缓存统计描述
  public String getStats() {
    return cachingDecorator.getStats();
  }
}
