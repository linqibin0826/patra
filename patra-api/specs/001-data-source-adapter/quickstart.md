# 新数据源接入快速指南

**目标读者**: 数据团队开发人员
**预计接入时间**: 2-3 个工作日
**版本**: 1.0.0
**最后更新**: 2025-11-11

---

## 前置条件

### 技能要求
- ✅ 熟悉 Java 17+ 和 Spring Boot 3.x
- ✅ 理解六边形架构和 DDD 基本概念
- ✅ 熟悉 Maven 构建工具
- ✅ 了解目标数据源的 API 文档

### 环境准备
- ✅ JDK 25
- ✅ Maven 3.9+
- ✅ IDE (推荐 IntelliJ IDEA)
- ✅ 数据源 API Key (需要提前申请)

---

## 接入流程(5 步)

### 步骤 1: 在 patra-registry 注册数据源配置 (30 分钟)

**目标**: 在配置中心注册新数据源的基础配置

#### 1.1 登录 patra-registry 管理后台

```bash
# 开发环境
URL: http://registry-dev.patra.com
账号: 向运维团队申请
```

#### 1.2 创建 Provenance 配置

导航到: **配置管理 > Provenance 配置 > 新增**

填写以下字段:

| 字段 | 示例值 | 说明 |
|------|--------|------|
| **code** | `epmc` | 数据源代码(小写,唯一) |
| **name** | `Europe PMC` | 数据源显示名称 |
| **baseUrl** | `https://www.ebi.ac.uk/europepmc/webservices/rest` | API 基础URL |
| **apiKey** | `your-api-key` | API 密钥(如果需要) |
| **timeout** | `30000` | 超时时间(毫秒) |
| **rateLimit** | `100` | 速率限制(请求/秒) |
| **successRateThreshold** | `0.7` | 成功率阈值(0.7 = 70%) |
| **parallelThreadCount** | `10` | 并行转换线程数 |

#### 1.3 添加数据类型支持

在 Provenance 配置下添加支持的数据类型:

- ☑️ `LITERATURE` (文献)
- ☑️ `AUTHOR` (作者,可选)
- ☑️ `FULLTEXT` (全文,可选)

**完成标志**: 能够通过 API 查询到配置

```bash
curl http://registry-dev.patra.com/api/v1/provenance/epmc
```

---

### 步骤 2: 创建 Client 防腐层 (4 小时)

**目标**: 封装对外部 API 的 HTTP 调用,隔离外部 API 变化

**模块位置**: `patra-starter-provenance/src/main/java/com/patra/starter/provenance/{dataSource}/`

#### 2.1 创建原始数据模型

```java
package com.patra.starter.provenance.epmc.model;

import lombok.Data;
import java.util.List;

/**
 * EPMC 文章原始数据模型。
 */
@Data
public class EpmcArticle {
    private String id;
    private String title;
    private String abstractText;
    private List<EpmcAuthor> authorList;
    private String journalTitle;
    private String pubYear;
    private String doi;
    private String pmid;
    private String pmcid;

    @Data
    public static class EpmcAuthor {
        private String fullName;
        private String firstName;
        private String lastName;
        private List<String> affiliationList;
    }
}
```

#### 2.2 创建 Client 接口和实现

```java
package com.patra.starter.provenance.epmc.client;

import com.patra.starter.provenance.epmc.model.EpmcArticle;
import java.util.List;

/**
 * EPMC 数据源客户端接口。
 */
public interface EpmcClient {

    /**
     * 搜索文章。
     *
     * @param query 查询条件
     * @param pageSize 每页数量
     * @param cursorMark 游标标记
     * @return 文章列表
     */
    List<EpmcArticle> searchArticles(String query, int pageSize, String cursorMark);

    /**
     * 根据ID获取文章详情。
     *
     * @param id 文章ID
     * @return 文章详情
     */
    EpmcArticle getArticle(String id);
}
```

#### 2.3 实现 Client

