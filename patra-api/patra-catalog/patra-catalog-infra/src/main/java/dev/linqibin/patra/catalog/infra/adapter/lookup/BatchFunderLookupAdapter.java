package dev.linqibin.patra.catalog.infra.adapter.lookup;

import dev.linqibin.patra.catalog.domain.port.lookup.FunderLookupPort;
import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationExternalIdDao;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

/// 批处理专用资助机构查找适配器。
///
/// 为 Spring Batch Step 提供 Step 级别缓存的 FunderLookupPort 实现。
/// 内部使用 CachingFunderLookupDecorator 实现缓存逻辑。
///
/// **生命周期**：`@StepScope` 确保每个 Step 执行时创建新实例，
/// 缓存在 Step 结束后自动销毁，不会跨 Job 污染。
///
/// **使用方式**：
///
/// ```java
/// // 在 Job 配置中通过 @Qualifier 注入
/// @Bean
/// @StepScope
/// public PubmedArticleItemProcessor processor(
///     @Qualifier("batchFunderLookupAdapter") FunderLookupPort funderLookupPort) {
///   return new PubmedArticleItemProcessor(..., funderLookupPort);
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component("batchFunderLookupAdapter")
@StepScope
public class BatchFunderLookupAdapter implements FunderLookupPort {

  private final CachingFunderLookupDecorator cachingDecorator;

  /// 构造函数。
  ///
  /// @param externalIdDao 外部标识符 DAO
  /// @param organizationDao 机构 DAO
  public BatchFunderLookupAdapter(
      OrganizationExternalIdDao externalIdDao, OrganizationDao organizationDao) {
    this.cachingDecorator = new CachingFunderLookupDecorator(externalIdDao, organizationDao);
    log.debug("BatchFunderLookupAdapter 实例已创建（Step 级别缓存）");

    // 自动预热 FundRef 缓存
    this.cachingDecorator.warmupFundRefCache();
  }

  @Override
  public Optional<Long> findByIdentifier(String funderIdentifier) {
    return cachingDecorator.findByIdentifier(funderIdentifier);
  }

  @Override
  public Optional<Long> findByName(String funderName) {
    return cachingDecorator.findByName(funderName);
  }

  /// 获取缓存统计。
  ///
  /// @return 缓存统计描述
  public String getStats() {
    return cachingDecorator.getStats();
  }
}
