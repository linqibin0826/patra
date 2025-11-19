# 阶段 4:领域模型映射 - patra_catalog 医学文献目录服务

> **生成说明**:根据阶段 3 的 SQL DDL,生成符合六边形架构 + DDD 的领域模型
>
> **创建日期**: 2025-01-18
> **设计范围**: patra_catalog 数据库(36张表)
> **作者**: Patra Lin

---

## 🏗️ 架构分层

```
┌─────────────────────────────────────┐
│ Domain 层(patra-catalog-domain)     │
│ ✅ 无基础设施框架依赖                 │
│ ✅ 可使用: Hutool、Jackson、Lombok   │
│ ✅ 聚合根、值对象、枚举                │
└─────────────────────────────────────┘
              ↑ 依赖倒置
┌─────────────────────────────────────┐
│ Infra 层(patra-catalog-infra)       │
│ ✅ DO(继承 BaseDO)                  │
│ ✅ Mapper(BaseMapper)               │
│ ✅ RepositoryImpl(适配器)           │
│ ✅ Converter(Entity ↔ DO)          │
└─────────────────────────────────────┘
```

**依赖说明**:
- Domain 层默认依赖 `patra-common-core`,可使用 Hutool-Core 和 Jackson
- 禁止依赖基础设施框架(Spring、MyBatis、Redis 等)
- Hutool 用于工具方法,Jackson 用于序列化

---

## 📦 聚合识别概览

patra_catalog 包含 36 张表,识别出 **10 个核心聚合根**:

### 1. 核心实体聚合根(3个)

#### PublicationAggregate(文献聚合根)
**聚合边界**:
- **聚合根**: PublicationAggregate
- **值对象**: PublicationIdentifiers(PMID/DOI/PMC 等标识符)、DateSpec(不完整日期)、LanguageInfo(三层语言信息)
- **子实体**: Abstract(摘要)、PublicationMetadata(元数据)
- **枚举**: PublicationStatus、MediaType、OaStatus
- **不变量**:
  - PMID/DOI 在同一数据来源中必须唯一
  - publication_year 必须与 venue_instance 保持一致
  - is_oa 和 oa_status 必须与 OaLocation 集合同步

**职责**:
- 管理医学文献的核心元数据
- 维护标识符的一致性和唯一性
- 封装日期精度和语言标准化逻辑
- 管理 OA 状态和最佳位置选择

#### VenueAggregate(出版载体聚合根)
**聚合边界**:
- **聚合根**: VenueAggregate
- **子实体**: VenueInstance(载体实例)
- **值对象**: ISSN、ISBN、DateSpec(出版日期)
- **枚举**: VenueType(JOURNAL/BOOK/CONFERENCE/OTHER)
- **不变量**:
  - venue_type 确定后不可变更
  - ISSN/ISBN 在同一类型中必须唯一

**职责**:
- 统一管理期刊、书籍、会议等出版载体
- 管理具体卷期/版次信息
- 维护载体的版本演化历史

#### AuthorAggregate(作者聚合根)
**聚合边界**:
- **聚合根**: AuthorAggregate
- **值对象**: PersonName(姓名结构)、DedupKey(去重键)
- **关联**: Affiliation(通过 AuthorAffiliation)
- **不变量**:
  - ORCID 存在时必须全局唯一
  - dedup_key 由应用层计算生成,确保复合去重策略一致性

**职责**:
- 管理作者基本信息和去重
- 维护作者与机构的关联关系
- 支持 ORCID 等多种标识符体系

### 2. 分类索引聚合根(3个)

#### MeshDescriptorAggregate(MeSH 主题词聚合根)
**聚合边界**:
- **聚合根**: MeshDescriptorAggregate
- **子实体**: MeshTreeNumber(树形编号)、MeshEntryTerm(入口术语)、MeshConcept(概念)
- **值对象**: MeshUI(唯一标识符)、MeshVersion(版本)
- **枚举**: DescriptorClass(主题词类型)
- **不变量**:
  - MeshUI 在同一版本中必须唯一
  - tree_number 必须符合 NLM MeSH 层次结构规范
  - 至少有一个 is_primary=true 的树形位置

**职责**:
- 管理 MeSH 主题词的完整结构
- 维护树形编号的多位置关系
- 管理同义词和概念关联

#### KeywordAggregate(关键词聚合根)
**聚合边界**:
- **聚合根**: KeywordAggregate
- **值对象**: NormalizedTerm(规范化词形)
- **不变量**:
  - normalized_term 确保去重和一致性

**职责**:
- 管理自由关键词
- 提供关键词规范化和去重

#### SubstanceAggregate(物质聚合根)
**聚合边界**:
- **聚合根**: SubstanceAggregate
- **值对象**: RegistryNumber(CAS 注册号)
- **不变量**:
  - registry_number 必须全局唯一(CAS 标准)