```java
package com.patra.starter.provenance.epmc.client.impl;

import com.patra.starter.provenance.epmc.client.EpmcClient;
import com.patra.starter.provenance.epmc.model.EpmcArticle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * EPMC 客户端实现。
 */
@Component
@RequiredArgsConstructor
public class EpmcClientImpl implements EpmcClient {

    private final RestTemplate restTemplate;
    private final EpmcProperties properties;  // 从 patra-registry 注入

    @Override
    public List<EpmcArticle> searchArticles(String query, int pageSize, String cursorMark) {
        String url = properties.getBaseUrl() + "/search";

        // 构建请求参数
        Map<String, Object> params = Map.of(
            "query", query,
            "pageSize", pageSize,
            "cursorMark", cursorMark != null ? cursorMark : "*",
            "format", "json"
        );

        // 调用外部 API
        EpmcSearchResponse response = restTemplate.getForObject(
            buildUrl(url, params),
            EpmcSearchResponse.class
        );

        return response != null ? response.getResultList() : List.of();
    }

    @Override
    public EpmcArticle getArticle(String id) {
        String url = properties.getBaseUrl() + "/article/" + id;
        return restTemplate.getForObject(url, EpmcArticle.class);
    }

    private String buildUrl(String base, Map<String, Object> params) {
        // 构建URL查询字符串
    }
}
```

**测试验证**:

```java
@Test
void should_fetch_articles_successfully() {
    List<EpmcArticle> articles = epmcClient.searchArticles("COVID-19", 10, null);
    assertThat(articles).isNotEmpty();
}
```

---

### 步骤 3: 实现数据转换策略 (4 小时)

**目标**: 将外部数据模型转换为规范数据模型

**模块位置**: `patra-starter-provenance/src/main/java/com/patra/starter/provenance/{dataSource}/strategy/`

#### 3.1 创建转换策略类

```java
package com.patra.starter.provenance.epmc.strategy;

import com.patra.ingest.domain.model.canonical.CanonicalLiterature;
import com.patra.ingest.domain.model.valueobject.Provenance;
import com.patra.ingest.domain.strategy.DataTransformStrategy;
import com.patra.ingest.domain.strategy.TransformResult;
import com.patra.ingest.domain.strategy.TransformError;
import com.patra.starter.provenance.epmc.model.EpmcArticle;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * EPMC 文章到规范文献的转换策略。
 */
@Component
public class EpmcToLiteratureStrategy
        implements DataTransformStrategy<EpmcArticle, CanonicalLiterature> {

    @Override
    public Class<EpmcArticle> getSourceType() {
        return EpmcArticle.class;
    }

    @Override
    public Class<CanonicalLiterature> getTargetType() {
        return CanonicalLiterature.class;
    }

    @Override
    public CanonicalLiterature transform(EpmcArticle source) {
        return CanonicalLiterature.builder()
            .id("epmc:" + source.getId())
            .title(source.getTitle())
            .abstractText(source.getAbstractText())
            .authors(transformAuthors(source.getAuthorList()))
            .identifiers(Map.of(
                "PMID", source.getPmid() != null ? source.getPmid() : "",
                "DOI", source.getDoi() != null ? source.getDoi() : "",
                "PMC_ID", source.getPmcid() != null ? source.getPmcid() : ""
            ))
            .publicationDate(parsePublicationDate(source.getPubYear()))
            .provenance(Provenance.ofCode("epmc"))
            .createdAt(Instant.now())
            .build();
    }

    @Override
    public TransformResult<CanonicalLiterature> batchTransform(List<EpmcArticle> sources) {
        List<CanonicalLiterature> successItems = new CopyOnWriteArrayList<>();
        List<TransformError> errors = new CopyOnWriteArrayList<>();

        // 分批并行转换
        sources.parallelStream().forEach(article -> {
            try {
                CanonicalLiterature literature = transform(article);
                successItems.add(literature);
            } catch (Exception e) {
                errors.add(new TransformError(
                    sources.indexOf(article),
                    truncate(article.toString(), 1024),
                    e.getMessage(),
                    e
                ));
            }
        });

        return TransformResult.<CanonicalLiterature>builder()
            .successItems(successItems)
            .errors(errors)
            .build();
    }

    private List<CanonicalLiterature.Author> transformAuthors(List<EpmcArticle.EpmcAuthor> epmcAuthors) {
        if (epmcAuthors == null) {
            return List.of();
        }
        return epmcAuthors.stream()
            .map(author -> CanonicalLiterature.Author.builder()
                .firstName(author.getFirstName())
                .lastName(author.getLastName())
                .fullName(author.getFullName())
                .affiliations(author.getAffiliationList())
                .build())
            .collect(Collectors.toList());
    }

    private LocalDate parsePublicationDate(String pubYear) {
        if (pubYear == null || pubYear.isBlank()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(pubYear), 1, 1);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated)";
    }
}
```

