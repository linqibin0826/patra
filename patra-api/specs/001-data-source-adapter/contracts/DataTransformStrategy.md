# DataTransformStrategy 数据转换策略接口契约

**版本**: 1.0.0
**模块**: `patra-ingest-domain`
**包路径**: `com.patra.ingest.domain.strategy`
**最后更新**: 2025-11-11

---

## 接口定义

### Java 接口签名

```java
package com.patra.ingest.domain.strategy;

import com.patra.ingest.domain.model.canonical.CanonicalData;
import java.util.List;

/**
 * 数据转换策略接口。
 *
 * <p>负责将外部数据源的原始数据转换为规范数据模型。</p>
 *
 * <h2>设计原则：</h2>
 * <ul>
 *   <li>可插拔：新数据源只需实现对应的转换策略,无需修改核心代码</li>
 *   <li>类型安全：使用泛型明确源类型和目标类型</li>
 *   <li>批量优化：支持批量转换,提升性能</li>
 *   <li>部分成功：单条数据失败不影响其他数据</li>
 * </ul>
 *
 * @param <S> 源数据类型(外部数据源的原始类型,如 PubMedArticle)
 * @param <T> 目标数据类型(CanonicalData 的实现类,如 CanonicalLiterature)
 * @since 1.0.0
 * @see TransformResult
 * @see TransformError
 */
public interface DataTransformStrategy<S, T extends CanonicalData> {

    /**
     * 获取源数据类型。
     *
     * @return 源数据的 Class 对象
     */
    Class<S> getSourceType();

    /**
     * 获取目标数据类型。
     *
     * @return 目标数据的 Class 对象
     */
    Class<T> getTargetType();

    /**
     * 转换单条数据。
     *
     * @param source 源数据
     * @return 转换后的规范数据
     * @throws TransformException 如果转换失败
     */
    T transform(S source);

    /**
     * 批量转换数据,支持部分成功。
     *
     * <p>即使部分数据转换失败,也会继续处理其他数据,
     * 最终返回成功和失败的详细信息。</p>
     *
     * <h3>并发策略：</h3>
     * <ul>
     *   <li>默认实现使用线程池并行转换(配置从 patra-registry 获取)</li>
     *   <li>批次大小：50 条/批(可配置)</li>
     *   <li>线程数：10 个(可配置,全局默认值)</li>
     * </ul>
     *
     * @param sources 源数据列表
     * @return 转换结果(包含成功项和错误列表)
     */
    TransformResult<T> batchTransform(List<S> sources);
}
```

---

## StrategyRegistry (策略注册表接口)

### 接口定义

```java
package com.patra.ingest.domain.strategy;

import com.patra.ingest.domain.model.canonical.CanonicalData;

/**
 * 数据转换策略注册表接口。
 *
 * <p>负责管理和查找数据转换策略。</p>
 *
 * @since 1.0.0
 */
public interface StrategyRegistry {

    /**
     * 根据源类型和目标类型查找策略。
     *
     * <p>时间复杂度: O(1)</p>
     *
     * @param sourceType 源数据类型
     * @param targetType 目标数据类型
     * @param <S> 源数据类型
     * @param <T> 目标数据类型
     * @return 转换策略实例
     * @throws StrategyNotFoundException 如果未找到匹配的策略
     */
    <S, T extends CanonicalData> DataTransformStrategy<S, T> getStrategy(
        Class<S> sourceType,
        Class<T> targetType
    );

    /**
     * 注册策略。
     *
     * @param strategy 策略实例
     */
    void register(DataTransformStrategy<?, ?> strategy);

    /**
     * 检查是否存在指定的策略。
     *
     * @param sourceType 源数据类型
     * @param targetType 目标数据类型
     * @return true 如果存在
     */
    boolean hasStrategy(Class<?> sourceType, Class<?> targetType);
}
```

---

## 实现示例: PubMedToLiteratureStrategy