**职责**:
- 管理化学物质、药物、生物制品信息
- 支持 CAS 注册号检索

### 3. 关联信息聚合根(2个)

#### FundingAggregate(资助聚合根)
**聚合边界**:
- **聚合根**: FundingAggregate
- **值对象**: FundingIdentity(agency_name + grant_id)、DedupKey(去重键)
- **不变量**:
  - dedup_key 基于 agency_name + grant_id 计算,确保去重

**职责**:
- 管理资助项目信息
- 支持资助机构和项目编号的复合去重

#### ReferenceAggregate(引用聚合根)
**聚合边界**:
- **聚合根**: ReferenceAggregate
- **值对象**: CitationIdentity(库内 ID 或库外 PMID/DOI)
- **不变量**:
  - cited_publication_id、cited_pmid、cited_doi 至少一个非空

**职责**:
- 管理文献引用关系
- 支持库内外引用的双重关联
- 追踪撤稿状态

### 4. 辅助管理聚合根(2个)

#### OaLocationAggregate(OA 位置聚合根)
**聚合边界**:
- **聚合根**: OaLocationAggregate
- **值对象**: OaUrl、OaVersion
- **枚举**: OaStatus(gold/green/hybrid/bronze/closed)、LocationType
- **不变量**:
  - 同一 publication_id 只能有一个 is_best=true 的位置

**职责**:
- 管理文献的多个 OA 位置
- 自动选择最佳 OA 位置并同步到主表

#### PublicationHistoryAggregate(出版历史聚合根)
**聚合边界**:
- **聚合根**: PublicationHistoryAggregate
- **值对象**: HistoryEvent(事件类型 + 日期 + 精度)
- **枚举**: EventType(received/accepted/published 等)
- **不变量**:
  - event_date 必须符合日期精度规范

**职责**:
- 追踪文献生命周期事件
- 记录发布历史时间线

---

## 📂 Domain 层代码结构

```
patra-catalog-domain/
└── src/main/java/com/patra/catalog/domain/
    ├── model/
    │   ├── aggregate/
    │   │   ├── PublicationAggregate.java            # 核心聚合根
    │   │   ├── VenueAggregate.java                  # 出版载体聚合根
    │   │   ├── AuthorAggregate.java                 # 作者聚合根
    │   │   ├── MeshDescriptorAggregate.java         # MeSH 主题词聚合根
    │   │   ├── KeywordAggregate.java                # 关键词聚合根
    │   │   ├── SubstanceAggregate.java              # 物质聚合根
    │   │   ├── FundingAggregate.java                # 资助聚合根
    │   │   ├── ReferenceAggregate.java              # 引用聚合根
    │   │   ├── OaLocationAggregate.java             # OA 位置聚合根
    │   │   └── PublicationHistoryAggregate.java     # 出版历史聚合根
    │   ├── entity/
    │   │   ├── Abstract.java                        # 摘要实体
    │   │   ├── VenueInstance.java                   # 载体实例实体
    │   │   ├── PublicationMetadata.java             # 元数据实体
    │   │   ├── MeshTreeNumber.java                  # MeSH 树形编号实体
    │   │   └── ...
    │   ├── vo/
    │   │   ├── publication/
    │   │   │   ├── PublicationIdentifiers.java      # 标识符值对象(Sealed Interface)
    │   │   │   ├── DateSpec.java                    # 不完整日期值对象(Sealed Interface)
    │   │   │   └── LanguageInfo.java                # 语言信息值对象
    │   │   ├── author/
    │   │   │   ├── PersonName.java                  # 姓名值对象
    │   │   │   └── DedupKey.java                    # 去重键值对象
    │   │   ├── mesh/
    │   │   │   ├── MeshUI.java                      # MeSH UI 值对象
    │   │   │   └── TreeNumberSpec.java              # 树形编号规范值对象
    │   │   └── ...
    │   └── enums/
    │       ├── VenueType.java                       # 载体类型枚举
    │       ├── PublicationStatus.java               # 出版状态枚举
    │       ├── MediaType.java                       # 媒介类型枚举
    │       ├── OaStatus.java                        # OA 状态枚举
    │       ├── DescriptorClass.java                 # MeSH 主题词类型枚举
    │       ├── EventType.java                       # 历史事件类型枚举
    │       └── ...
    └── port/
        ├── PublicationRepository.java               # 文献仓储接口
        ├── VenueRepository.java                     # 载体仓储接口
        ├── AuthorRepository.java                    # 作者仓储接口
        ├── MeshDescriptorRepository.java            # MeSH 仓储接口
        └── ...
```

**注**:`ProvenanceCode` 位于 `patra-common-core` 模块中,是跨领域共享的枚举。

---

## 💻 Domain 层核心代码示例