**测试验证**:

```java
@Test
void should_transform_article_successfully() {
    // Given
    EpmcArticle article = createSampleArticle();

    // When
    CanonicalLiterature literature = strategy.transform(article);

    // Then
    assertThat(literature.getId()).isEqualTo("epmc:" + article.getId());
    assertThat(literature.getTitle()).isEqualTo(article.getTitle());
    assertThat(literature.getProvenance().getCode()).isEqualTo("epmc");
}

@Test
void should_handle_partial_success_in_batch_transform() {
    // Given
    List<EpmcArticle> articles = List.of(
        validArticle1,
        invalidArticle,  // 缺少必填字段
        validArticle2
    );

    // When
    TransformResult<CanonicalLiterature> result = strategy.batchTransform(articles);

    // Then
    assertThat(result.getSuccessItems()).hasSize(2);
    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getSuccessRate()).isEqualTo(2.0 / 3.0);
}
```

---

### 步骤 4: 实现数据源适配器 (4 小时)

**目标**: 组合 Client 和 Strategy,实现完整的适配器

**模块位置**: `patra-starter-provenance/src/main/java/com/patra/starter/provenance/{dataSource}/`

#### 4.1 创建适配器类

```java
package com.patra.starter.provenance.epmc;

import com.patra.ingest.domain.model.canonical.CanonicalLiterature;
import com.patra.ingest.domain.model.valueobject.DataType;
import com.patra.ingest.domain.model.valueobject.ErrorType;
import com.patra.starter.provenance.common.adapter.DataSourceAdapter;
import com.patra.starter.provenance.common.adapter.AdapterRequest;
import com.patra.starter.provenance.common.adapter.AdapterResult;
import com.patra.starter.provenance.common.adapter.AdapterCapabilities;
import com.patra.starter.provenance.epmc.client.EpmcClient;
import com.patra.starter.provenance.epmc.model.EpmcArticle;
import com.patra.ingest.domain.strategy.DataTransformStrategy;
import com.patra.ingest.domain.strategy.TransformResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * EPMC 数据源适配器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EpmcDataSourceAdapter implements DataSourceAdapter<CanonicalLiterature> {

    private final EpmcClient epmcClient;
    private final DataTransformStrategy<EpmcArticle, CanonicalLiterature> transformStrategy;

    @Override
    public String getProvenanceCode() {
        return "epmc";
    }

    @Override
    public AdapterCapabilities getCapabilities() {
        return AdapterCapabilities.builder()
            .provenanceCode("epmc")
            .provenanceName("Europe PMC")
            .supportedDataTypes(Set.of(
                DataType.LITERATURE,
                DataType.FULLTEXT
            ))
            .supportsPagination(true)
            .supportsCursorPagination(true)
            .maxBatchSize(1000)
            .build();
    }

    @Override
    public AdapterResult<CanonicalLiterature> fetchData(AdapterRequest request) {
        try {
            // 1. 提取查询参数
            String query = request.getStringParam("query");
            Integer pageSize = request.getIntParam("pageSize");
            String cursorMark = request.getStringParam("cursorToken");

            if (query == null || query.isBlank()) {
                return AdapterResult.nonRetriableFailure("查询条件不能为空");
            }

            // 2. 调用 Client 获取原始数据
            List<EpmcArticle> articles = epmcClient.searchArticles(
                query,
                pageSize != null ? pageSize : 100,
                cursorMark
            );

            // 3. 调用 Strategy 转换数据
            TransformResult<CanonicalLiterature> transformResult =
                transformStrategy.batchTransform(articles);

            // 4. 检查成功率
            double threshold = request.getConfig()
                .getBatchingConfig()
                .getSuccessRateThreshold();

            if (transformResult.getSuccessRate() < threshold) {
                return AdapterResult.partialSuccess(
                    transformResult.getSuccessItems(),
                    String.format("转换成功率 %.2f%% 低于阈值 %.2f%%",
                        transformResult.getSuccessRate() * 100,
                        threshold * 100)
                );
            }

            // 5. 返回成功结果
            return AdapterResult.success(
                transformResult.getSuccessItems(),
                extractNextCursor(articles)  // 提取下一页游标
            );

        } catch (HttpClientErrorException.TooManyRequests e) {
            // HTTP 429 限流
            long retryAfterSeconds = parseRetryAfter(e.getResponseHeaders());
            return AdapterResult.<CanonicalLiterature>retriableFailure(
                "API限流,建议" + retryAfterSeconds + "秒后重试"
            ).withMetadata("retryAfterSeconds", retryAfterSeconds);

        } catch (SocketTimeoutException | TimeoutException e) {
            // 网络超时
            return AdapterResult.retriableFailure("网络超时: " + e.getMessage());

        } catch (HttpServerErrorException e) {
            // 服务器错误
            return AdapterResult.retriableFailure(
                "服务器错误(" + e.getStatusCode() + "): " + e.getMessage()
            );

        } catch (HttpClientErrorException e) {
            // 客户端错误(不可重试)
            return AdapterResult.nonRetriableFailure(
                "客户端错误(" + e.getStatusCode() + "): " + e.getMessage()
            );

        } catch (IllegalArgumentException e) {
            // 参数错误(不可重试)
            return AdapterResult.nonRetriableFailure("参数错误: " + e.getMessage());

        } catch (Exception e) {
            // 未知错误(默认不可重试,保守策略)
            log.error("适配器调用异常", e);
            return AdapterResult.nonRetriableFailure("未知错误: " + e.getMessage());
        }
    }

    private String extractNextCursor(List<EpmcArticle> articles) {
        // 从响应中提取下一页游标令牌
        // 具体实现依赖 EPMC API 的分页机制
        return null;
    }

    private long parseRetryAfter(HttpHeaders headers) {
        String retryAfter = headers.getFirst("Retry-After");
        if (retryAfter == null) {
            return 60L;  // 默认60秒
        }
        try {
            return Long.parseLong(retryAfter);
        } catch (NumberFormatException e) {
            return 60L;
        }
    }
}
```

