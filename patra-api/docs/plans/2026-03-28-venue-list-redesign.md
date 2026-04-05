# 期刊列表页改造 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 改造期刊列表页，展示学术评价指标（分区、h-index、OA 状态等），默认按 h-index 降序排列，替代当前仅展示标识符的简陋布局。

**Architecture:** 全链路改造 — Domain ReadModel 新增字段 → Infra MapStruct 提取 JSON 嵌套字段 → DAO 原生查询按 h-index 排序 → API Response 更新 → 前端卡片重新设计。数据全部来自 VenueEntity 已有的 JSON 列（citationMetrics、letPubData、openAccess），无需新增数据库列或 Flyway 迁移。

**Tech Stack:** Java 25, Spring Data JPA (native query), MapStruct, MySQL 8 JSON functions, Nuxt 4, @nuxt/ui, Tailwind CSS 4, Vue 3

---

## Task 1: 更新 VenueSummaryReadModel

**Files:**
- Modify: `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/read/venue/VenueSummaryReadModel.java`

**变更说明：**

新增 8 个字段（来自 VenueEntity 的 JSON 列），移除 3 个列表页不需要的字段：

| 新增字段 | 类型 | 来源 |
|---------|------|------|
| imageUrl | String | VenueEntity.imageUrl |
| hIndex | Integer | citationMetrics.hIndex |
| jifQuartile | String | letPubData.jifQuartile |
| casMajorQuartile | String | letPubData.casMajorQuartile |
| casTopJournal | Boolean | letPubData.casTopJournal |
| warningListStatus | String | letPubData.warningListStatus |
| isOa | Boolean | openAccess.isOa |
| researchDirection | String | letPubData.researchDirection |

移除字段：`issnL`、`nlmId`、`lastSyncedAt`（详情页已有，列表页不需要）

保留字段：`id`、`title`、`titleZh`、`countryCode`

**Step 1: 更新 VenueSummaryReadModel**

```java
/// 期刊列表摘要读模型。
///
/// 用于期刊列表页展示，包含学术评价指标摘要。
/// 数据来源：VenueEntity 的核心字段 + citationMetrics/letPubData/openAccess JSON 列。
///
/// @author linqibin
/// @since 0.1.0
@Builder
public record VenueSummaryReadModel(
    Long id,
    String title,
    @Nullable String titleZh,
    @Nullable String countryCode,
    @Nullable String imageUrl,
    @Nullable Integer hIndex,
    @Nullable String jifQuartile,
    @Nullable String casMajorQuartile,
    @Nullable Boolean casTopJournal,
    @Nullable String warningListStatus,
    @Nullable Boolean isOa,
    @Nullable String researchDirection) {

  public VenueSummaryReadModel {
    Objects.requireNonNull(id, "id must not be null");
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
  }
}
```

注意：字段数 ≥ 5，使用 `@Builder` 替代 `of()` 工厂方法（符合 Record 类设计规范）。

**Step 2: 编译验证**

Run: `./gradlew :patra-catalog:patra-catalog-domain:compileJava`
Expected: 编译失败 — VenueReadModelMapper、VenueApiConverter 等引用了旧字段

---

## Task 2: 更新 VenueItemResponse（API 响应 DTO）

**Files:**
- Modify: `patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/VenueItemResponse.java`

**Step 1: 更新 VenueItemResponse**

与 VenueSummaryReadModel 保持 1:1 映射：

```java
/// 期刊列表项响应。
///
/// @author linqibin
/// @since 0.1.0
@Builder
public record VenueItemResponse(
    Long id,
    String title,
    @Nullable String titleZh,
    @Nullable String countryCode,
    @Nullable String imageUrl,
    @Nullable Integer hIndex,
    @Nullable String jifQuartile,
    @Nullable String casMajorQuartile,
    @Nullable Boolean casTopJournal,
    @Nullable String warningListStatus,
    @Nullable Boolean isOa,
    @Nullable String researchDirection) {}
```

---