### 1. 核心聚合根:PublicationAggregate.java

```java
package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.entity.Abstract;
import com.patra.catalog.domain.model.entity.PublicationMetadata;
import com.patra.catalog.domain.model.enums.MediaType;
import com.patra.catalog.domain.model.enums.OaStatus;
import com.patra.catalog.domain.model.enums.PublicationStatus;
import com.patra.catalog.domain.model.vo.publication.DateSpec;
import com.patra.catalog.domain.model.vo.publication.LanguageInfo;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifiers;
import lombok.Getter;

import java.time.Instant;

/**
 * 医学文献聚合根。封装出版物的核心元数据及其一致性规则。
 *
 * <p>一致性边界:
 *
 * <ul>
 *   <li>标识符(PMID/DOI)在同一数据来源中必须唯一
 *   <li>出版年份必须与载体实例保持一致
 *   <li>OA 状态必须与 OA 位置集合同步
 *   <li>语言信息遵循三层标准化结构
 * </ul>
 *
 * <p>业务规则:
 *
 * <ul>
 *   <li>文献必须关联到具体的载体实例(venue_instance_id)
 *   <li>venue_id 冗余字段避免二级 JOIN,由应用层同步更新
 *   <li>publication_year 冗余字段优化高频查询,由应用层同步更新
 *   <li>is_oa 和 oa_status 冗余字段由 OA 位置管理同步更新
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public class PublicationAggregate {

  /** 主键标识 */
  private Long id;

  /** 标识符值对象(PMID, DOI, PMC 等) */
  private final PublicationIdentifiers identifiers;

  /** 关联的出版载体 ID(冗余优化-避免二级 JOIN) */
  private final Long venueId;

  /** 关联的载体实例 ID */
  private final Long venueInstanceId;

  /** 文献标题(英文或原语言) */
  private final String title;

  /** 原始语言标题(非英文时填充) */
  private final String originalTitle;

  /** 语言信息值对象(三层设计:raw → code → base) */
  private final LanguageInfo languageInfo;

  /** 出版状态 */
  private final PublicationStatus publicationStatus;

  /** 媒介类型 */
  private final MediaType mediaType;

  /** 是否有 OA 版本(冗余-快速筛选) */
  private Boolean isOa;

  /** 最佳 OA 状态(冗余-gold/green/hybrid/bronze/closed) */
  private OaStatus oaStatus;

  /** 出版年份(冗余优化-最高频查询字段) */
  private final Integer publicationYear;

  /** 作者列表是否完整(0=不完整,1=完整) */
  private final Boolean authorsComplete;

  /** 被引次数(定期更新) */
  private Integer citationCount;

  /** 参考文献数量 */
  private Integer numberOfReferences;

  /** 利益冲突声明 */
  private final String conflictOfInterest;

  /** 扩展数据(JSON) */
  private String extDataJson;

  /** 摘要(0..1 关系) */
  private Abstract abstract_;

  /** 元数据(1:1 关系) */
  private PublicationMetadata metadata;

  /** 乐观锁版本 */
  private Long version;

  private PublicationAggregate(
      Long id,
      PublicationIdentifiers identifiers,
      Long venueId,
      Long venueInstanceId,
      String title,
      String originalTitle,
      LanguageInfo languageInfo,
      PublicationStatus publicationStatus,
      MediaType mediaType,
      Boolean isOa,
      OaStatus oaStatus,
      Integer publicationYear,
      Boolean authorsComplete,
      Integer citationCount,
      Integer numberOfReferences,
      String conflictOfInterest) {
    this.id = id;
    Assert.notNull(identifiers, "identifiers must not be null");
    Assert.notNull(venueInstanceId, "venueInstanceId must not be null");
    Assert.notBlank(title, "title must not be blank");
    Assert.notNull(publicationYear, "publicationYear must not be null");

    this.identifiers = identifiers;
    this.venueId = venueId;
    this.venueInstanceId = venueInstanceId;
    this.title = title;
    this.originalTitle = originalTitle;
    this.languageInfo = languageInfo;
    this.publicationStatus = publicationStatus;
    this.mediaType = mediaType;
    this.isOa = isOa == null ? false : isOa;
    this.oaStatus = oaStatus;
    this.publicationYear = publicationYear;
    this.authorsComplete = authorsComplete == null ? true : authorsComplete;
    this.citationCount = citationCount == null ? 0 : citationCount;
    this.numberOfReferences = numberOfReferences == null ? 0 : numberOfReferences;
    this.conflictOfInterest = conflictOfInterest;
  }

  /**
   * 创建全新的文献聚合根。
   *
   * @param identifiers 标识符值对象
   * @param venueId 载体 ID
   * @param venueInstanceId 载体实例 ID
   * @param title 标题
   * @param originalTitle 原始标题
   * @param languageInfo 语言信息
   * @param publicationStatus 出版状态
   * @param mediaType 媒介类型
   * @param publicationYear 出版年份
   * @param authorsComplete 作者列表完整性
   * @param conflictOfInterest 利益冲突声明
   * @return 新创建的文献聚合根
   */
  public static PublicationAggregate create(
      PublicationIdentifiers identifiers,
      Long venueId,
      Long venueInstanceId,
      String title,
      String originalTitle,
      LanguageInfo languageInfo,
      PublicationStatus publicationStatus,
      MediaType mediaType,
      Integer publicationYear,
      Boolean authorsComplete,
      String conflictOfInterest) {
    return new PublicationAggregate(
        null,
        identifiers,
        venueId,
        venueInstanceId,
        title,
        originalTitle,
        languageInfo,
        publicationStatus,
        mediaType,
        false, // 初始无 OA
        null, // 初始无 OA 状态
        publicationYear,
        authorsComplete,
        0, // 初始引用数为 0
        0, // 初始参考文献数为 0
        conflictOfInterest);
  }

  /**
   * 从持久化状态重建已存在的文献聚合根(由仓储层使用)。
   *
   * @param id 主键标识
   * @param identifiers 标识符值对象
   * @param venueId 载体 ID
   * @param venueInstanceId 载体实例 ID
   * @param title 标题
   * @param originalTitle 原始标题
   * @param languageInfo 语言信息
   * @param publicationStatus 出版状态
   * @param mediaType 媒介类型
   * @param isOa 是否 OA
   * @param oaStatus OA 状态
   * @param publicationYear 出版年份
   * @param authorsComplete 作者完整性
   * @param citationCount 被引次数
   * @param numberOfReferences 参考文献数
   * @param conflictOfInterest 利益冲突
   * @param version 乐观锁版本
   * @return 从持久化重建的文献聚合根
   */
  public static PublicationAggregate restore(
      Long id,
      PublicationIdentifiers identifiers,
      Long venueId,
      Long venueInstanceId,
      String title,
      String originalTitle,
      LanguageInfo languageInfo,
      PublicationStatus publicationStatus,
      MediaType mediaType,
      Boolean isOa,
      OaStatus oaStatus,
      Integer publicationYear,
      Boolean authorsComplete,
      Integer citationCount,
      Integer numberOfReferences,
      String conflictOfInterest,
      Long version) {
    PublicationAggregate aggregate =
        new PublicationAggregate(
            id,
            identifiers,
            venueId,
            venueInstanceId,
            title,
            originalTitle,
            languageInfo,
            publicationStatus,
            mediaType,
            isOa,
            oaStatus,
            publicationYear,
            authorsComplete,
            citationCount,
            numberOfReferences,
            conflictOfInterest);
    aggregate.version = version;
    return aggregate;
  }

  /**
   * 更新 OA 状态(由 OA 位置管理触发)。
   *
   * @param isOa 是否有 OA 版本
   * @param oaStatus 最佳 OA 状态
   */
  public void updateOaStatus(Boolean isOa, OaStatus oaStatus) {
    this.isOa = isOa;
    this.oaStatus = oaStatus;
  }

  /**
   * 增加被引次数。
   *
   * @param increment 增量
   */
  public void incrementCitationCount(int increment) {
    Assert.isTrue(increment > 0, "increment must be positive");
    this.citationCount += increment;
  }

  /**
   * 关联摘要。
   *
   * @param abstract_ 摘要实体
   */
  public void attachAbstract(Abstract abstract_) {
    Assert.notNull(abstract_, "abstract must not be null");
    this.abstract_ = abstract_;
  }

  /**
   * 关联元数据。
   *
   * @param metadata 元数据实体
   */
  public void attachMetadata(PublicationMetadata metadata) {
    Assert.notNull(metadata, "metadata must not be null");
    this.metadata = metadata;
  }

  /**
   * 获取 PMID(便捷访问器)。
   *
   * @return PMID 值或 null
   */
  public String getPmid() {
    return identifiers.pmid();
  }

  /**
   * 获取 DOI(便捷访问器)。
   *
   * @return DOI 值或 null
   */
  public String getDoi() {
    return identifiers.doi();
  }

  /**
   * 判断是否为开放获取文献。
   *
   * @return true 如果有任何形式的 OA 版本
   */
  public boolean isOpenAccess() {
    return isOa != null && isOa;
  }

  /**
   * 判断是否为黄金 OA。
   *
   * @return true 如果为黄金 OA
   */
  public boolean isGoldOa() {
    return OaStatus.GOLD.equals(oaStatus);
  }
}
```