#### 4.2 注册适配器

适配器会通过 Spring 自动装配注册到 `AdapterRegistry`,无需手动注册。

**验证注册成功**:

```java
@Test
void should_register_adapter_automatically() {
    // 验证适配器已注册
    assertThat(adapterRegistry.hasAdapter("epmc", DataType.LITERATURE)).isTrue();

    // 获取适配器
    DataSourceAdapter<CanonicalLiterature> adapter =
        adapterRegistry.getAdapter("epmc", CanonicalLiterature.class);

    assertThat(adapter).isNotNull();
    assertThat(adapter.getProvenanceCode()).isEqualTo("epmc");
}
```

---

### 步骤 5: 集成测试 (2 小时)

**目标**: 验证适配器能够正确获取和转换数据

#### 5.1 创建集成测试

```java
package com.patra.starter.provenance.epmc;

import com.patra.ingest.domain.model.canonical.CanonicalLiterature;
import com.patra.ingest.domain.model.valueobject.DataType;
import com.patra.starter.provenance.common.adapter.AdapterRequest;
import com.patra.starter.provenance.common.adapter.AdapterResult;
import com.patra.starter.provenance.common.adapter.DataSourceAdapter;
import com.patra.starter.provenance.common.registry.AdapterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EPMC 适配器集成测试。
 */
@SpringBootTest
class EpmcDataSourceAdapterIT {

    @Autowired
    private AdapterRegistry adapterRegistry;

    @Test
    void should_fetch_and_transform_literature_successfully() {
        // Given
        DataSourceAdapter<CanonicalLiterature> adapter =
            adapterRegistry.getAdapter("epmc", CanonicalLiterature.class);

        AdapterRequest request = AdapterRequest.builder()
            .operationCode("INGEST_LITERATURE")
            .requestedDataType(DataType.LITERATURE)
            .executionParams(Map.of(
                "query", "COVID-19",
                "pageSize", 10
            ))
            .build();

        // When
        AdapterResult<CanonicalLiterature> result = adapter.fetchData(request);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotEmpty();
        assertThat(result.getData()).allMatch(lit ->
            lit.getId().startsWith("epmc:")
        );
        assertThat(result.getData()).allMatch(lit ->
            lit.getProvenance().getCode().equals("epmc")
        );
    }

    @Test
    void should_handle_invalid_query_gracefully() {
        // Given
        DataSourceAdapter<CanonicalLiterature> adapter =
            adapterRegistry.getAdapter("epmc", CanonicalLiterature.class);

        AdapterRequest request = AdapterRequest.builder()
            .requestedDataType(DataType.LITERATURE)
            .executionParams(Map.of())  // 缺少 query 参数
            .build();

        // When
        AdapterResult<CanonicalLiterature> result = adapter.fetchData(request);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorType()).isEqualTo(ErrorType.NON_RETRIABLE);
        assertThat(result.getErrorMessage()).contains("查询条件不能为空");
    }
}
```

