# Patra 项目领域建模模式

本文档描述 Patra 项目中的领域建模约定和最佳实践。

## 聚合根模式

### 基础结构

所有聚合根继承自 `AggregateRoot<Long>`：

```java
@Getter
public class XxxAggregate extends AggregateRoot<Long> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 核心属性（final，构造时必填） ==========
  private final String requiredField;

  // ========== 可选属性 ==========
  private String optionalField;

  // ========== 聚合内实体集合 ==========
  private final List<ChildEntity> children;

  // ========== 私有构造函数 ==========
  private XxxAggregate(Long id, String requiredField) {
    super(id);
    Assert.notBlank(requiredField, "必填字段不能为空");
    this.requiredField = requiredField;
    this.children = new ArrayList<>();
  }

  // ========== 工厂方法 ==========
  // ========== 属性设置方法（链式调用） ==========
  // ========== 聚合内实体管理方法 ==========
  // ========== 便捷判断方法 ==========
  // ========== 不变量验证 ==========
}
```

### 工厂方法约定

```java
// 1. 基础创建方法
public static XxxAggregate create(RequiredParam param) {
  return new XxxAggregate(null, param.value());
}

// 2. 从特定数据源创建
public static XxxAggregate fromOpenAlex(String openalexId, ...) {
  XxxAggregate aggregate = new XxxAggregate(null, ...);
  aggregate.openalexId = openalexId;
  aggregate.provenance = ProvenanceInfo.ofCode(ProvenanceInfo.CODE_OPENALEX);
  return aggregate;
}

public static XxxAggregate fromPubMed(String nlmId, ...) {
  // 类似模式
}

// 3. 从持久化重建（Repository 专用）
public static XxxAggregate restore(
    Long id,
    // ... 所有字段
    Long version) {
  XxxAggregate aggregate = new XxxAggregate(id, ...);
  // 设置所有字段
  aggregate.assignVersion(version);
  return aggregate;
}
```

### 链式设置方法

```java
public XxxAggregate withOptionalField(String value) {
  this.optionalField = value;
  return this;
}

// 使用示例
VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, type, name)
    .withIssnL(issnL)
    .withPublisher(publisher)
    .withCountryCode(countryCode);
```

### 聚合内实体管理

```java
// 添加（带去重检查）
public void addChild(ChildEntity child) {
  Assert.notNull(child, "子实体不能为空");
  boolean exists = children.stream()
      .anyMatch(c -> c.getKey().equals(child.getKey()));
  if (!exists) {
    children.add(child);
  }
}

// 批量添加
public XxxAggregate addChildren(List<ChildEntity> list) {
  if (list != null && !list.isEmpty()) {
    list.forEach(this::addChild);
  }
  return this;
}

// 移除
public void removeChild(String key) {
  children.removeIf(c -> c.getKey().equals(key));
}

// 获取（不可变视图）
public List<ChildEntity> getChildren() {
  return Collections.unmodifiableList(children);
}

// 批量设置（清空后添加）
public void setChildren(List<ChildEntity> newChildren) {
  children.clear();
  if (newChildren != null) {
    newChildren.forEach(this::addChild);
  }
}
```

### 不变量验证

```java
@Override
protected void assertInvariants() {
  if (requiredField == null) {
    throw new IllegalStateException("必填字段不能为空");
  }

  // 业务规则验证
  if (isFromOpenAlex() && StrUtil.isBlank(openalexId)) {
    throw new IllegalStateException("来自 OpenAlex 的实体必须有 OpenAlex ID");
  }

  // 集合约束
  if (treeNumbers.isEmpty()) {
    throw new IllegalStateException("主题词必须至少有一个树形编号");
  }
}
```

## 值对象模式

### 使用 record（推荐）

```java
/// 出版历史值对象。
///
/// @param firstPublishedYear 首次出版年份
/// @param lastPublishedYear 最后出版年份（null 表示仍在出版）
public record PublicationHistory(
    Integer firstPublishedYear,
    Integer lastPublishedYear
) {
  /// 是否已停刊。
  public boolean ceased() {
    return lastPublishedYear != null;
  }

  /// 是否仍在出版。
  public boolean isActive() {
    return lastPublishedYear == null;
  }
}
```

### 带验证的值对象

```java
/// MeSH 唯一标识符值对象。
///
/// @param ui UI 字符串（格式：D/Q/C + 6位数字）
public record MeshUI(String ui) {

  public MeshUI {
    Assert.notBlank(ui, "MeSH UI 不能为空");
    Assert.isTrue(
        ui.matches("^[DQC]\\d{6}$"),
        "MeSH UI 格式无效：%s", ui);
  }

  public boolean isDescriptor() {
    return ui.startsWith("D");
  }

  public boolean isQualifier() {
    return ui.startsWith("Q");
  }
}
```

### 复杂值对象（class）