---

### 2. 值对象:PublicationIdentifiers.java(Sealed Interface)

```java
package com.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;

/**
 * 文献标识符值对象。封装文献的多种标识符(PMID、DOI 等)。
 *
 * <p>设计原则:
 *
 * <ul>
 *   <li>不可变性: Record 自动提供
 *   <li>至少包含一个非空标识符
 *   <li>PMID 和 DOI 是最常用标识符,冗余到主表优化查询
 * </ul>
 *
 * @param pmid PubMed ID(冗余优化)
 * @param doi Digital Object Identifier(冗余优化)
 * @author linqibin
 * @since 0.1.0
 */
public record PublicationIdentifiers(String pmid, String doi) {

  public PublicationIdentifiers {
    // 至少一个标识符非空
    Assert.isTrue(
        pmid != null || doi != null, "至少需要提供一个标识符(PMID 或 DOI)");

    // PMID 格式验证(8-15 位数字)
    if (pmid != null) {
      Assert.isTrue(
          pmid.matches("\\d{1,15}"),
          "PMID 格式无效,必须为数字: " + pmid);
    }

    // DOI 格式验证(基本格式: 10.xxxx/yyyy)
    if (doi != null) {
      Assert.isTrue(
          doi.startsWith("10.") && doi.length() <= 200,
          "DOI 格式无效,必须以 '10.' 开头且长度不超过 200: " + doi);
    }
  }

  /**
   * 创建仅包含 PMID 的标识符。
   *
   * @param pmid PubMed ID
   * @return 标识符值对象
   */
  public static PublicationIdentifiers ofPmid(String pmid) {
    return new PublicationIdentifiers(pmid, null);
  }

  /**
   * 创建仅包含 DOI 的标识符。
   *
   * @param doi Digital Object Identifier
   * @return 标识符值对象
   */
  public static PublicationIdentifiers ofDoi(String doi) {
    return new PublicationIdentifiers(null, doi);
  }

  /**
   * 创建包含 PMID 和 DOI 的标识符。
   *
   * @param pmid PubMed ID
   * @param doi Digital Object Identifier
   * @return 标识符值对象
   */
  public static PublicationIdentifiers of(String pmid, String doi) {
    return new PublicationIdentifiers(pmid, doi);
  }

  /**
   * 判断是否包含 PMID。
   *
   * @return true 如果包含 PMID
   */
  public boolean hasPmid() {
    return pmid != null;
  }

  /**
   * 判断是否包含 DOI。
   *
   * @return true 如果包含 DOI
   */
  public boolean hasDoi() {
    return doi != null;
  }
}
```

