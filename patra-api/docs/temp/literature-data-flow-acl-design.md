# Literature Data Flow & ACL Architecture Design

> **Document Type**: Architecture Design Document
> **Created**: 2025-01-19
> **Status**: Draft
> **Context**: patra-ingest → patra-catalog 数据流架构设计

---

## 1. 背景与问题

### 1.1 核心需求

patra-ingest 服务需要将从外部数据源（PubMed, EPMC, Crossref）采集的文献数据传递给 patra-catalog 服务进行存储和管理。

### 1.2 关键问题

**Q1: 平台统一的文献 POJO 应该放在哪里？**
- 选项 A: patra-catalog-domain（catalog 的领域模型）
- 选项 B: patra-catalog-api（catalog 的对外契约）
- 选项 C: patra-ingest-domain（ingest 的内部模型）
- 选项 D: patra-common（共享模块）

**Q2: patra-ingest-app 是否可以直接依赖 patra-catalog-api？**
- 如果可以，是否违反六边形架构？
- 如果不可以，如何实现数据传递？

**Q3: 数据转换应该在哪个阶段完成？**
- PubmedArticle → StandardLiterature：在 ingest 阶段
- StandardLiterature → Literature：在 catalog 阶段

### 1.3 架构约束

1. **六边形架构原则**：
   - Domain 层：纯 Java，零外部依赖（只依赖 patra-common + Lombok + Hutool）
   - App 层：只依赖 Domain 层和必要的基础设施接口
   - Infra 层：可以依赖外部模块，实现 Domain 层定义的 Port 接口

2. **契约稳定性**：
   - patra-catalog-api 的 LiteratureDTO 处于初期，可能频繁变化
   - 需要隔离外部契约变化对内部领域模型的影响

3. **模块解耦**：
   - patra-catalog 不应该知道数据源（PubMed, EPMC）的存在
   - patra-ingest 不应该知道 catalog 的内部领域模型（Literature）

---

## 2. 架构决策

### 2.1 方案对比

#### 方案 A: App 层直接依赖 catalog-api（实用主义）

```
patra-ingest-app → patra-catalog-api (LiteratureDTO)
```

**优点**：
- 简洁直接，减少转换层
- 代码量少，维护成本低

**缺点**：
- ❌ App 层有外部模块依赖，违反严格的六边形架构
- ❌ catalog-api 变化直接影响 App 层
- ❌ Domain 层间接依赖外部契约

**适用场景**：契约高度稳定、团队紧密协作

---

#### 方案 B: Infra 层 ACL（Anti-Corruption Layer）⭐ **推荐**

```
patra-ingest-domain: StandardLiterature (内部模型)
patra-ingest-app: 使用 StandardLiterature
patra-ingest-infra: ACL 转换 StandardLiterature → LiteratureDTO
```

**优点**：
- ✅ 完全符合六边形架构
- ✅ Domain/App 层零外部依赖
- ✅ 外部契约变化只需修改 ACL，不影响核心逻辑
- ✅ 可以灵活转换、过滤、聚合数据

**缺点**：
- 增加一层转换（StandardLiterature → LiteratureDTO）
- 代码量稍多

**适用场景**：
- ✅ 契约不稳定，可能频繁变化
- ✅ 需要严格遵守六边形架构
- ✅ 需要隔离外部依赖变化

---

### 2.2 最终决策

**选择方案 B：Infra 层 ACL**

理由：
1. patra-catalog-api 的 LiteratureDTO 处于初期，变化频率不确定
2. 需要严格遵守六边形架构，保持 Domain/App 层纯净
3. ACL 提供了灵活的隔离层，降低长期维护成本

---

## 3. 架构设计

### 3.1 三层数据模型

| 模型 | 位置 | 作用 | 所有者 |
|------|------|------|--------|
| **PubmedArticle** | starter-provenance | 数据源特定模型 | starter-provenance |
| **StandardLiterature** ⭐ | **patra-ingest-domain** | ingest 内部模型 | patra-ingest |
| **LiteratureDTO** | patra-catalog-api | 跨服务传输契约 | patra-catalog |
| **Literature** | patra-catalog-domain | catalog 领域模型 | patra-catalog |