```java
@Getter
public class IndexingInfo {
  private final boolean isCurrentlyIndexed;
  private final LocalDate indexingStartDate;
  private final LocalDate indexingEndDate;
  private final String citationSubset;

  private IndexingInfo(boolean isCurrentlyIndexed, ...) {
    this.isCurrentlyIndexed = isCurrentlyIndexed;
    // ...
  }

  // 工厂方法
  public static IndexingInfo currentlyIndexed(LocalDate startDate) {
    return new IndexingInfo(true, startDate, null, null);
  }

  public static IndexingInfo notIndexed() {
    return new IndexingInfo(false, null, null, null);
  }
}
```

## 实体模式

聚合内实体有独立标识，但生命周期依赖于聚合根。

```java
@Getter
public class VenueIdentifier {

  private Long id;  // 数据库 ID（可选）
  private final VenueIdentifierType type;
  private final String value;
  private boolean isPrimary;

  private VenueIdentifier(VenueIdentifierType type, String value, boolean isPrimary) {
    Assert.notNull(type, "标识符类型不能为空");
    Assert.notBlank(value, "标识符值不能为空");
    this.type = type;
    this.value = value;
    this.isPrimary = isPrimary;
  }

  // 工厂方法
  public static VenueIdentifier create(VenueIdentifierType type, String value, boolean isPrimary) {
    return new VenueIdentifier(type, value, isPrimary);
  }

  // 便捷工厂方法
  public static VenueIdentifier forOpenAlex(String openalexId) {
    return new VenueIdentifier(VenueIdentifierType.OPENALEX, openalexId, true);
  }

  public static VenueIdentifier forIssnL(String issnL) {
    return new VenueIdentifier(VenueIdentifierType.ISSN_L, issnL, true);
  }

  // 业务方法
  public void markAsPrimary() {
    this.isPrimary = true;
  }

  public void unmarkAsPrimary() {
    this.isPrimary = false;
  }
}
```

## 枚举模式

### 带编码的枚举

```java
@Getter
@AllArgsConstructor
public enum VenueType {
  JOURNAL("journal", "学术期刊"),
  REPOSITORY("repository", "预印本仓库"),
  CONFERENCE("conference", "学术会议"),
  EBOOK_PLATFORM("ebook platform", "电子书平台"),
  BOOK_SERIES("book series", "图书系列"),
  OTHER("other", "其他");

  private final String code;
  private final String description;

  // 从编码解析
  public static VenueType fromCode(String code) {
    for (VenueType type : values()) {
      if (type.code.equalsIgnoreCase(code)) {
        return type;
      }
    }
    return OTHER;
  }

  // 便捷判断方法
  public boolean isJournal() {
    return this == JOURNAL;
  }

  public boolean isRepository() {
    return this == REPOSITORY;
  }
}
```

## Repository 接口模式

```java
/// 载体仓储接口。
public interface VenueRepository {

  /// 保存载体（新增或更新）。
  void save(VenueAggregate venue);

  /// 根据 ID 查询。
  Optional<VenueAggregate> findById(Long id);

  /// 根据 OpenAlex ID 查询。
  Optional<VenueAggregate> findByOpenalexId(String openalexId);

  /// 根据 ISSN-L 查询。
  Optional<VenueAggregate> findByIssnL(String issnL);

  /// 批量保存。
  void saveAll(List<VenueAggregate> venues);

  /// 检查是否存在。
  boolean existsByOpenalexId(String openalexId);
}
```

## 数据来源追踪

### ProvenanceInfo 值对象

```java
@Getter
public class ProvenanceInfo {
  public static final String CODE_OPENALEX = "openalex";
  public static final String CODE_PUBMED = "pubmed";
  public static final String CODE_CROSSREF = "crossref";

  private final String sourceCode;
  private final LocalDate sourceCreatedDate;
  private final LocalDate sourceUpdatedDate;

  public static ProvenanceInfo ofCode(String code) {
    return new ProvenanceInfo(code, null, null);
  }

  public static ProvenanceInfo forOpenAlex(LocalDate created, LocalDate updated) {
    return new ProvenanceInfo(CODE_OPENALEX, created, updated);
  }

  public boolean isFromOpenAlex() {
    return CODE_OPENALEX.equals(sourceCode);
  }

  public boolean isFromPubMed() {
    return CODE_PUBMED.equals(sourceCode);
  }
}
```

## 现有聚合根索引

| 聚合根 | 模块 | 说明 |
|--------|------|------|
| `VenueAggregate` | catalog-domain | 出版载体（期刊/仓库/会议） |
| `PublicationAggregate` | catalog-domain | 学术文献 |
| `AuthorAggregate` | catalog-domain | 作者 |
| `OrganizationAggregate` | catalog-domain | 研究机构 |
| `MeshDescriptorAggregate` | catalog-domain | MeSH 描述符 |
| `MeshQualifierAggregate` | catalog-domain | MeSH 限定词 |
| `PlanAggregate` | ingest-domain | 采集计划 |
| `TaskAggregate` | ingest-domain | 采集任务 |
| `ProvenanceConfiguration` | registry-domain | 数据源配置 |