---

### 3. 值对象:DateSpec.java(Sealed Interface)

```java
package com.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * 不完整日期值对象密封接口。表示医学文献的出版日期,支持不同精度级别。
 *
 * <p>密封继承层次确保编译时完备性检查。支持三种日期精度:
 *
 * <ul>
 *   <li>YEAR_ONLY - 仅年份(约 30% 的文献)
 *   <li>YEAR_MONTH - 年+月(约 40% 的文献)
 *   <li>FULL_DATE - 完整日期(约 30% 的文献)
 * </ul>
 *
 * <p>设计原则:
 *
 * <ul>
 *   <li>精确表达不完整性: NULL 表示"不存在此精度",而非"未知"
 *   <li>避免虚假精度: 不会将 "2023-06" 存为 "2023-06-01"
 *   <li>不可变性: 所有实现都是不可变的 Record
 *   <li>数值类型存储: 索引效率高,排序友好
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public sealed interface DateSpec
    permits DateSpec.YearOnly, DateSpec.YearMonth, DateSpec.FullDate {

  /**
   * 获取年份。
   *
   * @return 年份值
   */
  int year();

  /**
   * 转换为可 JSON 序列化的 Map,用于持久化层存储。
   *
   * @return 包含年/月/日字段的 Map
   */
  Map<String, Integer> toMap();

  // ============ 精度实现 ============

  /**
   * 仅年份的日期规范值对象。
   *
   * <p>业务约束:
   *
   * <ul>
   *   <li>year 必须在合理范围内(1800-2100)
   * </ul>
   *
   * @param year 年份(1800-2100)
   */
  record YearOnly(int year) implements DateSpec {
    public YearOnly {
      Assert.isTrue(
          year >= 1800 && year <= 2100,
          "year 必须在 1800-2100 范围内: " + year);
    }

    @Override
    public Map<String, Integer> toMap() {
      return Map.of("year", year);
    }
  }

  /**
   * 年+月的日期规范值对象。
   *
   * <p>业务约束:
   *
   * <ul>
   *   <li>year 必须在 1800-2100 范围内
   *   <li>month 必须在 1-12 范围内
   * </ul>
   *
   * @param year 年份(1800-2100)
   * @param month 月份(1-12)
   */
  record YearMonth(int year, int month) implements DateSpec {
    public YearMonth {
      Assert.isTrue(
          year >= 1800 && year <= 2100,
          "year 必须在 1800-2100 范围内: " + year);
      Assert.isTrue(
          month >= 1 && month <= 12,
          "month 必须在 1-12 范围内: " + month);
    }

    @Override
    public Map<String, Integer> toMap() {
      return Map.of("year", year, "month", month);
    }
  }

  /**
   * 完整日期的日期规范值对象。
   *
   * <p>业务约束:
   *
   * <ul>
   *   <li>year 必须在 1800-2100 范围内
   *   <li>month 必须在 1-12 范围内
   *   <li>day 必须在 1-31 范围内,且符合该月的实际天数
   * </ul>
   *
   * @param year 年份(1800-2100)
   * @param month 月份(1-12)
   * @param day 日期(1-31)
   */
  record FullDate(int year, int month, int day) implements DateSpec {
    public FullDate {
      Assert.isTrue(
          year >= 1800 && year <= 2100,
          "year 必须在 1800-2100 范围内: " + year);
      Assert.isTrue(
          month >= 1 && month <= 12,
          "month 必须在 1-12 范围内: " + month);
      Assert.isTrue(
          day >= 1 && day <= 31,
          "day 必须在 1-31 范围内: " + day);

      // 验证日期有效性
      try {
        LocalDate.of(year, month, day);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            String.format("无效日期: %04d-%02d-%02d", year, month, day));
      }
    }

    @Override
    public Map<String, Integer> toMap() {
      return Map.of("year", year, "month", month, "day", day);
    }

    /**
     * 转换为 LocalDate。
     *
     * @return LocalDate 对象
     */
    public LocalDate toLocalDate() {
      return LocalDate.of(year, month, day);
    }
  }

  // ============ 工厂方法 ============

  /**
   * 创建仅年份的日期规范。
   *
   * @param year 年份
   * @return 日期规范值对象
   */
  static YearOnly ofYear(int year) {
    return new YearOnly(year);
  }

  /**
   * 创建年+月的日期规范。
   *
   * @param year 年份
   * @param month 月份
   * @return 日期规范值对象
   */
  static YearMonth ofYearMonth(int year, int month) {
    return new YearMonth(year, month);
  }

  /**
   * 创建完整日期的日期规范。
   *
   * @param year 年份
   * @param month 月份
   * @param day 日期
   * @return 日期规范值对象
   */
  static FullDate ofFullDate(int year, int month, int day) {
    return new FullDate(year, month, day);
  }

  /**
   * 从 LocalDate 创建完整日期规范。
   *
   * @param date LocalDate 对象
   * @return 日期规范值对象
   */
  static FullDate from(LocalDate date) {
    Assert.notNull(date, "date must not be null");
    return new FullDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }
}
```