#### 5.2 运行集成测试

```bash
# 运行所有集成测试
mvn test -Dtest=*IT

# 运行特定测试
mvn test -Dtest=EpmcDataSourceAdapterIT
```

**预期结果**:
- ✅ 所有测试通过
- ✅ 能够成功获取和转换数据
- ✅ 错误处理正确(可重试/不可重试错误分类准确)

---

## 常见问题

### Q1: 如何处理数据源 API 返回格式变化?

**答**: 修改 Client 层的解析逻辑,不影响核心业务:

```java
// 只需修改 EpmcClient 的响应解析
@Override
public List<EpmcArticle> searchArticles(...) {
    EpmcSearchResponse response = restTemplate.getForObject(...);

    // 🔧 更新解析逻辑以适应新格式
    return parseResponse(response);  // 隔离变化
}
```

### Q2: 如何支持新的数据类型(如作者)?

**答**: 实现新的转换策略并注册:

```java
@Component
public class EpmcToAuthorStrategy
        implements DataTransformStrategy<EpmcAuthor, CanonicalAuthor> {
    // 实现转换逻辑
}
```

Spring 会自动注册到 `StrategyRegistry`,适配器自动支持。

### Q3: 如何调试适配器?

**答**: 使用日志和断点:

```java
// 开启DEBUG日志
logging:
  level:
    com.patra.starter.provenance.epmc: DEBUG

// 在关键位置打日志
log.debug("调用EPMC API: query={}, pageSize={}", query, pageSize);
log.debug("获取到 {} 条原始数据", articles.size());
log.debug("转换成功率: {}", transformResult.getSuccessRate());
```

### Q4: 如何处理大批量数据?

**答**: 使用分页和游标:

```java
String cursorMark = null;
int totalFetched = 0;

while (totalFetched < targetCount) {
    AdapterRequest request = AdapterRequest.builder()
        .executionParams(Map.of(
            "query", query,
            "pageSize", 1000,
            "cursorToken", cursorMark
        ))
        .build();

    AdapterResult<CanonicalLiterature> result = adapter.fetchData(request);
    totalFetched += result.getData().size();
    cursorMark = result.getNextCursorToken();

    if (cursorMark == null) {
        break;  // 无更多数据
    }
}
```

---

## 验收检查清单

### 功能验收
- [ ] 能够从 patra-registry 获取配置
- [ ] Client 能够成功调用外部 API
- [ ] 转换策略能够正确转换数据
- [ ] 适配器能够处理成功和失败场景
- [ ] 错误分类准确(RETRIABLE/NON_RETRIABLE)

### 性能验收
- [ ] 单次查询响应时间 < 3秒 (P95)
- [ ] 批量转换100条数据时间 < 5秒
- [ ] 成功率 ≥ 配置的阈值(默认70%)

### 质量验收
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 集成测试通过
- [ ] 代码审查通过
- [ ] 文档完整(README + JavaDoc)

---

## 参考资源

### 内部文档
- [数据模型设计](./data-model.md)
- [DataSourceAdapter 接口契约](./contracts/DataSourceAdapter.md)
- [DataTransformStrategy 接口契约](./contracts/DataTransformStrategy.md)
- [现有 PubMed 适配器实现](../../patra-starter-provenance/src/main/java/com/patra/starter/provenance/pubmed/)

### 外部资源
- [Europe PMC API 文档](https://europepmc.org/RestfulWebService)
- [Spring RestTemplate 文档](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [Lombok 注解指南](https://projectlombok.org/features/)

---

## 支持渠道

- **技术支持**: 在项目 Slack #patra-dev 频道提问
- **代码审查**: 提交 PR 后 @架构师团队 审查
- **问题反馈**: 在 Jira 创建 Bug/Task

祝接入顺利! 🚀
