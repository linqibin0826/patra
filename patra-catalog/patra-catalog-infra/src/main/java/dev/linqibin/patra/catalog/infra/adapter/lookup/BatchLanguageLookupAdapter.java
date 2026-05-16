package dev.linqibin.patra.catalog.infra.adapter.lookup;

import dev.linqibin.patra.catalog.domain.port.lookup.LanguageLookupPort;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

/// 批处理专用语言查找适配器。
///
/// 为 Spring Batch Step 提供 Step 级别缓存的 LanguageLookupPort 实现。
/// 组合 DefaultLanguageLookupAdapter + CachingLanguageLookupDecorator。
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
///     @Qualifier("batchLanguageLookupAdapter") LanguageLookupPort languageLookupPort) {
///   return new PubmedArticleItemProcessor(..., languageLookupPort);
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component("batchLanguageLookupAdapter")
@StepScope
public class BatchLanguageLookupAdapter implements LanguageLookupPort {

  private final CachingLanguageLookupDecorator cachingDecorator;

  /// 构造函数。
  ///
  /// @param defaultAdapter 默认无缓存实现
  public BatchLanguageLookupAdapter(DefaultLanguageLookupAdapter defaultAdapter) {
    // 使用装饰器包装默认实现
    this.cachingDecorator = new CachingLanguageLookupDecorator(defaultAdapter);
    log.debug("BatchLanguageLookupAdapter 实例已创建（Step 级别缓存）");
  }

  @Override
  public Map<String, String> resolve(Set<String> iso639Codes) {
    return cachingDecorator.resolve(iso639Codes);
  }

  /// 获取缓存统计。
  ///
  /// @return 缓存统计描述
  public String getStats() {
    return cachingDecorator.getStats();
  }

  /// 清空缓存。
  ///
  /// 通常不需要手动调用，`@StepScope` 会在 Step 结束后自动销毁实例。
  public void clear() {
    cachingDecorator.clear();
  }
}
