# patra-spring-boot-starter-provenance

## 概述

用于 PubMed 和 Europe PMC 数据源的 HTTP 客户端集成 Starter，提供直连模式的数据采集能力，无需独立的出站网关服务。

本 Starter 自动配置医学出版物 API 客户端，支持 ESearch、EFetch、EPost 等常用操作，内置请求组装、超时控制、简单重试和本地限流机制。

**HTTP 客户端**: 基于 **Spring RestClient**（使用底层 JDK 21 HttpClient），提供类型安全的 HTTP 调用和自动配置支持。

### 🩺 医学出版物数据源专用

本模块专为医学出版物数据源（PubMed/MEDLINE）设计，核心组件包括：

- **CanonicalPublication** 规范化模型：采用医学领域标准术语（MeSH、Investigator、Substance 等）
- **PubmedPublicationConverter**：精确映射 PubMed XML 响应到规范化医学出版物模型
- **MeSH 术语支持**：完整支持 MeSH 主题标引、限定词、补充概念
- **医学领域特性**：研究者信息、临床试验数据、外部数据库引用、参考文献等

## 核心功能

- **PubMed 客户端**: 支持 ESearch、EFetch、EPost 操作
- **Europe PMC 客户端**: 支持文献搜索
- **医学出版物转换器**: 将 PubMed 响应转换为规范化医学出版物模型（CanonicalPublication）
- **请求组装器**: 标准化参数构建，避免硬编码
- **配置合并**: 支持全局默认值和数据源级覆盖
- **指标集成**: 可选的 Micrometer 指标采集
- **提供者注册**: 统一的数据源提供者发现机制

## 自动配置内容

### 自动配置类

`ProvenanceAutoConfiguration` 自动配置以下 Bean:

| Bean 名称 | 类型 | 描述 |
|-----------|------|------|
| `pubMedClient` | `PubMedClient` | PubMed E-utilities 客户端（使用 defaultRestClient） |
| `epmcClient` | `EPMCClient` | Europe PMC 搜索客户端（使用 defaultRestClient） |
| `provenanceXmlMapper` | `XmlMapper` | PubMed XML 响应映射器 |
| `pubmedArticleConverter` | `PubmedPublicationConverter` | **PubMed 医学出版物转换器（核心组件）** |
| `defaultConfigProvider` | `DefaultConfigProvider` | 配置提供器 |
| `providerRegistry` | `ProviderRegistry` | 数据源提供者注册表 |
| `pubmedDataProvider` | `PubmedDataProvider` | PubMed 数据提供者实现 |
| `provenanceMetrics` | `ProvenanceMetrics` | 指标记录器（需要 MeterRegistry） |

### 启用条件

- 配置属性 `patra.provenance.enabled=true`（默认启用）
- 指标 Bean 需要 `MeterRegistry` 存在

## 主要组件

### RestClient（统一管理）

使用 `patra-spring-boot-starter-rest-client` 提供的 `defaultRestClient`，实现 HTTP 客户端统一管理：

- **统一配置**: 超时、重试、日志等配置由 rest-client Starter 统一管理
- **底层实现**: JDK 21 HttpClient（通过 JdkClientHttpRequestFactory）
- **BaseUrl 动态构建**: 每次请求时从 `ProvenanceConfig.baseUrl()` 动态构建完整 URL
- **避免 LoadBalancer 污染**: 使用非 Bean 方式创建 Factory，避免被 Spring Cloud LoadBalancer 包装

### PubMedClient

提供 PubMed E-utilities API 访问能力:

- `esearch()`: 搜索文献 ID
- `efetch()`: 获取文献详情
- `epost()`: 上传 ID 列表到 History Server（推荐用于 >200 个 ID）

### EPMCClient

提供 Europe PMC API 访问能力:

- `search()`: 文献搜索

### 🔬 PubmedPublicationConverter（医学出版物转换器）

将 PubMed XML 响应转换为规范化医学出版物模型（CanonicalPublication），支持完整的医学领域字段映射。