### 3.2 依赖关系

```
┌──────────────────────┐
│ starter-provenance   │
│  - PubmedArticle     │
└───────┬──────────────┘
        │ 依赖
        ↓
┌──────────────────────┐        ┌──────────────────────┐
│ patra-ingest-app     │───────→│ patra-ingest-domain  │
│  - Converter         │ 依赖   │  - StandardLiterature│
│  - Executor          │        │  - Port 接口         │
└───────┬──────────────┘        └──────────────────────┘
        │                                  ↑
        │                                  │ 实现
        ↓                                  │
┌──────────────────────────────────────────────────────┐
│ patra-ingest-infra                                   │
│  ┌────────────────────────────────────────────────┐  │
│  │ LiteraturePublisherAdapter (ACL)               │  │
│  │  - 实现 LiteraturePublisherPort                │  │
│  │  - 转换 StandardLiterature → LiteratureDTO     │  │
│  │  - 依赖 patra-catalog-api                      │  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────┬───────────────────────────────┘
                       │ 依赖（仅在此处）
                       ↓
            ┌──────────────────────┐
            │ patra-catalog-api    │
            │  - LiteratureDTO     │
            └──────────────────────┘
```

**关键点**：
- ✅ Domain 层：**零外部业务模块依赖**
- ✅ App 层：只依赖 Domain + starter-provenance（数据源）
- ✅ Infra 层：作为 ACL，**隔离所有外部契约依赖**

### 3.3 数据流

#### Phase 1: patra-ingest 采集和发布

```
1. PubmedBatchExecutor.execute()
   └─> ESearch (获取 PMID)
   └─> EFetch (获取 PubmedArticle)
   └─> PubmedArticleConverter.toStandardLiterature()
       └─> StandardLiterature (ingest 内部模型)
   └─> LiteraturePublisherPort.publish()
       └─> LiteraturePublisherAdapter (ACL)
           └─> toLiteratureDTO() (转换)
               └─> LiteratureDTO (catalog 契约)
           └─> serialize + compress
           └─> upload to MinIO
           └─> return storageKey
```

#### Phase 2: patra-catalog 消费和入库

```
1. LiteratureDataConsumer.onLiteratureDataReady()
   └─> download from MinIO
   └─> deserialize to LiteratureDTO
   └─> IngestLiteratureOrchestrator.ingest()
       └─> LiteratureDTOConverter.toDomain()
           └─> Literature (catalog 领域模型)
       └─> LiteratureRepository.save()
```

---

## 4. 详细实现

### 4.1 Domain 层：定义内部模型和 Port

#### StandardLiterature (内部模型)

```java
// patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/StandardLiterature.java
package com.patra.ingest.domain.model.vo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * 平台标准化的文献数据（ingest 内部模型）
 *
 * <p>这是 ingest 领域的概念，不依赖任何外部模块。
 * <p>用于统一不同数据源（PubMed, EPMC, Crossref）的文献数据格式。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
public class StandardLiterature {

  /** 文献标题 */
  String title;

  /** 摘要文本 */
  String abstractText;

  /** 作者列表 */
  List<StandardAuthor> authors;

  /** 期刊信息 */
  StandardJournal journal;

  /** 标识符集合 (pmid, doi, pmc, etc.) */
  Map<String, String> identifiers;

  /** 发表日期 */
  LocalDate publicationDate;

  /** 关键词 */
  List<String> keywords;

  /**
   * 标准化作者信息
   */
  @Value
  @Builder
  public static class StandardAuthor {
    String lastName;
    String foreName;
    String affiliation;
  }

  /**
   * 标准化期刊信息
   */
  @Value
  @Builder
  public static class StandardJournal {
    String title;
    String issn;
    String publisher;
  }
}
```

#### LiteraturePublisherPort (输出端口)