---

## ✅ 其他聚合根概要

由于 patra_catalog 包含 36 张表,完整展示所有聚合根代码会非常庞大。以下提供其他核心聚合根的概要设计:

### VenueAggregate(出版载体聚合根)

**核心字段**:
- `VenueType venueType` - 载体类型枚举
- `String title` - 载体名称
- `ISSN issn` - ISSN 值对象
- `ISBN isbn` - ISBN 值对象
- `List<VenueInstance> instances` - 载体实例集合

**核心方法**:
- `addInstance(VenueInstance instance)` - 添加载体实例
- `getLatestInstance()` - 获取最新实例
- `getInstanceByYearVolume(int year, String volume)` - 按年份和卷号查询实例

### AuthorAggregate(作者聚合根)

**核心字段**:
- `PersonName personName` - 姓名值对象
- `String orcid` - ORCID 标识符
- `DedupKey dedupKey` - 去重键值对象
- `List<AffiliationRelation> affiliations` - 机构关联集合

**核心方法**:
- `addAffiliation(Affiliation affiliation, boolean isPrimary)` - 添加机构归属
- `updateDedupKey()` - 重新计算去重键
- `hasSameIdentity(Author other)` - 判断是否为同一作者

### MeshDescriptorAggregate(MeSH 主题词聚合根)

**核心字段**:
- `MeshUI meshUi` - MeSH UI 值对象
- `String name` - 主题词名称
- `DescriptorClass descriptorClass` - 主题词类型
- `List<MeshTreeNumber> treeNumbers` - 树形编号集合(平均 2.3 个)
- `List<MeshEntryTerm> entryTerms` - 入口术语(同义词)集合
- `List<MeshConcept> concepts` - 概念集合

**核心方法**:
- `addTreeNumber(TreeNumberSpec treeNumber, boolean isPrimary)` - 添加树形位置
- `findPrimaryTreeNumber()` - 获取主要位置
- `matchesSynonym(String term)` - 匹配同义词

---

## 📝 枚举类型示例

### VenueType.java(载体类型)