#### 核心转换方法（v0.1.0 重大更新）

##### MeSH 相关转换（医学领域核心）

| 方法 | 描述 | 映射字段 |
|------|------|----------|
| `convertMeshHeadings()` | 转换 MeSH 主题标引 | `meshHeadings` |
| `convertSupplMeshNames()` | 转换补充 MeSH 概念 | `supplMeshNames` |

##### P0 核心字段转换（新增/增强）

| 方法 | 描述 | 映射字段 | v0.1.0 变更 |
|------|------|----------|------------|
| `convertPagination()` | 转换页码信息 | `pagination` | ✅ **重构为结构化对象**（startPage, endPage, medlinePgn） |
| `convertAuthors()` | 转换作者信息 | `authors` | ✅ **新增 valid 字段** |
| `convertFunding()` | 转换资助信息 | `funding` | ✅ **重命名 funderIdentifier → funderAcronym** |
| `extractPublicationDates()` | 提取出版日期 | `dates` | ✅ **新增 completed 日期** |
| `convertReferences()` | 转换参考文献列表 | `references` | ✅ **新增** |
| `convertReferences()` | 转换参考文献数量 | `numberOfReferences` | ✅ **新增** |

##### P1 医学领域字段转换（全新）

| 方法 | 描述 | 映射字段 | 用途场景 |
|------|------|----------|----------|
| `convertInvestigators()` | 转换研究者信息 | `investigators` | 临床试验、多中心研究 |
| `convertPersonalNameSubjects()` | 转换人物主题 | `personalNameSubjects` | 传记、医学史、案例报告 |
| `convertExternalReferences()` | 转换外部数据库引用 | `externalReferences` | GenBank、ClinicalTrials.gov 等 |
| `convertSupplementalObjects()` | 转换补充对象 | `supplementalObjects` | 图表、数据集、多媒体 |
| `convertRelatedItems()` | 转换相关项目 | `relatedItems` | 更正、撤稿、评论、转载 |

##### 其他转换方法

| 方法 | 描述 | 映射字段 |
|------|------|----------|
| `buildIdentifiers()` | 构建标识符列表 | `identifiers` |
| `extractAbstract()` | 提取摘要信息 | `abstractContent` |
| `convertAlternativeAbstracts()` | 转换其他语言摘要 | `alternativeAbstracts` |
| `convertJournal()` | 转换期刊信息 | `journal` |
| `convertSubstances()` | 转换化学物质列表 | `substances` |
| `convertGenes()` | 转换基因符号列表 | `genes` |
| `extractKeywords()` | 提取关键词集合 | `keywords` |
| `extractPublicationHistory()` | 提取发布历史时间线 | `publicationHistory` |
| `extractMetadata()` | 提取文献元数据 | `metadata` |

#### 使用示例

```java
@Component
@RequiredArgsConstructor
public class PubmedDataAdapter {

    private final PubmedPublicationConverter converter;

    public CanonicalPublication fetchPublication(String pmid) {
        // 1. 从 PubMed 获取原始 XML 响应
        PubmedPublication rawArticle = pubMedClient.efetch(...);

        // 2. 转换为规范化医学出版物模型
        CanonicalPublication publication = converter.toCanonicalPublication(rawArticle);

        // 3. 访问医学领域特定字段
        List<MeshHeading> meshHeadings = publication.getMeshHeadings();
        List<Investigator> investigators = publication.getInvestigators();
        List<Reference> references = publication.getReferences();

        return publication;
    }
}
```

### 请求组装器

- `PubMedESearchRequestAssembler`: 组装 ESearch 请求
- `EpmcSearchRequestAssembler`: 组装 EPMC 搜索请求

**API 常量来源**: 所有参数键、端点路径和参数值枚举统一维护在 **`patra-common-provenance-api`** 模块：
- 参数键常量: `PubMedParamKeys`、`EpmcParamKeys`、`CrossrefParamKeys`
- 端点路径常量: `CrossrefEndpoints`
- 参数值枚举: `RetMode`、`RetType`、`UseHistory`、`DateType`、`Format`、`ResultType` 等
- 操作枚举: `PubMedOperation`、`EpmcOperation`（封装操作名称+端点+描述）