```java
// patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/LiteraturePublisherPort.java
package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.vo.StandardLiterature;
import java.util.List;
import lombok.Builder;

/**
 * 文献发布端口（输出端口）
 *
 * <p>Domain 层定义接口，Infrastructure 层实现。
 * <p>App 层通过这个接口发布数据，完全不知道具体的实现细节。
 *
 * <p>实现者负责：
 * <ul>
 *   <li>将 StandardLiterature 转换为外部格式（如 LiteratureDTO）
 *   <li>序列化并上传到对象存储（MinIO）
 *   <li>返回存储位置（storageKey）
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface LiteraturePublisherPort {

  /**
   * 发布标准化的文献数据到存储
   *
   * @param literature 标准化的文献列表
   * @param context 发布上下文（runId, batchNo 等）
   * @return 发布结果（包含 storageKey）
   */
  PublishResult publish(List<StandardLiterature> literature, PublishContext context);

  /**
   * 发布结果
   */
  @Builder
  record PublishResult(
      /** 对象存储的 key */
      String storageKey,
      /** 实际发布的文献数量 */
      int publishedCount
  ) {}

  /**
   * 发布上下文
   */
  @Builder
  record PublishContext(
      /** 执行批次的 runId */
      Long runId,
      /** 批次编号 */
      int batchNo,
      /** 数据源代码 (PUBMED, EPMC, etc.) */
      String provenanceCode
  ) {}
}
```

### 4.2 App 层：使用 Domain 模型

#### PubmedArticleConverter

```java
// patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/execute/converter/PubmedArticleConverter.java
package com.patra.ingest.app.usecase.execution.execute.converter;

import com.patra.ingest.domain.model.vo.StandardLiterature;
import com.patra.ingest.domain.model.vo.StandardLiterature.StandardAuthor;
import com.patra.ingest.domain.model.vo.StandardLiterature.StandardJournal;
import com.patra.starter.provenance.pubmed.model.response.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PubMed 文章转换器
 *
 * <p>将 PubmedArticle（外部数据源模型）转换为 StandardLiterature（内部领域模型）
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@Slf4j
public class PubmedArticleConverter {

  /**
   * 转换 PubmedArticle 为 StandardLiterature
   *
   * @param article PubMed 文章
   * @return 标准化文献数据
   */
  public StandardLiterature toStandardLiterature(PubmedArticle article) {
    return StandardLiterature.builder()
        .title(extractTitle(article))
        .abstractText(extractAbstract(article))
        .authors(convertAuthors(article))
        .journal(convertJournal(article))
        .identifiers(buildIdentifiers(article))
        .publicationDate(extractPublicationDate(article))
        .keywords(extractKeywords(article))
        .build();
  }

  private String extractTitle(PubmedArticle article) {
    return article.article() != null ? article.article().title() : null;
  }

  private String extractAbstract(PubmedArticle article) {
    return article.article() != null ? article.article().abstractText() : null;
  }

  private List<StandardAuthor> convertAuthors(PubmedArticle article) {
    if (article.article() == null || article.article().authors() == null) {
      return Collections.emptyList();
    }

    return article.article().authors().stream()
        .map(author -> StandardAuthor.builder()
            .lastName(author.lastName())
            .foreName(author.foreName())
            .affiliation(author.affiliation())
            .build())
        .collect(Collectors.toList());
  }

  private StandardJournal convertJournal(PubmedArticle article) {
    if (article.journalInfo() == null) {
      return null;
    }

    return StandardJournal.builder()
        .title(article.journalInfo().medlineTA())
        .issn(article.journalInfo().issnLinking())
        .publisher(null)  // PubMed doesn't provide publisher info
        .build();
  }

  private Map<String, String> buildIdentifiers(PubmedArticle article) {
    Map<String, String> identifiers = new HashMap<>();

    // PMID (always present)
    if (article.pmid() != null) {
      identifiers.put("pmid", article.pmid());
    }

    // DOI (optional)
    extractDoi(article).ifPresent(doi -> identifiers.put("doi", doi));

    // PMC ID (optional)
    extractPmcId(article).ifPresent(pmc -> identifiers.put("pmc", pmc));

    return identifiers;
  }

  private Optional<String> extractDoi(PubmedArticle article) {
    // DOI extraction logic from PubmedData
    if (article.pubmedData() == null) {
      return Optional.empty();
    }
    // TODO: implement DOI extraction from article IDs
    return Optional.empty();
  }

  private Optional<String> extractPmcId(PubmedArticle article) {
    // PMC ID extraction logic
    // TODO: implement PMC ID extraction
    return Optional.empty();
  }

  private LocalDate extractPublicationDate(PubmedArticle article) {
    // TODO: implement publication date extraction
    return null;
  }

  private List<String> extractKeywords(PubmedArticle article) {
    if (article.article() == null || article.article().keywords() == null) {
      return Collections.emptyList();
    }
    return article.article().keywords();
  }
}
```