## Task 3: 更新 VenueReadModelMapper（MapStruct 嵌套字段提取）

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadModelMapper.java`

**Step 1: 添加嵌套字段映射**

VenueEntity 的 JSON 列（citationMetrics、letPubData、openAccess）是 Hibernate 自动反序列化的 Java 对象，MapStruct 可以直接用点号语法访问嵌套属性：

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VenueReadModelMapper {

  @Mapping(source = "citationMetrics.hIndex", target = "hIndex")
  @Mapping(source = "letPubData.jifQuartile", target = "jifQuartile")
  @Mapping(source = "letPubData.casMajorQuartile", target = "casMajorQuartile")
  @Mapping(source = "letPubData.casTopJournal", target = "casTopJournal")
  @Mapping(source = "letPubData.warningListStatus", target = "warningListStatus")
  @Mapping(source = "letPubData.researchDirection", target = "researchDirection")
  @Mapping(source = "openAccess.isOa", target = "isOa")
  VenueSummaryReadModel toReadModel(VenueEntity entity);

  // toDetailReadModel 保持不变
  VenueDetailReadModel toDetailReadModel(VenueEntity entity);
}
```

MapStruct 对 null 嵌套对象自动生成安全检查（如 `entity.getCitationMetrics() != null ? entity.getCitationMetrics().hIndex() : null`）。

**Step 2: 编译验证**

Run: `./gradlew :patra-catalog:patra-catalog-infra:compileJava`
Expected: PASS（MapStruct 生成的代码应编译通过）

---

## Task 4: 更新 VenueDao 排序（原生查询 + JSON_EXTRACT）

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/dao/VenueDao.java`
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadAdapter.java`

**背景：** h-index 存储在 `citation_metrics` JSON 列中，JPQL 不支持 `JSON_EXTRACT`，需改为 MySQL 原生查询。

**Step 1: 改写 findJournalPage 为原生查询**

```java
@Query(
    value = """
        SELECT * FROM cat_venue v
        WHERE v.venue_type = 'JOURNAL'
          AND (:keyword IS NULL
               OR LOWER(v.title) LIKE LOWER(CONCAT(:keyword, '%'))
               OR LOWER(v.title_zh) LIKE LOWER(CONCAT(:keyword, '%')))
          AND (:countryCode IS NULL OR v.country_code = :countryCode)
          AND (:issnL IS NULL OR v.issn_l = :issnL)
          AND (:nlmId IS NULL OR v.nlm_id = :nlmId)
        ORDER BY COALESCE(CAST(JSON_EXTRACT(v.citation_metrics, '$.hIndex') AS SIGNED), 0) DESC,
                 v.id DESC
        """,
    countQuery = """
        SELECT COUNT(*) FROM cat_venue v
        WHERE v.venue_type = 'JOURNAL'
          AND (:keyword IS NULL
               OR LOWER(v.title) LIKE LOWER(CONCAT(:keyword, '%'))
               OR LOWER(v.title_zh) LIKE LOWER(CONCAT(:keyword, '%')))
          AND (:countryCode IS NULL OR v.country_code = :countryCode)
          AND (:issnL IS NULL OR v.issn_l = :issnL)
          AND (:nlmId IS NULL OR v.nlm_id = :nlmId)
        """,
    nativeQuery = true)
Page<VenueEntity> findJournalPage(
    @Param("keyword") String keyword,
    @Param("countryCode") String countryCode,
    @Param("issnL") String issnL,
    @Param("nlmId") String nlmId,
    Pageable pageable);
```

**Step 2: 修改 VenueReadAdapter 移除排序参数**

排序已内置在原生查询中，Pageable 只需要提供分页参数：

```java
// 改前：
Pageable pageable = PageRequest.of(paging.page() - 1, paging.pageSize(), BaseJpaEntity.DEFAULT_SORT);

// 改后：
Pageable pageable = PageRequest.of(paging.page() - 1, paging.pageSize());
```

**Step 3: 编译验证**

Run: `./gradlew :patra-catalog:patra-catalog-infra:compileJava`
Expected: PASS

---

## Task 5: 更新 VenueApiConverter（MapStruct 适配）

**Files:**
- Modify: `patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/mapper/VenueApiConverter.java`

**Step 1: 确认映射**

由于 VenueSummaryReadModel 和 VenueItemResponse 字段名 1:1 对应，MapStruct 自动映射，无需额外 `@Mapping`。
如果因为移除了旧字段（issnL/nlmId/lastSyncedAt）导致 `unmappedTargetPolicy = ERROR` 报错，只需确认两侧字段一致即可。

**Step 2: 编译验证全链路**

Run: `./gradlew :patra-catalog:patra-catalog-adapter:compileJava`
Expected: PASS

---

## Task 6: 后端全链路编译 + 测试

**Step 1: 全模块编译**