参考 [patra-common-provenance-api/README.md](../patra-common/patra-common-provenance-api/README.md) 了解完整的 API 常量使用指南。

### ProviderRegistry

统一的数据源提供者注册表，支持:
- 自动发现所有 `ProvenanceDataProvider` 实现
- 按数据源代码查找提供者
- 为 Ingest 服务提供统一的数据源访问接口

## 配置属性

配置前缀: `patra.provenance`

### 全局配置

```yaml
patra:
  provenance:
    enabled: true  # 启用自动配置（默认 true）
    defaults:
      http:
        timeout-connect-millis: 10000  # 连接超时（默认 10s）
        timeout-read-millis: 30000     # 读取超时（默认 30s）
      pagination:
        page-size-value: 100           # 分页大小（默认 100）
      batching:
        epost-threshold: 200           # EPost 阈值（默认 200）
      retry:
        max-retry-times: 3             # 最大重试次数（默认 3）
        initial-delay-millis: 1000     # 初始重试延迟（默认 1s）
      rate-limit:
        max-concurrent-requests: 10    # 最大并发请求（默认 10）
        per-credential-qps-limit: 5    # 每凭证 QPS 限制（默认 5）
```

### 数据源级覆盖

```yaml
patra:
  provenance:
    sources:
      pubmed:
        base-url: "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"
        http:
          timeout-read-millis: 60000  # 覆盖 PubMed 读取超时
      epmc:
        base-url: "https://www.ebi.ac.uk/europepmc/webservices/rest"
```

## 使用方式

### Maven 依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-provenance</artifactId>
</dependency>
```

注意：
- `spring-web` 已作为核心依赖自动引入，提供 RestClient 支持
- `patra-common-provenance-api` 已自动引入，提供 API 常量和枚举
- `patra-common-model` 已自动引入，提供 CanonicalPublication 模型

### 配置示例

```yaml
patra:
  provenance:
    enabled: true
    defaults:
      http:
        timeout-read-millis: 45000
      batching:
        epost-threshold: 300
```

### 代码示例

#### 使用 ProvenanceDataProvider（推荐）

```java
@Component
@RequiredArgsConstructor
public class ProvenanceDataAdapterImpl implements ProvenanceDataPort {

    private final ProviderRegistry providerRegistry;

    @Override
    public QuerySession prepareQuerySession(ExecutionContext context, DataType dataType) {
        // 1. 从注册表获取提供者
        ProvenanceDataProvider provider = providerRegistry.getProvider(
            context.provenanceCode(),
            dataType
        );

        // 2. 构建提供者请求
        ProviderRequest request = buildProviderRequest(context);

        // 3. 调用提供者准备查询会话
        return provider.prepareQuerySession(request);
    }

    @Override
    public <T> DataFetchResult<T> fetchData(
        ExecutionContext context,
        DataType dataType,
        TypeReference<T> typeRef,
        Batch batch
    ) {
        ProvenanceDataProvider provider = providerRegistry.getProvider(
            context.provenanceCode(),
            dataType
        );

        ProviderRequest request = buildProviderRequest(context, batch);
        return provider.fetchData(request, dataType, typeRef);
    }
}
```

#### 直接使用 PubMedClient（低级 API）

```java
@Component
@RequiredArgsConstructor
public class PubmedSearchService {

    private final PubMedClient pubMedClient;
    private static final PubMedESearchRequestAssembler ASSEMBLER = new PubMedESearchRequestAssembler();

    public PlanMetadata preparePlanMetadata(String query, JsonNode params) {
        // 从已渲染的参数构建请求
        ESearchRequest request = ASSEMBLER.buildList(params);

        // 调用 PubMed API
        ESearchResponse response = pubMedClient.esearch(request);

        if (response == null || response.result() == null) {
            return PlanMetadata.empty();
        }

        var result = response.result();
        return new PlanMetadata(result.count(), result.webEnv(), result.queryKey());
    }
}
```

#### 使用 PubmedPublicationConverter（医学出版物转换）

```java
@Component
@RequiredArgsConstructor
public class PubmedDataProvider implements ProvenanceDataProvider {