#### PubmedBatchExecutor

```java
// patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/execute/PubmedBatchExecutor.java
package com.patra.ingest.app.usecase.execution.execute;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.execution.execute.converter.PubmedArticleConverter;
import com.patra.ingest.domain.model.vo.*;
import com.patra.ingest.domain.port.LiteraturePublisherPort;
import com.patra.ingest.domain.port.PubmedSearchPort;
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.model.request.*;
import com.patra.starter.provenance.pubmed.model.response.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PubMed 批次执行器
 *
 * <p>职责：
 * <ul>
 *   <li>通过 ESearch 获取 PMID 列表
 *   <li>通过 EFetch 获取文献详情
 *   <li>转换为 StandardLiterature
 *   <li>通过 LiteraturePublisherPort 发布数据
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PubmedBatchExecutor implements BatchExecutor {

  private final PubMedClient pubMedClient;
  private final LiteraturePublisherPort publisherPort;  // ⭐ 使用 Port 接口
  private final PubmedArticleConverter converter;

  @Override
  public ProvenanceCode getProvenanceCode() {
    return ProvenanceCode.PUBMED;
  }

  @Override
  public BatchResult execute(ExecutionContext context, Batch batch) {
    int batchNo = batch.batchNo();
    String queryHash = hashCode(batch.compiledQuery());

    log.info(
        "[INGEST][APP] pubmed batch execute start batchNo={} queryHash={} retstart={}",
        batchNo,
        queryHash,
        batch.batchParams().path("retstart").asInt());

    try {
      // Step 1: ESearch - Get PMID list
      List<String> pmids = executePubmedSearch(context, batch);
      if (pmids.isEmpty()) {
        log.info("[INGEST][APP] pubmed batch empty batchNo={} queryHash={}",
            batchNo, queryHash);
        return BatchResult.success(batchNo, 0, null, null);
      }

      log.info("[INGEST][APP] pubmed esearch got {} pmids for batch {}",
          pmids.size(), batchNo);

      // Step 2: EFetch - Get article details
      List<PubmedArticle> articles = executePubmedFetch(context, pmids);
      log.info("[INGEST][APP] pubmed efetch got {} articles for batch {}",
          articles.size(), batchNo);

      // Step 3: Convert to StandardLiterature (Domain model)
      List<StandardLiterature> standardLiterature = articles.stream()
          .map(converter::toStandardLiterature)
          .collect(Collectors.toList());

      // Step 4: Publish via Port (不知道具体实现)
      LiteraturePublisherPort.PublishContext publishContext =
          LiteraturePublisherPort.PublishContext.builder()
              .runId(context.runId())
              .batchNo(batchNo)
              .provenanceCode(context.provenanceCode())
              .build();

      LiteraturePublisherPort.PublishResult result =
          publisherPort.publish(standardLiterature, publishContext);

      log.info(
          "[INGEST][APP] pubmed batch success batchNo={} fetchedCount={} storageKey={}",
          batchNo,
          result.publishedCount(),
          result.storageKey());

      // Step 5: Return result
      return BatchResult.success(
          batchNo,
          result.publishedCount(),
          null,  // PubMed uses offset pagination, no cursor
          result.storageKey());

    } catch (Exception e) {
      log.error("[INGEST][APP] batch execution failed batchNo={}", batchNo, e);
      return BatchResult.failure(batchNo, "Execution error: " + e.getMessage());
    }
  }

  private List<String> executePubmedSearch(ExecutionContext context, Batch batch) {
    ESearchRequest searchReq = buildESearchRequest(batch);
    ESearchResponse searchResp = pubMedClient.esearch(
        searchReq,
        toProvenanceConfig(context.configSnapshot()));

    return extractPmids(searchResp);
  }

  private List<PubmedArticle> executePubmedFetch(ExecutionContext context, List<String> pmids) {
    EFetchRequest fetchReq = buildEFetchRequest(pmids);
    EFetchResponse fetchResp = pubMedClient.efetch(
        fetchReq,
        toProvenanceConfig(context.configSnapshot()));

    return fetchResp.articles();
  }

  private ESearchRequest buildESearchRequest(Batch batch) {
    return ESearchRequest.builder()
        .term(batch.compiledQuery())
        .retstart(batch.batchParams().path("retstart").asInt(0))
        .retmax(batch.batchParams().path("retmax").asInt(100))
        .retmode("json")
        .usehistory(false)
        .build();
  }

  private EFetchRequest buildEFetchRequest(List<String> pmids) {
    return EFetchRequest.builder()
        .ids(pmids)
        .retmode("xml")  // EFetch only supports XML
        .rettype("full")
        .build();
  }

  private List<String> extractPmids(ESearchResponse response) {
    if (response == null || response.result() == null) {
      return Collections.emptyList();
    }
    List<String> idlist = response.result().idlist();
    return idlist != null ? idlist : Collections.emptyList();
  }

  private ProvenanceConfig toProvenanceConfig(ProvenanceConfigSnapshot snapshot) {
    // TODO: implement conversion
    return null;
  }

  private String hashCode(String s) {
    return s != null ? Integer.toHexString(s.hashCode()) : "null";
  }
}
```