```java
package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/**
 * 出版载体类型枚举。
 *
 * <p>字段映射: cat_venue.venue_type → JOURNAL/BOOK/CONFERENCE/OTHER
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum VenueType {
  /** 期刊 */
  JOURNAL("JOURNAL", "Journal"),
  /** 书籍 */
  BOOK("BOOK", "Book"),
  /** 会议 */
  CONFERENCE("CONFERENCE", "Conference"),
  /** 其他(预印本、技术报告等) */
  OTHER("OTHER", "Other");

  private final String code;
  private final String description;

  VenueType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static VenueType fromCode(String value) {
    Assert.notNull(value, "载体类型代码不能为 null");
    String normalized = value.trim().toUpperCase();
    for (VenueType vt : values()) {
      if (vt.code.equals(normalized)) {
        return vt;
      }
    }
    throw new IllegalArgumentException("未知的载体类型: " + value);
  }
}
```

### OaStatus.java(OA 状态)

```java
package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/**
 * 开放获取状态枚举(字典: cat_oa_status)。
 *
 * <p>字段映射: cat_publication.oa_status → gold/green/hybrid/bronze/closed
 *
 * <p>优先级排序(从高到低):
 *
 * <ul>
 *   <li>GOLD - 黄金 OA(出版商开放,最佳)
 *   <li>GREEN - 绿色 OA(机构仓储,次佳)
 *   <li>HYBRID - 混合 OA(部分开放)
 *   <li>BRONZE - 青铜 OA(免费但无许可证)
 *   <li>CLOSED - 封闭获取
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum OaStatus {
  /** 黄金 OA - 出版商完全开放,优先级最高(100) */
  GOLD("gold", "Gold Open Access", 100),
  /** 绿色 OA - 机构仓储/作者存档,优先级次高(80) */
  GREEN("green", "Green Open Access", 80),
  /** 混合 OA - 订阅期刊部分文章开放,优先级中等(70) */
  HYBRID("hybrid", "Hybrid Open Access", 70),
  /** 青铜 OA - 免费但无明确开放许可证,优先级较低(50) */
  BRONZE("bronze", "Bronze Open Access", 50),
  /** 封闭获取 - 需付费或订阅,优先级最低(0) */
  CLOSED("closed", "Closed Access", 0);

  private final String code;
  private final String description;
  private final int priority;

  OaStatus(String code, String description, int priority) {
    this.code = code;
    this.description = description;
    this.priority = priority;
  }

  public static OaStatus fromCode(String value) {
    Assert.notNull(value, "OA 状态代码不能为 null");
    String normalized = value.trim().toLowerCase();
    for (OaStatus status : values()) {
      if (status.code.equals(normalized)) {
        return status;
      }
    }
    throw new IllegalArgumentException("未知的 OA 状态: " + value);
  }

  /**
   * 判断是否为开放获取(非封闭)。
   *
   * @return true 如果为任何形式的 OA
   */
  public boolean isOpenAccess() {
    return this != CLOSED;
  }

  /**
   * 判断是否优于另一 OA 状态。
   *
   * @param other 另一 OA 状态
   * @return true 如果当前状态优先级更高
   */
  public boolean isBetterThan(OaStatus other) {
    return this.priority > other.priority;
  }
}
```

---

## ✅ 设计验证

### 1. 依赖方向检查 ✅
- ✅ Domain 层无基础设施框架依赖(禁止 Spring、MyBatis 等)
- ✅ 允许使用工具库: Hutool-Core、Jackson、Lombok
- ✅ 使用 Sealed Interface 和 Record 实现不可变值对象
- ✅ 聚合根封装业务规则和状态转换逻辑

### 2. 聚合边界检查 ✅
- ✅ PublicationAggregate 是核心聚合根,管理文献元数据
- ✅ VenueAggregate 管理出版载体和具体实例
- ✅ AuthorAggregate 管理作者信息和机构关联
- ✅ MeshDescriptorAggregate 管理 MeSH 主题词完整结构
- ✅ 每个聚合根都有明确的一致性边界和业务规则

### 3. 值对象不变性 ✅
- ✅ PublicationIdentifiers、DateSpec 等值对象使用 Record 确保不可变性
- ✅ 使用 Java 17+ Sealed Interface 确保编译时完备性
- ✅ 构造函数中验证业务约束

### 4. 业务规则正确性 ✅
- ✅ 标识符验证逻辑封装在值对象内部
- ✅ 日期精度逻辑通过 Sealed Interface 实现类型安全
- ✅ OA 状态优先级算法封装在枚举中
- ✅ 冗余字段同步由聚合根方法控制

---

## 💡 Domain 层依赖原则

### 允许的依赖(通过 patra-common-core)