    private final PubMedClient pubMedClient;
    private final PubmedPublicationConverter converter;

    @Override
    public ProviderResult<CanonicalPublication> fetchData(
        ProviderRequest request,
        DataType dataType,
        TypeReference<CanonicalPublication> typeRef
    ) {
        // 1. 调用 PubMed API 获取原始数据
        EFetchResponse response = pubMedClient.efetch(...);

        // 2. 转换为规范化医学出版物模型
        List<CanonicalPublication> publications = response.articles().stream()
            .map(converter::toCanonicalPublication)
            .collect(Collectors.toList());

        // 3. 访问医学领域特定字段
        for (CanonicalPublication lit : publications) {
            // MeSH 主题标引（医学索引核心）
            List<MeshHeading> meshHeadings = lit.getMeshHeadings();

            // 补充 MeSH 概念（疾病、药物试验等）
            List<SupplMeshName> supplMeshNames = lit.getSupplMeshNames();

            // 研究者信息（临床试验常见）
            List<Investigator> investigators = lit.getInvestigators();

            // 人物主题（传记、医学史）
            List<PersonalNameSubject> personalNameSubjects = lit.getPersonalNameSubjects();

            // 外部数据库引用（GenBank、ClinicalTrials.gov）
            List<ExternalReference> externalRefs = lit.getExternalReferences();

            // 补充对象（图表、数据集）
            List<SupplementalObject> supplements = lit.getSupplementalObjects();

            // 参考文献（完整列表和数量）
            List<Reference> references = lit.getReferences();
            Integer refCount = lit.getNumberOfReferences();

            // 相关项目（更正、撤稿、评论）
            List<RelatedItem> relatedItems = lit.getRelatedItems();

            // 结构化页码信息
            Pagination pagination = lit.getPagination();
            String startPage = pagination.getStartPage();
            String endPage = pagination.getEndPage();
            String medlinePgn = pagination.getMedlinePgn();
        }

        return ProviderResult.success(publications, dataType, null);
    }
}
```

#### 使用 API 常量和枚举（推荐）

```java
import com.patra.common.provenance.api.params.PubMedParamKeys;
import com.patra.common.provenance.api.values.pubmed.*;
import com.patra.common.provenance.api.constants.PubMedOperation;

// 构建请求参数（类型安全）
Map<String, String> params = new HashMap<>();
params.put(PubMedParamKeys.TERM, "cancer");
params.put(PubMedParamKeys.RETMODE, RetMode.JSON.value());  // 使用枚举
params.put(PubMedParamKeys.RETTYPE, RetType.UILIST.value());
params.put(PubMedParamKeys.DATETYPE, DateType.PUBLICATION_DATE.value());
params.put(PubMedParamKeys.USEHISTORY, UseHistory.YES.value());

// 使用操作枚举（推荐，包含操作名称+端点+描述）
PubMedOperation op = PubMedOperation.ESEARCH;
String endpoint = op.getEndpoint();  // "/esearch.fcgi"
String operationName = op.getOperationName();  // "esearch"