### 4.3 Infrastructure 层：ACL 实现

#### LiteraturePublisherAdapter (ACL)

```java
// patra-ingest-infra/src/main/java/com/patra/ingest/infra/adapter/LiteraturePublisherAdapter.java
package com.patra.ingest.infra.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.api.dto.*;  // ⭐ 只在这里依赖外部模块
import com.patra.ingest.domain.model.vo.StandardLiterature;
import com.patra.ingest.domain.model.vo.StandardLiterature.StandardAuthor;
import com.patra.ingest.domain.model.vo.StandardLiterature.StandardJournal;
import com.patra.ingest.domain.port.LiteraturePublisherPort;
import com.patra.ingest.domain.port.StorageAdapter;
import com.patra.ingest.domain.port.StorageAdapter.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文献发布适配器（ACL - Anti-Corruption Layer）
 *
 * <p>职责：
 * <ul>
 *   <li>将 Domain 模型（StandardLiterature）转换为外部契约（LiteratureDTO）
 *   <li>序列化并上传到对象存储（MinIO）
 *   <li>隔离外部模块（patra-catalog-api）的变化
 * </ul>
 *
 * <p><strong>ACL 的核心价值</strong>：
 * 当 catalog-api 的 LiteratureDTO 结构变化时，只需修改这个类的转换逻辑，
 * Domain 和 App 层完全不受影响。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LiteraturePublisherAdapter implements LiteraturePublisherPort {

  private final StorageAdapter storageAdapter;
  private final ObjectMapper objectMapper;

  @Override
  public PublishResult publish(
      List<StandardLiterature> literature,
      PublishContext context) {

    try {
      // ⭐ ACL 核心：StandardLiterature → LiteratureDTO（转换层）
      List<LiteratureDTO> dtos = literature.stream()
          .map(this::toLiteratureDTO)
          .collect(Collectors.toList());

      // 序列化为 JSON
      byte[] jsonData = objectMapper.writeValueAsBytes(dtos);
      byte[] compressedData = gzipCompress(jsonData);

      // 上传到 MinIO
      String objectPath = storageAdapter.generateObjectPath(
          context.provenanceCode(),
          context.runId(),
          context.batchNo());

      StorageUploadRequest uploadReq = StorageUploadRequest.builder()
          .data(compressedData)
          .objectPath(objectPath)
          .contentType("application/json")
          .encoding("gzip")
          .metadata(Map.of(
              "batchNo", String.valueOf(context.batchNo()),
              "count", String.valueOf(dtos.size()),
              "provenanceCode", context.provenanceCode()
          ))
          .build();

      StorageUploadResult uploadResult = storageAdapter.upload(uploadReq);

      log.info(
          "[INGEST][INFRA] published {} literature to storage key={} size={}bytes",
          dtos.size(),
          uploadResult.storageKey(),
          compressedData.length);

      return PublishResult.builder()
          .storageKey(uploadResult.storageKey())
          .publishedCount(dtos.size())
          .build();

    } catch (IOException e) {
      log.error("[INGEST][INFRA] failed to serialize literature", e);
      throw new LiteraturePublishException("Failed to serialize literature", e);
    } catch (Exception e) {
      log.error("[INGEST][INFRA] failed to publish literature", e);
      throw new LiteraturePublishException("Failed to publish literature", e);
    }
  }

  /**
   * ⭐⭐⭐ ACL 核心方法：转换 Domain 模型 → 外部契约
   *
   * <p><strong>变化隔离</strong>：
   * 当 LiteratureDTO 结构变化时（如字段重命名、新增字段、类型调整），
   * 只需修改这个方法和相关辅助方法，Domain/App 层代码无需任何修改。
   *
   * @param std 内部标准化文献模型
   * @return 外部契约 DTO
   */
  private LiteratureDTO toLiteratureDTO(StandardLiterature std) {
    return LiteratureDTO.builder()
        .title(std.getTitle())
        .abstractText(std.getAbstractText())
        .authors(convertAuthors(std.getAuthors()))
        .journal(convertJournal(std.getJournal()))
        .identifiers(std.getIdentifiers())
        .publicationDate(std.getPublicationDate())
        .keywords(std.getKeywords())
        .build();
  }

  private List<AuthorDTO> convertAuthors(List<StandardAuthor> authors) {
    if (authors == null) {
      return Collections.emptyList();
    }

    return authors.stream()
        .map(a -> AuthorDTO.builder()
            .lastName(a.getLastName())
            .foreName(a.getForeName())
            .affiliation(a.getAffiliation())
            .build())
        .collect(Collectors.toList());
  }

  private JournalDTO convertJournal(StandardJournal journal) {
    if (journal == null) {
      return null;
    }

    return JournalDTO.builder()
        .title(journal.getTitle())
        .issn(journal.getIssn())
        .publisher(journal.getPublisher())
        .build();
  }

  private byte[] gzipCompress(byte[] data) throws IOException {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
      gzipStream.write(data);
    }
    return byteStream.toByteArray();
  }

  /**
   * 发布异常
   */
  public static class LiteraturePublishException extends RuntimeException {
    public LiteraturePublishException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
```