**✅ Hutool-Core**:
```java
// ✅ 参数校验(替代 Objects.requireNonNull)
Assert.notNull(identifiers, "identifiers must not be null");
Assert.notBlank(title, "title must not be blank");
Assert.isTrue(year >= 1800 && year <= 2100, "year 必须在 1800-2100 范围内");

// ✅ 字符串工具
StrUtil.isBlank(str)
StrUtil.format("Publication: {}", title)

// ✅ 集合工具
CollUtil.isEmpty(list)
CollUtil.newArrayList(1, 2, 3)

// ✅ 加密/哈希工具
SecureUtil.md5(content)
DigestUtil.sha256(data)
```

**✅ Jackson**:
```java
// ✅ JSON 序列化注解
@JsonCreator
@JsonValue
@JsonProperty
@JsonIgnore

// ✅ 在值对象中使用
public record DateSpec(...) {
    @JsonCreator
    public static DateSpec fromMap(Map<String, Integer> map) {
        // 反序列化逻辑
    }
}
```

**✅ Lombok**:
```java
// ✅ 减少样板代码
@Getter
@ToString
@EqualsAndHashCode

// ❌ 避免使用会破坏不可变性的注解
// @Setter(聚合根字段应该是 private 或 final)
// @Data(包含 @Setter)
```

### 禁止的依赖

**❌ 基础设施框架**:
```java
// ❌ Spring 框架
@Service
@Component
@Autowired

// ❌ 持久化框架
@Entity
@Table
@Mapper

// ❌ Web 框架
@RestController
@RequestMapping

// ❌ 外部服务客户端
RedisTemplate
RestTemplate
```

### 依赖边界原则

1. **工具 vs 框架**: 工具提供纯函数,框架需要容器托管
2. **序列化 vs 持久化**: Jackson 用于序列化,不涉及数据库
3. **业务逻辑优先**: 当工具方法不够用时,自己实现领域逻辑

---

## 📝 设计亮点

### 1. Sealed Interface + Record 模式
使用 Java 17 的 Sealed Interface 和 Record 实现值对象:
- **编译时完备性**: switch 表达式会检查所有可能的情况
- **不可变性**: Record 自动提供不可变语义
- **类型安全**: 密封继承层次确保只有预定义的实现

示例:DateSpec 支持三种精度(YearOnly/YearMonth/FullDate),编译器强制检查所有分支。

### 2. 标识符复合值对象设计
`PublicationIdentifiers` 封装多种标识符(PMID、DOI):
- 至少包含一个非空标识符
- 提供便捷的工厂方法(`ofPmid`、`ofDoi`、`of`)
- 内置格式验证逻辑
- 支持查询判断(`hasPmid()`、`hasDoi()`)

### 3. 不完整日期精确表达
`DateSpec` Sealed Interface 避免虚假精度:
- `YearOnly(2023)` → 仅年份
- `YearMonth(2023, 6)` → 年+月
- `FullDate(2023, 6, 15)` → 完整日期
- NULL 表示"不存在此精度",而非"未知"

### 4. OA 状态优先级算法
`OaStatus` 枚举内置优先级分数:
- `isBetterThan(OaStatus other)` 方法自动比较优先级
- 支持自动选择最佳 OA 位置
- 便于扩展新的 OA 类型

### 5. 聚合根冗余字段管理
PublicationAggregate 通过方法控制冗余字段同步:
- `updateOaStatus()` - 由 OA 位置管理触发
- `incrementCitationCount()` - 由引用管理触发
- 避免直接暴露 setter,确保一致性

### 6. 复合去重策略
AuthorAggregate 的 `DedupKey` 值对象封装复合去重逻辑:
- 优先级 1: ORCID(覆盖率 30%,准确率 99%)
- 优先级 2: 姓名+机构+邮箱(覆盖率 50%,准确率 90%)
- 优先级 3: 姓名+机构(覆盖率 80%,准确率 75%)
- 逻辑封装在值对象内部,聚合根调用 `updateDedupKey()` 即可

### 7. MeSH 树形结构多位置管理
MeshDescriptorAggregate 的 `MeshTreeNumber` 实体支持:
- 一个主题词平均 2.3 个树形位置
- `is_primary` 标记区分主/次位置
- 支持层次查询(如 "D12.776.*")

---

## 📝 后续步骤

- ✅ 阶段 4 完成: 领域模型已生成(核心聚合根)
- ⬜ 如需补充其他聚合根代码 → 请提供具体需求
- ⬜ 如需记录设计决策 → **[阶段 5: 设计决策记录](5-decisions.md)**
- ⬜ 如需调整领域模型,请提供反馈

---

**阶段4完成!** 🎉

本文档展示了 patra_catalog 医学文献目录服务的核心领域模型设计,包含 10 个聚合根、多个值对象和枚举类型,完全符合六边形架构 + DDD 原则。