// 类型安全的枚举比较
if (request.retmode() == RetMode.XML) {
    // 处理 XML 格式
}
```

#### 错误处理

所有客户端在失败时抛出 `ProvenanceClientException`:

```java
try {
    ESearchResponse response = pubMedClient.esearch(request);
} catch (ProvenanceClientException ex) {
    log.error("PubMed API 调用失败: {}", ex.getMessage(), ex);
    // 处理错误: 重试、降级或传播
}
```

## 医学出版物数据映射说明

### CanonicalPublication 模型设计原则

CanonicalPublication 是 Patra 平台的规范化医学出版物模型，基于以下国际标准设计：

- **PubMed/MEDLINE** - 医学出版物元数据标准（主要数据源）
- **MeSH** - 美国国家医学图书馆医学主题词表
- **Dublin Core** - 核心元数据标准（title, creator, identifier 等）
- **Schema.org** - ScholarlyArticle 规范（author, abstract, keywords 等）

### 医学领域特性支持

#### MeSH 术语支持

MeSH（Medical Subject Headings）是美国国家医学图书馆（NLM）创建的受控词表，用于标引医学出版物的主题和内容。

**主要字段**：
- `meshHeadings`: MeSH 主题标引列表，包含主题词（Descriptor）和限定词（Qualifiers）
- `supplMeshNames`: 补充 MeSH 概念列表，用于描述疾病、药物试验、化学物质等特定主题

**使用示例**：
```java
// MeSH 主题标引
List<MeshHeading> meshHeadings = publication.getMeshHeadings();
for (MeshHeading heading : meshHeadings) {
    DescriptorName descriptor = heading.getDescriptorName();
    System.out.println("主题词: " + descriptor.getTerm());
    System.out.println("是否主要主题: " + descriptor.getMajorTopic());

    // 限定词（进一步细化主题）
    List<QualifierName> qualifiers = heading.getQualifierNames();
    for (QualifierName qualifier : qualifiers) {
        System.out.println("  限定词: " + qualifier.getTerm());
    }
}

// 补充 MeSH 概念
List<SupplMeshName> supplMeshNames = publication.getSupplMeshNames();
for (SupplMeshName supplMesh : supplMeshNames) {
    System.out.println("补充概念: " + supplMesh.getName());
    System.out.println("概念类型: " + supplMesh.getType());  // Protocol, Disease 等
}
```

#### 研究者信息（Investigators）

研究者是参与研究但未列为文章作者的研究人员，常见于大型临床试验、多中心研究等。

**字段结构**：
```java
List<Investigator> investigators = publication.getInvestigators();
for (Investigator investigator : investigators) {
    String name = investigator.getLastName() + ", " + investigator.getForeName();
    List<Affiliation> affiliations = investigator.getAffiliations();
    Boolean valid = investigator.getValid();  // 信息有效性
}
```

#### 人物主题（PersonalNameSubjects）

用于传记、医学史、案例报告等以特定人物为主题的文献。

**使用场景**：
- 医学史研究（如弗莱明与青霉素的发现）
- 传记文献
- 案例报告中的患者（匿名化）

#### 外部数据库引用（ExternalReferences）

关联到外部数据库的引用，如基因库、临床试验、软件仓库等。

**常见数据库**：
- **GenBank**: 基因序列数据库
- **ClinicalTrials.gov**: 临床试验注册库
- **PDB**: 蛋白质数据库
- **GEO**: 基因表达数据库

**使用示例**：
```java
List<ExternalReference> externalRefs = publication.getExternalReferences();
for (ExternalReference ref : externalRefs) {
    String type = ref.getType();  // database, clinical-trial, software, dataset
    String name = ref.getName();  // GenBank, ClinicalTrials.gov 等
    List<String> identifiers = ref.getIdentifiers();  // 登记号列表
}
```

#### 补充对象（SupplementalObjects）

文献的附加材料，如图表、数据集、关键词列表、多媒体文件等。

**对象类型**：
- `keyword`: 关键词列表
- `figure`: 图表
- `dataset`: 数据集
- `video`: 视频
- 其他多媒体内容

#### 参考文献（References）

完整的参考文献列表及数量统计。

**使用示例**：
```java
Integer totalReferences = publication.getNumberOfReferences();
List<Reference> references = publication.getReferences();
for (Reference ref : references) {
    String citation = ref.getCitation();  // 格式化的引用字符串
    List<Identifier> ids = ref.getIdentifiers();  // PMID, DOI 等
}
```

#### 相关项目（RelatedItems）

文献的相关项目，如更正、撤稿、评论、转载等。

**关系类型**：
- `retraction-of`: 撤稿
- `erratum-in`: 勘误
- `comment-on`: 评论
- `republished-from`: 转载
- `correction-to`: 更正

**使用场景**：
- 识别已撤稿文献
- 跟踪文献更正记录
- 发现相关评论和讨论

### P0/P1 字段优先级

**P0 核心字段**（必须支持）：
- 标识符（pmid, doi, pmc）
- 标题和摘要
- 作者和机构
- 期刊信息
- 出版日期
- **MeSH 主题标引**（医学索引核心）
- **页码信息**（结构化对象）
- **参考文献数量和列表**

**P1 医学领域字段**（增强功能）：
- **补充 MeSH 概念**
- **研究者信息**
- **人物主题**
- **外部数据库引用**
- **补充对象**
- **相关项目**

## 架构集成

### 六边形架构中的位置

本 Starter 位于**框架层（Framework Layer）**，为基础设施层提供技术支撑：

```
Domain Layer (patra-ingest-domain)
  - ProvenanceDataPort (业务端口接口)
    ↑ implements