### 4.4 pom.xml 依赖配置

#### patra-ingest-domain/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <parent>
        <groupId>com.patra</groupId>
        <artifactId>patra-ingest</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>patra-ingest-domain</artifactId>
    <name>patra-ingest-domain</name>

    <dependencies>
        <!-- ⭐ 只依赖基础库，无外部业务模块 -->
        <dependency>
            <groupId>com.patra</groupId>
            <artifactId>patra-common</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>

        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
    </dependencies>
</project>
```

#### patra-ingest-app/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <parent>
        <groupId>com.patra</groupId>
        <artifactId>patra-ingest</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>patra-ingest-app</artifactId>
    <name>patra-ingest-app</name>

    <dependencies>
        <!-- Domain 层 -->
        <dependency>
            <groupId>com.patra</groupId>
            <artifactId>patra-ingest-domain</artifactId>
        </dependency>

        <!-- 数据源依赖（需要 PubmedArticle） -->
        <dependency>
            <groupId>com.patra</groupId>
            <artifactId>patra-spring-boot-starter-provenance</artifactId>
        </dependency>

        <!-- ⭐ 不依赖 patra-catalog-api -->

        <!-- Spring 相关 -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
    </dependencies>
</project>
```

#### patra-ingest-infra/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <parent>
        <groupId>com.patra</groupId>
        <artifactId>patra-ingest</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>patra-ingest-infra</artifactId>
    <name>patra-ingest-infra</name>

    <dependencies>
        <!-- Domain 层 -->
        <dependency>
            <groupId>com.patra</groupId>
            <artifactId>patra-ingest-domain</artifactId>
        </dependency>

        <!-- ⭐ 只在这里依赖 catalog-api（ACL 使用） -->
        <dependency>
            <groupId>com.patra</groupId>
            <artifactId>patra-catalog-api</artifactId>
        </dependency>

        <!-- 基础设施 -->
        <dependency>
            <groupId>com.patra</groupId>
            <artifactId>patra-spring-boot-starter-mybatis</artifactId>
        </dependency>

        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## 5. ACL 的核心价值