Run: `./gradlew :patra-catalog:patra-catalog-boot:compileJava`
Expected: PASS

**Step 2: 运行现有单元测试**

Run: `./gradlew :patra-catalog:patra-catalog-domain:test :patra-catalog:patra-catalog-app:test`
Expected: 可能有引用旧字段的测试需要更新

**Step 3: 修复受影响的测试**

检查是否有测试构造 VenueSummaryReadModel 时使用了旧的构造器参数，改为使用 Builder。

**Step 4: Commit 后端改动**

```
feat(catalog): 改造期刊列表读模型，新增学术评价指标字段

- VenueSummaryReadModel: 新增 hIndex/jifQuartile/casMajorQuartile/casTopJournal/
  warningListStatus/isOa/researchDirection/imageUrl，移除 issnL/nlmId/lastSyncedAt
- VenueDao: 改为 MySQL 原生查询，按 h-index 降序排列
- VenueReadModelMapper: MapStruct 嵌套字段提取（JSON 列 → 扁平字段）
- VenueItemResponse: 同步更新 API 响应结构
```

---

## Task 7: 前端类型定义更新

**项目：** `../patra-web/`（Nuxt 4 + @nuxt/ui + Tailwind CSS 4 + Vue 3）

**Files:**
- Modify: `../patra-web/app/types/venue.ts`

**Step 1: 更新 VenueItem 类型**

```typescript
export interface VenueItem {
  id: number
  title: string
  titleZh: string | null
  countryCode: string | null
  imageUrl: string | null
  hIndex: number | null
  jifQuartile: string | null        // "Q1" | "Q2" | "Q3" | "Q4"
  casMajorQuartile: string | null   // "1区" | "2区" | "3区" | "4区"
  casTopJournal: boolean | null
  warningListStatus: string | null
  isOa: boolean | null
  researchDirection: string | null
}
```

同步更新 VenueListParams，移除 issnL 和 nlmId 筛选参数（列表页不再需要）。

---

## Task 8: 前端卡片重新设计

**Files:**
- Modify: `../patra-web/app/pages/venues/index.vue`

**设计目标：**

```
┌──────────────────────────────────────────┐
│ [封面图/首字母]  Nature Medicine          │
│                  自然·医学                 │
│                  医学 · 综合               │
│                                          │
│  Q1   CAS 1区   Top          h-index 412 │
│                                          │
│  Gold OA                    ⚠️ 预警       │
└──────────────────────────────────────────┘
```

**Step 1: 改造卡片布局**

使用 @nuxt/ui 组件（UCard、UBadge 等）+ Tailwind CSS：
- 左侧：封面图（imageUrl），无图时用 title 首字母生成占位符（bg-primary 圆角色块）
- 标题行：title + titleZh
- 副标题：researchDirection（学科方向）
- 标签行：jifQuartile（UBadge 彩色）、casMajorQuartile（UBadge）、casTopJournal（"Top" UBadge）
- 指标：h-index 数值显示
- 底部：isOa（"OA" 标签）、warningListStatus（红色预警标签，有值时显示）

**分区标签颜色（UBadge color prop）：**
- Q1 / 1区 → success (green)
- Q2 / 2区 → info (blue)
- Q3 / 3区 → warning (orange)
- Q4 / 4区 → neutral (gray)

**Step 2: 移除旧的筛选项**

移除 ISSN-L 和 NLM ID 筛选输入框。
保留：关键词搜索（UInput）、国家筛选。

**Step 3: 本地验证**

Run: `cd ../patra-web && pnpm dev`
在浏览器中验证列表页展示效果。

**Step 4: Commit 前端改动**

```
feat(web): 重新设计期刊列表卡片，展示学术评价指标

- 卡片展示：封面图、学科方向、JIF/CAS 分区标签、h-index、OA 状态、预警标记
- 移除列表页中 ISSN-L、NLM ID、同步时间等技术字段
- 分区标签按等级着色（Q1 绿色 → Q4 灰色）
```

---

## 实现顺序依赖

```
Task 1 (ReadModel)
    ↓
Task 2 (Response DTO)  ←── 可与 Task 1 同步
    ↓
Task 3 (Mapper)
    ↓
Task 4 (DAO + Adapter)
    ↓
Task 5 (ApiConverter)
    ↓
Task 6 (编译 + 测试 + Commit)
    ↓
Task 7 (前端类型)
    ↓
Task 8 (前端卡片 + Commit)
```