Infrastructure Layer (patra-ingest-infra)
  - ProvenanceDataAdapter (桥接适配器)
    ↓ uses
Framework Layer (patra-starter-provenance) ← 本 Starter
  - ProvenanceDataProvider (技术提供者接口)
  - ProviderRegistry (提供者注册表)
  - RestClient (Spring 管理的 HTTP 客户端)
  - PubmedPublicationConverter (医学出版物转换器)
    ↑ implements
Provider Implementations (各数据源实现层)
  - PubmedProvenanceDataProvider (具体实现)
  - EpmcProvenanceDataProvider (具体实现)
```

### 命名语义说明

- **ProvenanceDataProvider**（本 Starter）：框架层的技术提供者接口，定义"如何提供数据获取能力"
- **ProvenanceDataAdapter**（Infrastructure 层）：桥接适配器，连接领域端口和框架提供者
- **ProvenanceDataPort**（Domain 层）：业务端口接口，定义"需要什么数据获取能力"

### 使用场景

Infrastructure 层的 `ProvenanceDataAdapter` 使用本 Starter：

```java
@Component
@RequiredArgsConstructor
public class ProvenanceDataAdapter implements ProvenanceDataPort {
    private final ProviderRegistry providerRegistry; // 来自本 Starter

    @Override
    public <T> DataFetchResult<T> fetchData(
        ExecutionContext context,
        DataType dataType,
        TypeReference<T> typeRef,
        Batch batch
    ) {
        // 1. 从注册表获取提供者
        ProvenanceDataProvider provider = providerRegistry.getProvider(
            context.provenanceCode(),
            dataType
        );

        // 2. 构建提供者请求
        ProviderRequest request = buildProviderRequest(context, batch);

        // 3. 调用提供者（类型安全）
        ProviderResult<T> result = provider.fetchData(request, dataType, typeRef);

        // 4. 转换为领域结果
        return convertToDataFetchResult(result);
    }
}
```

## 扩展点

### 自定义配置提供器

如需完全自定义配置逻辑，可禁用自动配置并手动创建 Bean:

```yaml
patra:
  provenance:
    enabled: false
```

```java
@Configuration
public class CustomProvenanceConfig {

    @Bean
    public PubMedClient customPubMedClient(
        RestClient defaultRestClient,  // 使用统一的 defaultRestClient
        DefaultConfigProvider configProvider,
        ObjectMapper objectMapper,
        XmlMapper xmlMapper
    ) {
        // 自定义配置提供者（覆盖 baseUrl 等）
        return new PubMedClientAdapter(
            defaultRestClient,
            configProvider,
            objectMapper,
            xmlMapper,
            null
        );
    }

    private XmlMapper xmlMapper() {
        return XmlMapper.builder()
            .findAndAddModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
    }
}
```

### 实现自定义数据源提供者

```java
@Component
public class CustomProvenanceDataProvider implements ProvenanceDataProvider {

    @Override
    public ProvenanceCode getProvenanceCode() {
        return ProvenanceCode.of("custom-source");
    }

    @Override
    public Set<DataType> getSupportedDataTypes() {
        return Set.of(DataType.PUBLICATION);
    }