### 5.1 变化隔离示例

**场景 1：LiteratureDTO 添加新字段**

```java
// catalog-api 添加新字段
public class LiteratureDTO {
    private String newField;  // ⭐ 新增
}

// ⭐ 只需修改 ACL
private LiteratureDTO toLiteratureDTO(StandardLiterature std) {
    return LiteratureDTO.builder()
        // ... existing fields
        .newField(computeNewField(std))  // ⭐ 在这里处理
        .build();
}

// Domain 和 App 层代码：无需任何修改！
```

**场景 2：LiteratureDTO 字段重命名**

```java
// catalog-api 重命名字段
public class LiteratureDTO {
    private String summary;  // 原来叫 abstractText
}

// ⭐ 只需修改 ACL
private LiteratureDTO toLiteratureDTO(StandardLiterature std) {
    return LiteratureDTO.builder()
        .summary(std.getAbstractText())  // ⭐ 映射到新名称
        .build();
}

// Domain 和 App 层代码：无需任何修改！
```

**场景 3：LiteratureDTO 类型变化**

```java
// catalog-api 修改类型
public class LiteratureDTO {
    private Instant publicationDate;  // 原来是 LocalDate
}

// ⭐ 只需修改 ACL
private LiteratureDTO toLiteratureDTO(StandardLiterature std) {
    return LiteratureDTO.builder()
        .publicationDate(
            std.getPublicationDate() != null
                ? std.getPublicationDate().atStartOfDay().toInstant(ZoneOffset.UTC)
                : null
        )
        .build();
}

// Domain 和 App 层代码：无需任何修改！
```

### 5.2 灵活性示例

**过滤敏感字段**

```java
private LiteratureDTO toLiteratureDTO(StandardLiterature std) {
    return LiteratureDTO.builder()
        // 过滤作者的敏感信息（如邮箱）
        .authors(filterSensitiveAuthorInfo(std.getAuthors()))
        .build();
}
```

**字段聚合/拆分**

```java
private LiteratureDTO toLiteratureDTO(StandardLiterature std) {
    return LiteratureDTO.builder()
        // 将多个字段聚合为一个
        .fullTitle(buildFullTitle(std.getTitle(), std.getJournal()))
        .build();
}
```

**添加计算字段**

```java
private LiteratureDTO toLiteratureDTO(StandardLiterature std) {
    return LiteratureDTO.builder()
        .citationCount(computeCitationCount(std))  // 动态计算
        .qualityScore(assessQuality(std))
        .build();
}
```

---

## 6. 实施步骤

### Phase 1: Domain 层实现（无外部依赖）

- [ ] 创建 `StandardLiterature.java`（内部模型）
- [ ] 创建 `LiteraturePublisherPort.java`（输出端口接口）
- [ ] 编写单元测试（纯 Java，无 Spring）

### Phase 2: App 层实现（使用 Domain 模型）

- [ ] 实现 `PubmedArticleConverter.java`
- [ ] 修改 `PubmedBatchExecutor.java`，使用 Port 接口
- [ ] 编写单元测试（Mock Port）

### Phase 3: Infra 层实现（ACL）

- [ ] 等待 patra-catalog-api 模块创建（包含 LiteratureDTO）
- [ ] 实现 `LiteraturePublisherAdapter.java`（ACL 转换）
- [ ] 在 `pom.xml` 中添加 catalog-api 依赖
- [ ] 编写集成测试（使用 TestContainers 测试 MinIO 上传）

### Phase 4: 端到端测试

- [ ] 测试完整流程：PubmedArticle → StandardLiterature → LiteratureDTO → MinIO
- [ ] 验证 JSON 序列化/反序列化
- [ ] 验证 GZIP 压缩/解压
- [ ] 性能测试（大批量数据）

### Phase 5: catalog 消费端实现（catalog 模块）