```java
package com.patra.starter.provenance.pubmed.strategy;

import com.patra.ingest.domain.model.canonical.CanonicalLiterature;
import com.patra.ingest.domain.model.valueobject.Provenance;
import com.patra.ingest.domain.strategy.DataTransformStrategy;
import com.patra.ingest.domain.strategy.TransformResult;
import com.patra.ingest.domain.strategy.TransformError;
import com.patra.starter.provenance.pubmed.model.PubMedArticle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * PubMed 文章到规范文献的转换策略。
 *
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class PubMedToLiteratureStrategy
        implements DataTransformStrategy<PubMedArticle, CanonicalLiterature> {

    @Override
    public Class<PubMedArticle> getSourceType() {
        return PubMedArticle.class;
    }

    @Override
    public Class<CanonicalLiterature> getTargetType() {
        return CanonicalLiterature.class;
    }

    @Override
    public CanonicalLiterature transform(PubMedArticle source) {
        return CanonicalLiterature.builder()
            .id("pubmed:" + source.getPmid())
            .title(source.getTitle())
            .abstractText(source.getAbstractText())
            .authors(transformAuthors(source.getAuthors()))
            .journal(transformJournal(source.getJournal()))
            .identifiers(Map.of(
                "PMID", source.getPmid(),
                "DOI", source.getDoi() != null ? source.getDoi() : "",
                "PMC_ID", source.getPmcId() != null ? source.getPmcId() : ""
            ))
            .publicationDate(source.getPublicationDate())
            .keywords(source.getMeshTerms())
            .provenance(Provenance.ofCode("pubmed"))
            .createdAt(Instant.now())
            .build();
    }

    @Override
    public TransformResult<CanonicalLiterature> batchTransform(List<PubMedArticle> sources) {
        List<CanonicalLiterature> successItems = new CopyOnWriteArrayList<>();
        List<TransformError> errors = new CopyOnWriteArrayList<>();

        // 分批并行转换(批次大小50,线程池大小从配置获取)
        int batchSize = 50;
        List<List<PubMedArticle>> batches = partition(sources, batchSize);

        batches.parallelStream().forEach(batch -> {
            for (int i = 0; i < batch.size(); i++) {
                PubMedArticle article = batch.get(i);
                try {
                    CanonicalLiterature literature = transform(article);
                    successItems.add(literature);
                } catch (Exception e) {
                    int globalIndex = sources.indexOf(article);
                    errors.add(new TransformError(
                        globalIndex,
                        truncate(article.toString(), 1024),  // 截断至1KB
                        e.getMessage(),
                        e
                    ));
                }
            }
        });

        return TransformResult.<CanonicalLiterature>builder()
            .successItems(successItems)
            .errors(errors)
            .build();
    }

    private List<CanonicalLiterature.Author> transformAuthors(List<PubMedArticle.Author> pubmedAuthors) {
        if (pubmedAuthors == null) {
            return List.of();
        }
        return pubmedAuthors.stream()
            .map(author -> CanonicalLiterature.Author.builder()
                .firstName(author.getForeName())
                .lastName(author.getLastName())
                .fullName(author.getCollectiveName())
                .affiliations(author.getAffiliations())
                .build())
            .collect(Collectors.toList());
    }

    private CanonicalLiterature.Journal transformJournal(PubMedArticle.Journal pubmedJournal) {
        if (pubmedJournal == null) {
            return null;
        }
        return CanonicalLiterature.Journal.builder()
            .title(pubmedJournal.getTitle())
            .abbreviation(pubmedJournal.getIsoAbbreviation())
            .issn(pubmedJournal.getIssn())
            .build();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated)";
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        return IntStream.range(0, (list.size() + size - 1) / size)
            .mapToObj(i -> list.subList(i * size, Math.min((i + 1) * size, list.size())))
            .collect(Collectors.toList());
    }
}
```

---

## 使用示例

### 查找并调用策略

```java
// 1. 从注册表获取策略
DataTransformStrategy<PubMedArticle, CanonicalLiterature> strategy =
    strategyRegistry.getStrategy(PubMedArticle.class, CanonicalLiterature.class);

// 2. 转换单条数据
PubMedArticle article = pubMedClient.getArticle("12345678");
CanonicalLiterature literature = strategy.transform(article);

// 3. 批量转换
List<PubMedArticle> articles = pubMedClient.searchArticles(query);
TransformResult<CanonicalLiterature> result = strategy.batchTransform(articles);

// 4. 处理结果
if (result.isFullSuccess()) {
    log.info("全部转换成功: {} 条", result.getSuccessItems().size());
    return result.getSuccessItems();

} else if (result.isPartialSuccess()) {
    log.warn("部分转换失败: 成功 {} 条, 失败 {} 条",
        result.getSuccessItems().size(),
        result.getErrors().size());

    // 记录失败详情
    result.getErrors().forEach(error ->
        log.error("转换失败[索引 {}]: {}, 原始数据: {}",
            error.getIndex(),
            error.getErrorMessage(),
            error.getSourceDataSnapshot())
    );

    return result.getSuccessItems();  // 返回成功的数据

} else {
    log.error("全部转换失败: {} 条", result.getErrors().size());
    throw new TransformException("批量转换全部失败");
}
```

---

## 总结

本契约定义了可插拔的数据转换策略接口,确保:

1. ✅ **类型安全**: 使用泛型明确源类型和目标类型
2. ✅ **可插拔**: 新数据源只需实现策略接口并注册
3. ✅ **批量优化**: 支持批量转换,使用线程池并行处理
4. ✅ **部分成功**: 单条数据失败不影响其他数据
5. ✅ **错误详情**: TransformError 记录失败数据的索引、原始内容和错误原因

**下一步**: 参考 `quickstart.md` 了解如何接入新数据源。