    @Override
    public <T> ProviderResult<T> fetchData(
        ProviderRequest request,
        DataType dataType,
        TypeReference<T> typeRef
    ) {
        // 实现数据获取逻辑
        // ...
        return ProviderResult.success(data, dataType, nextCursor);
    }

    @Override
    public QuerySession prepareQuerySession(ProviderRequest request) {
        // 实现查询会话准备逻辑
        // ...
        return new QuerySession(totalCount, sessionToken);
    }
}
```

提供者会自动注册到 `ProviderRegistry`。

## 技术栈

- Spring Boot 4.0.1
- **Spring Web** (RestClient)
- Jackson (JSON/XML)
- Micrometer（可选）
- Hutool
- patra-common-core
- **patra-common-model**（CanonicalPublication 模型）
- **patra-common-provenance-api**（API 常量和枚举）

## 重大变更记录

### v0.1.0: CanonicalPublication 医学领域化重构

**背景**: 将 CanonicalPublication 模型从通用学术出版物模型重构为医学领域专用模型，并相应更新 PubmedPublicationConverter。

#### 模型变更

**移除的通用抽象**：
- `List<Subject> subjects` → 替换为 MeSH 特定字段（`meshHeadings`, `supplMeshNames`）
- `Subject` 和 `SubjectQualifier` 类 → 删除

**新增的 MeSH 特定字段**：
- `List<MeshHeading> meshHeadings` - MeSH 主题标引列表
- `List<SupplMeshName> supplMeshNames` - 补充 MeSH 概念

**新增的 P0 核心字段**：
- `Integer numberOfReferences` - 参考文献数量
- `List<Reference> references` - 参考文献列表
- `PublicationDates.completed` - 文献完成日期
- `FundingInfo.funderAcronym` - 资助机构缩写
- `Author.valid` - 作者信息有效性标识
- `Pagination` 对象（从 String 重构为结构化对象）
  - `startPage` - 起始页码
  - `endPage` - 结束页码
  - `medlinePgn` - MEDLINE 格式页码

**新增的 P1 医学领域字段**：
- `List<Investigator> investigators` - 研究者列表
- `List<PersonalNameSubject> personalNameSubjects` - 作为主题的人物
- `List<SupplementalObject> supplementalObjects` - 补充对象

#### 转换器变更

**重构的方法**：
- `convertSubjects()` → `convertMeshHeadings()` - 使用 MeSH 特定类型
- `convertPagination()` - 返回结构化对象而非 String
- `convertAuthors()` - 添加 valid 字段映射
- `convertFunding()` - funderIdentifier → funderAcronym

**新增的转换方法**（7 个）：
1. `convertSupplMeshNames()` - 补充 MeSH 概念
2. `convertInvestigators()` - 研究者信息
3. `convertPersonalNameSubjects()` - 人物主题
4. `convertExternalReferences()` - 外部数据库引用
5. `convertSupplementalObjects()` - 补充材料
6. `convertReferences()` - 参考文献列表
7. `convertRelatedItems()` - 相关条目

**使用示例**：

```java
// 1. 使用 MeSH 特定字段
List<MeshHeading> meshHeadings = publication.getMeshHeadings();
List<SupplMeshName> supplMeshNames = publication.getSupplMeshNames();

// 2. 使用结构化页码对象
Pagination pagination = publication.getPagination();
if (pagination != null) {
    String startPage = pagination.getStartPage();
    String endPage = pagination.getEndPage();
    String medlinePgn = pagination.getMedlinePgn();
}