- [ ] 实现 `LiteratureDataConsumer.java`（MQ 消费者）
- [ ] 实现 `LiteratureDTOConverter.java`（DTO → Domain）
- [ ] 实现 `IngestLiteratureOrchestrator.java`
- [ ] 端到端集成测试

---

## 7. 架构检查清单

在实施过程中，定期检查以下关键点：

### 依赖方向检查

- [ ] patra-ingest-domain：无任何业务模块依赖（只依赖 patra-common + Lombok + Hutool）
- [ ] patra-ingest-app：不依赖 patra-catalog-api
- [ ] patra-ingest-infra：只在这里依赖 patra-catalog-api

### 六边形架构检查

- [ ] Domain 层：纯 Java，无 Spring 注解（除 Lombok）
- [ ] App 层：通过 Port 接口与外部交互，不依赖具体实现
- [ ] Infra 层：实现 Port 接口，可以依赖外部模块

### ACL 职责检查

- [ ] ACL 只负责模型转换（StandardLiterature → LiteratureDTO）
- [ ] ACL 不包含业务逻辑
- [ ] ACL 隔离了 catalog-api 的变化

### 测试覆盖检查

- [ ] Domain 层：单元测试覆盖率 > 80%
- [ ] App 层：单元测试（Mock Port）覆盖率 > 70%
- [ ] Infra 层：集成测试验证 ACL 转换正确性
- [ ] 端到端测试：验证完整数据流

---

## 8. 常见问题

### Q1: 为什么不直接在 App 层依赖 catalog-api？

**A**: 严格的六边形架构要求 Domain/App 层零外部依赖。catalog-api 的 LiteratureDTO 处于初期，可能频繁变化。如果 App 层直接依赖，每次契约变化都需要修改核心业务逻辑。ACL 提供了变化隔离层，降低长期维护成本。

### Q2: ACL 会增加多少工作量？

**A**: 初期会增加约 20% 的代码量（一个 Converter 类）。但长期看，当契约变化时，修改成本远低于直接依赖方案。特别是当 catalog-api 变化频繁时，ACL 的价值会更明显。

### Q3: StandardLiterature 和 LiteratureDTO 很相似，是否重复？

**A**: 表面上看字段相似，但它们的职责不同：
- StandardLiterature：ingest 内部的领域概念，可以自由演化
- LiteratureDTO：catalog 对外的契约，需要保持稳定和向后兼容

两者解耦后，各自可以独立演化，避免相互牵制。

### Q4: 如果 LiteratureDTO 一直很稳定，ACL 是否过度设计？

**A**: 这是一个权衡问题。如果：
- catalog-api 已经稳定（版本 > 1.0）
- 团队紧密协作，能够同步调整
- 不涉及跨团队/跨部门

那么可以考虑简化为直接依赖。但当前 catalog-api 处于初期，建议使用 ACL。

### Q5: catalog 消费端也需要 ACL 吗？

**A**: 是的！catalog 应该有自己的 ACL，将 LiteratureDTO → Literature（catalog 的领域模型）。这样：
- catalog 可以自由定义自己的领域模型
- LiteratureDTO 的变化不会直接影响 catalog 的核心逻辑
- 两个服务都通过 ACL 隔离外部契约

---

## 9. 参考资料

### 架构模式

- **Hexagonal Architecture** (Ports and Adapters): Alistair Cockburn
- **Anti-Corruption Layer (ACL)**: Eric Evans, Domain-Driven Design
- **Published Language**: DDD Context Mapping Pattern
- **Conformist**: DDD Context Mapping Pattern

### 代码示例

- `patra-ingest-domain/model/vo/StandardLiterature.java`
- `patra-ingest-domain/port/LiteraturePublisherPort.java`
- `patra-ingest-app/converter/PubmedArticleConverter.java`
- `patra-ingest-infra/adapter/LiteraturePublisherAdapter.java`

### 相关文档

- [patra-ingest-flow.md](./patra-ingest-flow.md) - 数据采集流程
- [patra-ingest README.md](../patra-ingest/README.md) - 模块说明

---

**Last Updated**: 2025-01-19
**Author**: Claude Code + linqibin
**Version**: 1.0-draft