// 3. 使用医学领域字段
List<Investigator> investigators = publication.getInvestigators();
List<PersonalNameSubject> personalNameSubjects = publication.getPersonalNameSubjects();
List<Reference> references = publication.getReferences();
Integer refCount = publication.getNumberOfReferences();
```

**优势**：
- ✅ 使用医学领域标准术语，语义更清晰
- ✅ 完整支持 MeSH 主题标引和限定词
- ✅ 支持临床试验、医学史等医学领域特有场景
- ✅ 提供结构化的参考文献和外部数据库引用
- ✅ 优化医学出版物处理性能

### v0.1.0: RestClient 统一管理

**背景**: v0.1.0 版本统一使用 `patra-spring-boot-starter-rest-client` 提供的 `defaultRestClient`，不再创建专用的 RestClient Bean。

**主要变更**:

1. **依赖变更**:
   - 新增: `patra-spring-boot-starter-rest-client` 依赖
   - 移除: 自定义 `pubMedRestClient` 和 `epmcRestClient` Bean

2. **自动配置变更**:
   - 移除: `pubMedRestClient` 和 `epmcRestClient` Bean
   - 修改: `PubMedClientAdapter` 和 `EpmcClientAdapter` 使用 `defaultRestClient`
   - 修改: BaseUrl 在每次请求时从 `ProvenanceConfig.baseUrl()` 动态构建

3. **设计意图**:
   - 避免 Spring Cloud LoadBalancer 的 BeanPostProcessor 包装 `JdkClientHttpRequestFactory`
   - 统一 HTTP 客户端配置管理（超时、重试、日志等）
   - 支持运行时动态切换配置

**影响范围**:
- **对用户透明**: 如果使用 `PubMedClient` 或 `EPMCClient` 接口，无需修改代码
- **自定义配置**: 如果手动创建了 `PubMedClientAdapter`，需要传入 `defaultRestClient`

**示例**:

```java
// 使用自动配置（推荐）
@Autowired
private PubMedClient pubMedClient;  // ✅ 直接注入

// 手动配置（高级用法）
@Autowired
private RestClient defaultRestClient;  // 使用统一的 RestClient

PubMedClient client = new PubMedClientAdapter(
    defaultRestClient,  // ✅ 使用 defaultRestClient
    configProvider,
    objectMapper,
    xmlMapper,
    null
);
```

**优势**:
- ✅ HTTP 客户端配置统一管理
- ✅ 避免 LoadBalancer 包装导致的外部 URL 调用失败
- ✅ 减少 Bean 数量，简化自动配置
- ✅ 支持运行时动态切换 baseUrl

### v0.1.0: API 常量迁移到 patra-common-provenance-api

**背景**: v0.1.0 版本将 API 常量从 `patra-spring-boot-starter-provenance` 迁移到独立的 `patra-common-provenance-api` 模块。

**主要变更**:

1. **包路径变更**:
   - 旧位置: `com.patra.starter.provenance.pubmed.request.PubMedParamKeys`
   - 新位置: `com.patra.common.provenance.api.params.PubMedParamKeys`

   - 旧位置: `com.patra.starter.provenance.epmc.request.EpmcParamKeys`
   - 新位置: `com.patra.common.provenance.api.params.EpmcParamKeys`

2. **新增功能**:
   - 端点路径常量: `CrossrefEndpoints`
   - 操作枚举: `PubMedOperation`、`EpmcOperation`（封装操作名称+端点+描述）
   - 参数值枚举: `RetMode`、`RetType`、`UseHistory`、`DateType`、`Format`、`ResultType`

3. **依赖关系**:
   - ✅ `patra-spring-boot-starter-provenance` 自动依赖 `patra-common-provenance-api`

**使用示例**:

```java
import com.patra.common.provenance.api.params.PubMedParamKeys;
import com.patra.common.provenance.api.values.pubmed.RetMode;
import com.patra.common.provenance.api.constants.PubMedOperation;

// 使用类型安全的枚举
params.put(PubMedParamKeys.RETMODE, RetMode.JSON.value());
```

**优势**:
- ✅ 单一事实来源（SSOT），避免重复定义
- ✅ 类型安全的枚举，避免魔法字符串
- ✅ 跨模块共享，支持测试、监控、CLI 等场景
- ✅ IDE 友好，自动补全和重构安全

参考 [patra-common-provenance-api/README.md](../patra-common/patra-common-provenance-api/README.md) 了解完整的 API 常量使用指南。
