# Venue Enrichment Post-Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 venue worker-loop 迁移的 macro review 发现的两个真实问题：(1) `VenueSnapshot` 多一次不必要的 PK 查询；(2) Worker 的 `@Transactional(REQUIRES_NEW)` 包住了 HTTP 爬取和 `Thread.sleep`，存在 HikariCP 连接池 starvation 的生产风险。

**Architecture:**
- **Task 1（投影增强）**：`VenueSnapshot` 新增 `existingCoverKey` 字段，两条 JPQL 查询用 `SELECT new VenueSnapshot(...)` 一次性 fetch，彻底删除 `findExistingCoverKey` 方法。
- **Task 2（事务边界收紧）**：引入 `LetPubEnrichmentPersister` / `ScopusEnrichmentPersister` 两个新 bean 专职承载 `@Transactional(REQUIRES_NEW)`，Worker 自己去掉 `@Transactional`，变成纯编排（scraper + cover download + 委托 Persister）。跨 bean 调用触发 Spring AOP 代理，事务只包 DB 写入。

**Tech Stack:** Java 25 / Spring Boot 4.0.1 / Spring Data JPA / Hibernate 7 / MySQL 8.x / JUnit 5 + Mockito / Testcontainers

**Branch & Worktree:**
- **Branch:** `feat/venue-worker-loop`
- **Worktree:** `/Users/linqibin/Desktop/Patra/patra-api-venue-worker-loop`
- **所有文件路径**在下文中都是相对于该 worktree 的

**Prerequisites（执行前必须成立）:**
1. 提交 `bb08335c..fc2bc723` 的 17 个 worker-loop 迁移 commit 已落地
2. 本 session 之前已落地的 simplify 改动（4 处 Minor cleanup + `VenueEnrichRunStats` 合并）已提交到 `feat/venue-worker-loop` 分支
3. `main` 分支上本 plan 文件所在的 `docs/superpowers/plans/` 目录可写（本 plan 应跟 `2026-04-11-venue-enrichment-worker-loop-migration.md` 一起 commit）
4. worktree 的工作目录是干净的（`git -C /Users/linqibin/Desktop/Patra/patra-api-venue-worker-loop status` 无 uncommitted 改动）

---

## Rejected Findings（显式记录不做）

Code review 一共抛出 4 项性能/架构发现，本 plan 只处理 2 项。另外 2 项经复核后判定为误报或 premature optimization，**不做**：

### ❌ 不做 #A — 添加 `(venue_id, year)` 组合索引

**原始 finding:** reviewer 声称 `cat_venue_jcr_rating` / `cat_venue_scopus_rating` 只索引了 `venue_id`，`NOT EXISTS` 子查询 `WHERE j.venue_id = v.id AND j.year = :targetYear` 会在内侧全扫描，建议加 `@Index(columnList = "venue_id, year")`。

**驳回理由:** 两张表都已经有 `uk_venue_year UNIQUE (venue_id, year)` 约束（见 `JcrRatingEntity.java` + `ScopusRatingEntity.java` 的 `@Table(uniqueConstraints = ...)`）。MySQL InnoDB 会把 unique constraint 当作 B-tree 索引使用，对 `WHERE venue_id = ? AND year = ?` 这种最左前缀查询是 index-only seek，无需额外索引。reviewer 的断言基于不完整的 schema 读取。

**副产品发现:** 两张表的 `idx_venue_id` 其实与 `uk_venue_year` 重复（后者的左列就是 `venue_id`），删掉 `idx_venue_id` 是真正的小清理。**本 plan 不处理**，作为独立的 schema 微优化留给未来。

### ❌ 不做 #B — 用 `INSERT ... ON DUPLICATE KEY` 替换 SELECT-before-insert

**原始 finding:** reviewer 指出 `LetPubEnrichmentPersistAdapter` 每个 venue 做 3 次 SELECT-before-insert 去重（`findYearsByVenueId` / `findKeysByVenueId` ×2），建议改为 DB 唯一约束 + upsert，每 venue 省 3 次 round-trip。

**驳回理由:**
1. **收益不足:** 3 次 SELECT 走的是 `uk_venue_year` 的 index-only seek，合计 ~3ms 量级。相比之下 LetPub 单 venue 的爬取时间是 **~10 秒**（rate limit + HTTP + 反爬 backoff），3ms 在噪声以下。
2. **实现成本高:** JPA 原生不支持 bulk `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE`。可选路径都有真实 tradeoff：
   - `@Modifying @Query(nativeQuery=true)` 单行 insert → 10-30 次 round-trip/venue，比现状**更差**
   - `JdbcTemplate.batchUpdate` → 绕过 Hibernate audit，`created_at` / `updated_at` 列管理要手动兜底
   - `Hibernate StatelessSession` → 绕过二级缓存和 listener，生态集成成本高
3. **Premature optimization:** 节省的 wall-time 远小于误引入 audit/bug 的修复成本。

**结论:** 维持现状；若将来 LetPub 的爬取瓶颈消除（改为纯 API 调用），再重新评估本项。

---

## File Structure（本 plan 的修改范围）

### Task 1 新增 / 修改 / 删除（共 7 文件）

| 操作 | 文件 | 职责 |
|------|------|------|
| 修改 | `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/enrichment/VenueSnapshot.java` | record 新增 `existingCoverKey` 字段 + 更新 `of()` factory |
| 修改 | `patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/port/enrichment/VenueSnapshotTest.java` | 更新 3 个测试的构造函数调用 |
| 修改 | `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/VenueEnrichmentReadPort.java` | 删除 `findExistingCoverKey` 方法 + 移除 `java.util.Optional` import |
| 修改 | `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueEnrichmentReadAdapter.java` | 两条 JPQL 扩充 `SELECT` 列；删除 `findExistingCoverKey` 实现 |
| 修改 | `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueEnrichmentReadAdapterIT.java` | 删除 `FindExistingCoverKeyTests` 内嵌类；`persistJournalWithCover` 辅助方法改为配合新 `VenueSnapshot` 投影的断言测试 |
| 修改 | `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorker.java` | `downloadCoverIfNeeded` 读 `venue.existingCoverKey()` 替代 `readPort.findExistingCoverKey(venue.id())`；移除 `readPort` 注入 |
| 修改 | `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorkerTest.java` | 去掉 `@Mock readPort`，通过 fixture 直接传带/不带 coverKey 的 `VenueSnapshot` |

### Task 2 新增 / 修改（共 6 文件）

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentPersister.java` | 新 bean，承载 `@Transactional(REQUIRES_NEW)`，单方法 `persist(venueId, data, coverKey)` 委托 `LetPubEnrichmentPersistPort` |
| 新建 | `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentPersister.java` | 同上，Scopus 版本；单方法 `persist(venueId, data)` |
| 修改 | `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorker.java` | 去掉 `@Transactional`，依赖注入从 `LetPubEnrichmentPersistPort` 换为 `LetPubEnrichmentPersister` |
| 修改 | `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentWorker.java` | 同上 |
| 修改 | `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorkerTest.java` | `@Mock` 从 `persistPort` 换为 `persister` |
| 修改 | `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentWorkerTest.java` | 同上 |
| 新建 | `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentPersisterTest.java` | 新测试：验证 Persister 透传参数给 PersistPort + 透传 PersistStats 返回值 |
| 新建 | `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentPersisterTest.java` | 同上 |

---

## Task 1: 把 existingCoverKey 投影进 VenueSnapshot

**Files:**
- Modify: `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/enrichment/VenueSnapshot.java`
- Modify: `patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/port/enrichment/VenueSnapshotTest.java`
- Modify: `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/VenueEnrichmentReadPort.java`
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueEnrichmentReadAdapter.java`
- Modify: `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueEnrichmentReadAdapterIT.java`
- Modify: `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorker.java`
- Modify: `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorkerTest.java`

- [ ] **Step 1.1: RED — 更新 `VenueSnapshotTest` 断言新字段**

替换整个文件内容为：

```java
package com.patra.catalog.domain.port.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VenueSnapshotTest {

  @Test
  void of_validArguments_createsSnapshot() {
    VenueSnapshot snapshot = VenueSnapshot.of(42L, "1234-5678", "catalog/venue-cover/42.jpg");
    assertThat(snapshot.id()).isEqualTo(42L);
    assertThat(snapshot.issnL()).isEqualTo("1234-5678");
    assertThat(snapshot.existingCoverKey()).isEqualTo("catalog/venue-cover/42.jpg");
  }

  @Test
  void of_nullIssnL_allowedForDebugging() {
    VenueSnapshot snapshot = VenueSnapshot.of(42L, null, null);
    assertThat(snapshot.issnL()).isNull();
    assertThat(snapshot.existingCoverKey()).isNull();
  }

  @Test
  void of_nullCoverKey_allowed() {
    VenueSnapshot snapshot = VenueSnapshot.of(42L, "1234-5678", null);
    assertThat(snapshot.existingCoverKey()).isNull();
  }

  @Test
  void of_nullId_rejected() {
    assertThatThrownBy(() -> VenueSnapshot.of(null, "1234-5678", null))
        .isInstanceOf(NullPointerException.class);
  }
}
```

- [ ] **Step 1.2: RED — 运行测试，预期编译失败**

Run: `./gradlew :patra-catalog:patra-catalog-domain:test --tests 'com.patra.catalog.domain.port.enrichment.VenueSnapshotTest'`
Expected: 编译错误 `method of(Long,String,String) cannot be applied to VenueSnapshot` —— factory 还是 2 参。

- [ ] **Step 1.3: GREEN — 更新 `VenueSnapshot` record**

替换整个文件内容为：

```java
package com.patra.catalog.domain.port.enrichment;

import java.util.Objects;

/// Venue 工作队列读端的轻量投影。
///
/// 用于 [VenueEnrichmentReadPort] 返回"需要富化的 venue"列表，
/// 包含 Runner/Worker 实际需要的字段：
///
/// - `id`：聚合根主键，同时作为 keyset 游标和持久化目标
/// - `issnL`：富化查询键（空 → Worker 跳过）
/// - `existingCoverKey`：venue 当前封面对象键（空 → LetPub Worker 需要下载新封面）
///
/// **为什么在这一层投影封面键**：LetPub 富化流程里，判断"是否需要下载新封面"的
/// 逻辑和"是否需要抓取 venue"属于同一次查询的两个产物——与其在 Worker 里再往
/// `cat_venue` 多发一次 PK 查询读 `image_object_key`，不如一次 JPQL projection
/// 把两者都拿回来。Scopus 管线无封面概念，字段在 Scopus 结果中恒为 null，开销
/// 只是多一列投影，无实际成本。
///
/// @param id 聚合根主键，非 null
/// @param issnL ISSN-L，若缺失则调用方应跳过该 venue
/// @param existingCoverKey venue 当前封面对象键，无封面时为 null
/// @author linqibin
/// @since 0.1.0
public record VenueSnapshot(Long id, String issnL, String existingCoverKey) {

  public VenueSnapshot {
    Objects.requireNonNull(id, "VenueSnapshot.id 不可为 null");
  }

  /// 创建 [VenueSnapshot]。
  ///
  /// @param id 聚合根主键，不可为 null（由紧凑构造器校验）
  /// @param issnL ISSN-L，允许为 null
  /// @param existingCoverKey 封面对象键，允许为 null（无封面 / Scopus 管线）
  /// @return 新建的 [VenueSnapshot] 实例
  public static VenueSnapshot of(Long id, String issnL, String existingCoverKey) {
    return new VenueSnapshot(id, issnL, existingCoverKey);
  }
}
```

- [ ] **Step 1.4: GREEN — 验证 VenueSnapshotTest 通过**

Run: `./gradlew :patra-catalog:patra-catalog-domain:test --tests 'com.patra.catalog.domain.port.enrichment.VenueSnapshotTest' --rerun-tasks`
Expected: PASS 4/4

- [ ] **Step 1.5: RED — 从 `VenueEnrichmentReadPort` 删除 `findExistingCoverKey`**

替换整个文件内容为：

```java
package com.patra.catalog.domain.port.read;

import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import java.util.List;

/// Venue 富化工作队列读端口。
///
/// 为同步 worker loop 提供"待富化 venue"的分页读取，使用 **keyset pagination**
/// （`id > lastId`）配合 `NOT EXISTS` 过滤：
///
/// - `NOT EXISTS` 动态收敛 — 随 Worker 写入评级数据，下一批次的候选集合
///   自动缩减，无需专门的"失败清单"表或重试队列
/// - `lastId` 游标前进 — 避免同一 venue 失败后被无限重捞（同一次 run 内失败
///   的 venue 留待下次 Job 调度时再次进入查询）
///
/// **为什么返回的 [VenueSnapshot] 直接携带 `existingCoverKey`**：
/// LetPub Worker 判断是否跳过封面下载时要读 `cat_venue.image_object_key`，
/// 与其多发一次 PK 查询，不如在同一条工作队列查询里 projection 出来——
/// Scopus 管线不用但字段为 null，开销可忽略。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueEnrichmentReadPort {

  /// 查询缺少指定年份 JCR 评级的期刊 venue。
  ///
  /// @param targetYear 目标年份（对应 `cat_venue_jcr_rating.year`）
  /// @param minCitedByCount 被引次数下限（0 = 不过滤）
  /// @param lastId keyset 游标，传 0 表示从头开始
  /// @param limit 批大小（通常 50）
  /// @return 按 `id` 升序排列的 venue 快照列表；空列表表示没有更多候选
  List<VenueSnapshot> findNeedingLetPubEnrichment(
      short targetYear, int minCitedByCount, long lastId, int limit);

  /// 查询缺少指定年份 Scopus 评级的期刊 venue。
  ///
  /// 与 [findNeedingLetPubEnrichment] 结构一致，但 `NOT EXISTS` 子查询
  /// 指向 `cat_venue_scopus_rating` 表。
  ///
  /// @param targetYear 目标年份（对应 `cat_venue_scopus_rating.year`）
  /// @param minCitedByCount 被引次数下限（0 = 不过滤）
  /// @param lastId keyset 游标，传 0 表示从头开始
  /// @param limit 批大小（通常 50）
  /// @return 按 `id` 升序排列的 venue 快照列表；空列表表示没有更多候选
  List<VenueSnapshot> findNeedingScopusEnrichment(
      short targetYear, int minCitedByCount, long lastId, int limit);
}
```

- [ ] **Step 1.6: GREEN — 更新 `VenueEnrichmentReadAdapter` 的 JPQL 和删除 findExistingCoverKey 实现**

替换整个文件内容为：

```java
package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Component;

/// [VenueEnrichmentReadPort] 的 JPA 实现，使用 keyset pagination + NOT EXISTS。
///
/// **为什么不用 Repository**：
///
/// 本查询是"工作队列读取"而非聚合根加载，不需要拉起完整 VenueEntity。
/// 使用 JPQL 投影直接构造 [VenueSnapshot]，省掉一次对象图装配。
///
/// **NOT EXISTS 的动态收敛特性**：
///
/// 查询过滤 `NOT EXISTS (SELECT 1 FROM JcrRatingEntity WHERE venue_id = v.id AND year = :targetYear)`，
/// 随 Worker 写入数据，下一批次的候选集合自动缩减。配合 `id > :lastId`
/// keyset 游标，确保当次 run 内失败的 venue 不会被无限重捞。
///
/// @author linqibin
/// @since 0.1.0
@Component
public class VenueEnrichmentReadAdapter implements VenueEnrichmentReadPort {

  @PersistenceContext private EntityManager em;

  /// 查询缺少指定年份 JCR 评级的期刊 venue。
  ///
  /// 使用 keyset pagination（`id > :lastId`）结合 `NOT EXISTS` 子查询过滤已有 JCR 评级的 venue。
  /// 仅返回 `venueType = 'JOURNAL'` 且 `issnL` 不为 null 的 venue。同一次 projection 把
  /// `imageObjectKey` 一起带出，让下游 LetPub Worker 免掉一次 PK 查询。
  ///
  /// @param targetYear 目标年份（对应 `cat_venue_jcr_rating.year`），不可为 null
  /// @param minCitedByCount 被引次数下限（0 = 不过滤）
  /// @param lastId keyset 游标，传 0 表示从头开始
  /// @param limit 批大小（通常 50），必须 > 0
  /// @return 按 `id` 升序排列的 venue 快照列表；空列表表示没有更多候选
  @Override
  public List<VenueSnapshot> findNeedingLetPubEnrichment(
      short targetYear, int minCitedByCount, long lastId, int limit) {
    return em.createQuery(
            """
            SELECT new com.patra.catalog.domain.port.enrichment.VenueSnapshot(
                v.id, v.issnL, v.imageObjectKey)
            FROM VenueEntity v
            WHERE v.venueType = 'JOURNAL'
              AND v.issnL IS NOT NULL
              AND v.id > :lastId
              AND NOT EXISTS (
                SELECT 1 FROM JcrRatingEntity j
                WHERE j.venueId = v.id AND j.year = :targetYear
              )
              AND (:minCitedByCount = 0 OR v.citedByCount >= :minCitedByCount)
            ORDER BY v.id ASC
            """,
            VenueSnapshot.class)
        .setParameter("targetYear", targetYear)
        .setParameter("minCitedByCount", minCitedByCount)
        .setParameter("lastId", lastId)
        .setMaxResults(limit)
        .getResultList();
  }

  /// 查询缺少指定年份 Scopus 评级的期刊 venue。
  ///
  /// 与 [findNeedingLetPubEnrichment] 结构一致，但 `NOT EXISTS` 子查询
  /// 指向 `cat_venue_scopus_rating` 表，互不干扰。Scopus 管线不使用 `existingCoverKey`
  /// 字段（恒有值或 null 都无影响），projection 保留是为了与 LetPub 查询共用同一
  /// [VenueSnapshot] record 类型。
  ///
  /// @param targetYear 目标年份（对应 `cat_venue_scopus_rating.year`），不可为 null
  /// @param minCitedByCount 被引次数下限（0 = 不过滤）
  /// @param lastId keyset 游标，传 0 表示从头开始
  /// @param limit 批大小（通常 50），必须 > 0
  /// @return 按 `id` 升序排列的 venue 快照列表；空列表表示没有更多候选
  @Override
  public List<VenueSnapshot> findNeedingScopusEnrichment(
      short targetYear, int minCitedByCount, long lastId, int limit) {
    return em.createQuery(
            """
            SELECT new com.patra.catalog.domain.port.enrichment.VenueSnapshot(
                v.id, v.issnL, v.imageObjectKey)
            FROM VenueEntity v
            WHERE v.venueType = 'JOURNAL'
              AND v.issnL IS NOT NULL
              AND v.id > :lastId
              AND NOT EXISTS (
                SELECT 1 FROM ScopusRatingEntity s
                WHERE s.venueId = v.id AND s.year = :targetYear
              )
              AND (:minCitedByCount = 0 OR v.citedByCount >= :minCitedByCount)
            ORDER BY v.id ASC
            """,
            VenueSnapshot.class)
        .setParameter("targetYear", targetYear)
        .setParameter("minCitedByCount", minCitedByCount)
        .setParameter("lastId", lastId)
        .setMaxResults(limit)
        .getResultList();
  }
}
```

注意：该文件的 `Optional` import 已去掉。

- [ ] **Step 1.7: GREEN — 更新 `VenueEnrichmentReadAdapterIT`**

读当前文件，然后做以下 3 处修改：

(a) 删除整个 `FindExistingCoverKeyTests` 内嵌类（原文件第 153-180 行）以及它的 3 个测试方法。

(b) 在 `FindNeedingLetPubEnrichmentTests` 内嵌类末尾（原文件第 120 行之前，紧接 `respectsLimit` 测试之后）新增一个断言 cover key projection 的测试：

```java
    @Test
    @DisplayName("cover key 投影 - 已存在封面的 venue 在 snapshot 里带回 existingCoverKey")
    void projectsExistingCoverKey() {
      VenueEntity v1 = persistJournalWithCover("8000-0001", "catalog/venue-cover/111.jpg");
      VenueEntity v2 = persistJournal("8000-0002", 0); // 无封面

      List<VenueSnapshot> result = adapter.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50);

      assertThat(result)
          .filteredOn(s -> s.id().equals(v1.getId()))
          .singleElement()
          .extracting(VenueSnapshot::existingCoverKey)
          .isEqualTo("catalog/venue-cover/111.jpg");
      assertThat(result)
          .filteredOn(s -> s.id().equals(v2.getId()))
          .singleElement()
          .extracting(VenueSnapshot::existingCoverKey)
          .isNull();
    }
```

(c) 保留 `persistJournalWithCover` 辅助方法——它现在服务于上面的新测试，不要删。

(d) 更新 Javadoc 头注释：把 `findExistingCoverKey` 的覆盖说明去掉，用"`findNeedingLetPubEnrichment` 的 cover key projection"替代：

```java
/// **覆盖场景**：
/// - `findNeedingLetPubEnrichment`：NOT EXISTS 过滤、keyset 前进、citedByCount 下限、
///   ISSN-L 为空跳过、limit 生效、cover key 投影（6 个测试）
/// - `findNeedingScopusEnrichment`：NOT EXISTS 对接 `cat_venue_scopus_rating` 表（2 个代表性测试，验证关键差异）
```

- [ ] **Step 1.8: GREEN — 更新 `LetPubEnrichmentWorker`**

打开 `LetPubEnrichmentWorker.java`，做以下修改：

(a) 删除 `VenueEnrichmentReadPort` import：
```java
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;   // DELETE
```

(b) 删除构造注入的 `readPort` 字段：
```java
  private final VenueEnrichmentReadPort readPort;   // DELETE
```

(c) 修改 `downloadCoverIfNeeded` 方法签名——把 `long venueId` 换为完整的 `VenueSnapshot venue`，并用 `venue.existingCoverKey()` 替代 `readPort.findExistingCoverKey(venueId)`：

原来的代码：

```java
  private String downloadCoverIfNeeded(long venueId, LetPubVenueData data) {
    Optional<String> existing = readPort.findExistingCoverKey(venueId);
    if (existing.isPresent()) {
      log.debug("Venue [id={}] 已存在封面对象键，跳过下载", venueId);
      return null;
    }
    String sourceUrl = data.basicInfo().coverImageSourceUrl();
    // ... rest unchanged
```

改为：

```java
  private String downloadCoverIfNeeded(VenueSnapshot venue, LetPubVenueData data) {
    if (venue.existingCoverKey() != null) {
      log.debug("Venue [id={}] 已存在封面对象键，跳过下载", venue.id());
      return null;
    }
    String sourceUrl = data.basicInfo().coverImageSourceUrl();
    if (sourceUrl == null || sourceUrl.isBlank()) {
      return null;
    }

    String stableKey = "catalog/venue-cover/" + venue.id() + ".jpg";
    URI sourceUri;
    try {
      sourceUri = URI.create(sourceUrl);
    } catch (IllegalArgumentException e) {
      log.warn("venue 封面 URL 格式非法（继续）: venueId={} sourceUrl={}", venue.id(), sourceUrl);
      return null;
    }
    try {
      return coverImageDownloadPort.downloadAndStore(sourceUri, stableKey);
    } catch (FileDownloadException e) {
      log.warn(
          "venue 封面下载失败（继续）: venueId={} trait={} reason={}",
          venue.id(),
          e.getErrorTraits(),
          e.getMessage());
      return null;
    } catch (RuntimeException e) {
      log.warn("venue 封面下载意外异常（继续）: venueId={}", venue.id(), e);
      return null;
    }
  }
```

注意：`java.util.Optional` 若该文件中只这一处使用，也要删除 import。

(d) 更新 `processVenue` 里对 `downloadCoverIfNeeded` 的调用：

```java
    String coverObjectKey = downloadCoverIfNeeded(venue.id(), data);   // BEFORE
    String coverObjectKey = downloadCoverIfNeeded(venue, data);        // AFTER
```

(e) 更新类级 Javadoc：把"检查 ISSN → 爬取 → 按需下载封面 → 调 persistPort 持久化"里"按需下载封面"的意思已经不变，保留。但删除对 `ReadPort` 的提及（如果有）。

- [ ] **Step 1.9: GREEN — 更新 `LetPubEnrichmentWorkerTest`**

读当前测试文件，做以下修改：

(a) 删除 `@Mock VenueEnrichmentReadPort readPort;` 字段（原文件 line 41）以及 `setUp()` 方法里 `new LetPubEnrichmentWorker(scraperPort, persistPort, coverPort, readPort)` 的第 4 个参数：

```java
// BEFORE
worker = new LetPubEnrichmentWorker(scraperPort, persistPort, coverPort, readPort);
// AFTER
worker = new LetPubEnrichmentWorker(scraperPort, persistPort, coverPort);
```

(b) 删除所有 `when(readPort.findExistingCoverKey(100L)).thenReturn(Optional.empty())` / `.thenReturn(Optional.of("..."))` 的 stub 调用，改为在构造 `VenueSnapshot` fixture 时直接传入 `existingCoverKey` 参数：

示例 - happy path 测试：

```java
// BEFORE
VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678");
LetPubVenueData data = letPubDataWithCoverUrl();
when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.of(data));
when(readPort.findExistingCoverKey(100L)).thenReturn(Optional.empty());
when(coverPort.downloadAndStore(any(), eq("catalog/venue-cover/100.jpg")))
    .thenReturn("catalog/venue-cover/100.jpg");

// AFTER
VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678", null);   // 无 cover key → 走下载路径
LetPubVenueData data = letPubDataWithCoverUrl();
when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.of(data));
when(coverPort.downloadAndStore(any(), eq("catalog/venue-cover/100.jpg")))
    .thenReturn("catalog/venue-cover/100.jpg");
```

对应调整的测试：
- `processVenue_happyPath_returnsPROCESSED` — `VenueSnapshot.of(100L, "1234-5678", null)`
- `processVenue_missingIssn_returnsMISSING_ISSN` — `VenueSnapshot.of(100L, null, null)`
- `processVenue_notFoundOnSource_returnsNOT_FOUND_IN_SOURCE` — `VenueSnapshot.of(100L, "1234-5678", null)`
- `processVenue_coverDownloadFails_continuesWithoutCover` — `VenueSnapshot.of(100L, "1234-5678", null)`
- `processVenue_scraperThrows_propagatesException` — `VenueSnapshot.of(100L, "1234-5678", null)`
- `processVenue_existingCoverKey_skipsDownload` — **关键变化**：`VenueSnapshot.of(100L, "1234-5678", "catalog/venue-cover/100.jpg")`。删除原来的 `when(readPort.findExistingCoverKey(100L)).thenReturn(Optional.of(...))` stub。
- `processVenue_blankCoverUrl_skipsDownload` — `VenueSnapshot.of(100L, "1234-5678", null)`
- `processVenue_persistThrows_propagatesException` — `VenueSnapshot.of(100L, "1234-5678", null)`

(c) 同时把 `VenueEnrichmentReadPort` import 删除。

- [ ] **Step 1.10: GREEN — 全面编译 + 测试验证**

Run: `./gradlew :patra-catalog:patra-catalog-domain:test :patra-catalog:patra-catalog-app:test :patra-catalog:patra-catalog-infra:compileJava :patra-catalog:patra-catalog-infra:compileTestJava --rerun-tasks`
Expected: 全绿，包括 `LetPubEnrichmentWorkerTest` 的 8 个测试和 `VenueSnapshotTest` 的 4 个测试。

infra 的 IT 需要 Testcontainers MySQL，默认 `./gradlew :patra-catalog:patra-catalog-infra:test` 会跑，如果本地环境可用：
Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests 'com.patra.catalog.infra.adapter.read.VenueEnrichmentReadAdapterIT' --rerun-tasks`
Expected: 原有 `findExistingCoverKey` 的 3 个测试不再存在；`findNeedingLetPubEnrichment` 下新增的 `projectsExistingCoverKey` 通过；总共 7 个测试（原 5 个 LetPub + 1 个新增 + 2 个 Scopus - 3 个已删）。

- [ ] **Step 1.11: Commit**

```bash
git -C /Users/linqibin/Desktop/Patra/patra-api-venue-worker-loop add \
  patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/enrichment/VenueSnapshot.java \
  patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/port/enrichment/VenueSnapshotTest.java \
  patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/VenueEnrichmentReadPort.java \
  patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueEnrichmentReadAdapter.java \
  patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueEnrichmentReadAdapterIT.java \
  patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorker.java \
  patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorkerTest.java
git -C /Users/linqibin/Desktop/Patra/patra-api-venue-worker-loop commit -m "refactor(catalog): VenueSnapshot 投影 existingCoverKey 消除 Worker 的多余 PK 查询"
```

---

## Task 2: 把 Worker 的事务边界从 HTTP 调用里剥离

**背景问题**：`LetPubEnrichmentWorker.processVenue` 和 `ScopusEnrichmentWorker.processVenue` 目前都带 `@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)`。该注解覆盖的方法体里第一个动作就是 `scraperPort.findByIssn(...)`——LetPub 是一次完整的反爬 HTTP 请求（内置 `Thread.sleep` backoff），Scopus 虽然 rate limit 在 Runner 里，但 `findByIssn` 也是 HTTP。HikariCP 连接会被占用整段 HTTP 时长，并发运行时（即便单线程 Worker，多 Job 并行）非常容易打满连接池。

**修复方式**：引入 `LetPubEnrichmentPersister` / `ScopusEnrichmentPersister` 两个**专职承载事务边界**的 bean。它们的唯一方法 `persist(...)` 带 `@Transactional(REQUIRES_NEW, rollbackFor = Exception.class)`，委托给现有 `*EnrichmentPersistPort`。Worker 自身去掉 `@Transactional`，`processVenue` 变成非事务的编排方法——scraper HTTP 调用和封面下载都在事务外发生，只有最后的 `persister.persist(...)` 跨 bean 调用触发 Spring AOP 代理进入一个紧凑的 DB-only 事务。

**为什么不直接在 `PersistPort` 接口上加 `@Transactional`**：项目规则 `rules/tech/jpa.md` 明确"只在 Application 层使用 `@Transactional`"。`PersistPort` 的 domain 层定义和 infra 层 `PersistAdapter` 实现都在 domain/infra 层，不是 app 层。Persister bean 是 app 层的事务门面，完全符合架构约束。

**Files:**
- Create: `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentPersister.java`
- Create: `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentPersister.java`
- Modify: `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorker.java`
- Modify: `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentWorker.java`
- Modify: `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorkerTest.java`
- Modify: `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentWorkerTest.java`
- Create: `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentPersisterTest.java`
- Create: `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentPersisterTest.java`

- [ ] **Step 2.1: RED — 写 `LetPubEnrichmentPersisterTest`**

创建 `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentPersisterTest.java`：

```java
package com.patra.catalog.app.usecase.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort.PersistStats;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// LetPubEnrichmentPersister 单元测试。
///
/// Persister 的唯一职责是**承载事务边界**并把调用转发给 PersistPort。
/// 单元测试只验证 1) 参数透传 2) 返回值透传 3) 异常透传。事务边界的实际
/// 生效需要在集成测试里通过真实 Spring 容器验证（超出单元测试范围）。
@DisplayName("LetPubEnrichmentPersister 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class LetPubEnrichmentPersisterTest {

  @Mock LetPubEnrichmentPersistPort persistPort;

  LetPubEnrichmentPersister persister;

  @BeforeEach
  void setUp() {
    persister = new LetPubEnrichmentPersister(persistPort);
  }

  @Test
  @DisplayName("透传 venueId / data / coverKey 参数到 PersistPort")
  void persist_delegatesArgumentsToPort() {
    LetPubVenueData data = LetPubVenueData.empty();
    PersistStats expected = new PersistStats(10, 5, 2, true);
    when(persistPort.persist(100L, data, "catalog/venue-cover/100.jpg")).thenReturn(expected);

    PersistStats actual = persister.persist(100L, data, "catalog/venue-cover/100.jpg");

    assertThat(actual).isSameAs(expected);
    verify(persistPort).persist(100L, data, "catalog/venue-cover/100.jpg");
  }

  @Test
  @DisplayName("null coverKey 也原样透传（跳过封面下载路径）")
  void persist_nullCoverKey_passedThrough() {
    LetPubVenueData data = LetPubVenueData.empty();
    PersistStats expected = new PersistStats(1, 0, 0, false);
    when(persistPort.persist(100L, data, null)).thenReturn(expected);

    PersistStats actual = persister.persist(100L, data, null);

    assertThat(actual).isSameAs(expected);
    verify(persistPort).persist(100L, data, null);
  }

  @Test
  @DisplayName("PersistPort 抛异常 - 向上传播（由事务边界触发回滚）")
  void persist_portThrows_propagates() {
    LetPubVenueData data = LetPubVenueData.empty();
    when(persistPort.persist(100L, data, null))
        .thenThrow(new RuntimeException("db constraint violation"));

    assertThatThrownBy(() -> persister.persist(100L, data, null))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db constraint violation");
  }
}
```

- [ ] **Step 2.2: RED — 运行测试验证 Persister 不存在**

Run: `./gradlew :patra-catalog:patra-catalog-app:compileTestJava`
Expected: 编译错误 `cannot find symbol: class LetPubEnrichmentPersister`

- [ ] **Step 2.3: GREEN — 创建 `LetPubEnrichmentPersister`**

创建 `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentPersister.java`：

```java
package com.patra.catalog.app.usecase.venue.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort.PersistStats;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// LetPub 富化持久化事务边界 bean。
///
/// **唯一职责**：给 [LetPubEnrichmentPersistPort#persist] 的调用包一层
/// `@Transactional(REQUIRES_NEW)` 事务。自身没有任何业务逻辑。
///
/// **为什么需要这个 bean**：[LetPubEnrichmentWorker#processVenue] 要先做
/// HTTP 爬取和封面下载（耗时以秒计），然后写 DB。老实现把 `@Transactional`
/// 放在 `processVenue` 上会让整个耗时窗口都占用 HikariCP 连接，并发下极易
/// 打满连接池。把事务边界下移到 Persister 这个单方法 bean，Worker 内部不
/// 再持有 DB 连接直到调用 `persister.persist(...)` 那一刻。跨 bean 调用
/// 触发 Spring AOP 代理，事务语义与直接标注在 Worker 上等效但作用域更紧凑。
///
/// **为什么不直接在 PersistPort 上加 `@Transactional`**：项目规则
/// `rules/tech/jpa.md` 规定"只在 Application 层使用 `@Transactional`"。
/// PersistPort 的 domain 定义和 infra 实现都不在 app 层。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class LetPubEnrichmentPersister {

  private final LetPubEnrichmentPersistPort persistPort;

  /// 在独立事务里持久化一个 venue 的 LetPub 富化结果。
  ///
  /// 调用方必须已经完成 HTTP 爬取和封面下载——本方法只做 DB 写入。
  /// `REQUIRES_NEW` 确保即便外层调用方已有事务，本次写入也在独立事务里，
  /// 任何 `Exception` 触发本事务回滚但不影响外层。
  ///
  /// @param venueId 目标 venue 主键
  /// @param data LetPub 爬取数据，不为 null
  /// @param coverObjectKey 新下载的封面对象键，若跳过下载则传 null
  /// @return PersistPort 返回的 [PersistStats]，供调用方记录日志
  /// @throws RuntimeException 任何持久化异常都会向上传播并触发事务回滚
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public PersistStats persist(long venueId, LetPubVenueData data, String coverObjectKey) {
    return persistPort.persist(venueId, data, coverObjectKey);
  }
}
```

- [ ] **Step 2.4: GREEN — 运行 `LetPubEnrichmentPersisterTest`**

Run: `./gradlew :patra-catalog:patra-catalog-app:test --tests 'com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentPersisterTest' --rerun-tasks`
Expected: PASS 3/3

- [ ] **Step 2.5: RED — 改 `LetPubEnrichmentWorker`：去掉 `@Transactional`，注入 Persister**

打开 `LetPubEnrichmentWorker.java`（已经在 Task 1 Step 1.8 里被改过一次）。现在做第二轮改动：

(a) 删除事务相关 import：
```java
import org.springframework.transaction.annotation.Propagation;   // DELETE
import org.springframework.transaction.annotation.Transactional; // DELETE
```

(b) 改 `@Service` 保留，但 `processVenue` 上的 `@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)` 整行删除。

(c) 把字段 `private final LetPubEnrichmentPersistPort persistPort;` 换为 `private final LetPubEnrichmentPersister persister;`，同时删除 `LetPubEnrichmentPersistPort` 的 import。

(d) 在 `processVenue` 方法里把：
```java
    LetPubEnrichmentPersistPort.PersistStats stats =
        persistPort.persist(venue.id(), data, coverObjectKey);
```
换为：
```java
    LetPubEnrichmentPersistPort.PersistStats stats =
        persister.persist(venue.id(), data, coverObjectKey);
```
**注意：`PersistStats` 类型仍来自 `LetPubEnrichmentPersistPort`**——它是 Port 接口内部的 nested record。Persister 原样透传该类型，所以 Worker 这里还得保留 `LetPubEnrichmentPersistPort.PersistStats` 作为类型名（Port 的 import 被删除了，但用全类名引用 nested type 仍合法）。
**但项目 code-style 禁止使用全类名**。正确做法是**重新 import `LetPubEnrichmentPersistPort`** 仅作类型引用，即便本类不直接调 Port 的方法。或者把 `PersistStats` 提到独立文件（超出本 plan 范围）。
**决定**：保留 `LetPubEnrichmentPersistPort` 的 import，Worker 只是不再作为字段依赖 Port，但编译仍需要 import 来书写 `LetPubEnrichmentPersistPort.PersistStats` 这个嵌套类型引用。

完整替换后的文件内容：

```java
package com.patra.catalog.app.usecase.venue.letpub;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 处理单个 venue 的 LetPub 富化编排单元（非事务）。
///
/// **架构分层**：
///
/// - **本类（Worker）**：非事务，负责编排顺序——检查 ISSN → HTTP 爬取 → 按需
///   下载封面 → 委托 [LetPubEnrichmentPersister] 持久化。HTTP 调用和
///   `Thread.sleep` backoff 发生在事务外，不占用 DB 连接。
/// - **[LetPubEnrichmentPersister]**：事务门面，`@Transactional(REQUIRES_NEW)`
///   包一层 `PersistPort.persist` 调用，把 DB 连接占用收缩到最紧凑的 DB-only 窗口。
/// - **[LetPubEnrichmentRunner]**：外循环协调器，keyset pagination 喂 venue。
///
/// **为什么与 [LetPubEnrichmentRunner] 是两个 bean**：Spring AOP 自调用不触发代理，
/// 而 [LetPubEnrichmentPersister] 需要通过跨 bean 调用才能激活 `@Transactional`。
/// Runner → Worker → Persister 是三层独立 bean，AOP 代理在每一跳都生效。
///
/// **返回值语义**：三种 [Outcome] 用于 Runner 统计，不是失败标志。
/// 任何 `Exception` 都会向上传播，由 Runner 的 try/catch 计入 `failed`。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class LetPubEnrichmentWorker {

  private final LetPubEnrichmentPort scraperPort;
  private final LetPubEnrichmentPersister persister;
  private final VenueCoverImageDownloadPort coverImageDownloadPort;

  /// 单 venue 处理结果的三种正常分支。
  public enum Outcome {
    /// 成功完成持久化（至少调用一次 persister.persist）
    PROCESSED,
    /// venue 在 LetPub 未检索到数据（scraperPort 返回 empty）
    NOT_FOUND_IN_SOURCE,
    /// venue 的 ISSN-L 为空或空白，无法查询
    MISSING_ISSN
  }

  /// 处理单个 venue 的 LetPub 富化编排。
  ///
  /// **本方法本身不是事务**——事务边界在 [LetPubEnrichmentPersister#persist] 里。
  /// HTTP 爬取和封面下载在事务外执行，只有最后一步 `persister.persist(...)` 会
  /// 进入独立 `REQUIRES_NEW` 事务。
  ///
  /// 流程：检查 ISSN → 爬取 → 按需下载封面 → 调 Persister 持久化。
  ///
  /// @param venue 待处理 venue 快照，`id` 不为 null；`issnL` 允许为 null（返回 MISSING_ISSN）
  /// @return 三种 [Outcome] 之一：PROCESSED / NOT_FOUND_IN_SOURCE / MISSING_ISSN
  /// @throws RuntimeException 任何下游 port 抛出的运行时异常都会向上传播
  public Outcome processVenue(VenueSnapshot venue) {
    if (venue.issnL() == null || venue.issnL().isBlank()) {
      log.debug("Venue [id={}] ISSN-L 为空，跳过 LetPub 富化", venue.id());
      return Outcome.MISSING_ISSN;
    }

    Optional<LetPubVenueData> result = scraperPort.findByIssn(venue.issnL());
    if (result.isEmpty()) {
      log.debug("Venue [id={}, issn={}] 未在 LetPub 找到数据", venue.id(), venue.issnL());
      return Outcome.NOT_FOUND_IN_SOURCE;
    }

    LetPubVenueData data = result.get();
    String coverObjectKey = downloadCoverIfNeeded(venue, data);
    LetPubEnrichmentPersistPort.PersistStats stats =
        persister.persist(venue.id(), data, coverObjectKey);

    log.info(
        "Venue [id={}, issn={}] LetPub 富化成功 JCR={} CAS={} Warning={} cover={}",
        venue.id(),
        venue.issnL(),
        stats.jcrInserted(),
        stats.casInserted(),
        stats.warningInserted(),
        stats.coverUpdated());
    return Outcome.PROCESSED;
  }

  /// 按需下载封面图到对象存储，失败时宽容处理不影响主流程。
  ///
  /// 跳过条件：venue 已有封面键（读 `venue.existingCoverKey()` 得到非 null）/
  /// LetPub 未返回 URL / URL 格式非法 / 下载时抛异常。
  ///
  /// @param venue 目标 venue 快照
  /// @param data LetPub 爬取数据，从中提取 coverImageSourceUrl
  /// @return 新下载的对象键；任何跳过或失败路径返回 null（调用方传 null 给 persister）
  private String downloadCoverIfNeeded(VenueSnapshot venue, LetPubVenueData data) {
    if (venue.existingCoverKey() != null) {
      log.debug("Venue [id={}] 已存在封面对象键，跳过下载", venue.id());
      return null;
    }
    String sourceUrl = data.basicInfo().coverImageSourceUrl();
    if (sourceUrl == null || sourceUrl.isBlank()) {
      return null;
    }

    String stableKey = "catalog/venue-cover/" + venue.id() + ".jpg";
    URI sourceUri;
    try {
      sourceUri = URI.create(sourceUrl);
    } catch (IllegalArgumentException e) {
      log.warn("venue 封面 URL 格式非法（继续）: venueId={} sourceUrl={}", venue.id(), sourceUrl);
      return null;
    }
    try {
      return coverImageDownloadPort.downloadAndStore(sourceUri, stableKey);
    } catch (FileDownloadException e) {
      log.warn(
          "venue 封面下载失败（继续）: venueId={} trait={} reason={}",
          venue.id(),
          e.getErrorTraits(),
          e.getMessage());
      return null;
    } catch (RuntimeException e) {
      log.warn("venue 封面下载意外异常（继续）: venueId={}", venue.id(), e);
      return null;
    }
  }
}
```

- [ ] **Step 2.6: RED → GREEN — 更新 `LetPubEnrichmentWorkerTest` 为新依赖**

(a) 把 `@Mock LetPubEnrichmentPersistPort persistPort;` 改为 `@Mock LetPubEnrichmentPersister persister;`

(b) 删除 `LetPubEnrichmentPersistPort` 的 import（如果剩下的 test 代码还用 `LetPubEnrichmentPersistPort.PersistStats` 作为返回值构造，就保留该 import 但不要放 `@Mock` 字段）

(c) `setUp()`：
```java
// BEFORE
worker = new LetPubEnrichmentWorker(scraperPort, persistPort, coverPort);
// AFTER (注意 Task 1 已经移除了 readPort 参数，这里只是把 persistPort 换成 persister)
worker = new LetPubEnrichmentWorker(scraperPort, persister, coverPort);
```

(d) 把所有 `when(persistPort.persist(...))` 的 stub 改为 `when(persister.persist(...))`。`verify(persistPort).persist(...)` 同样改为 `verify(persister).persist(...)`。返回类型仍然是 `LetPubEnrichmentPersistPort.PersistStats`——这是 Persister 方法的真实返回类型。

例如：
```java
// BEFORE
when(persistPort.persist(100L, data, "catalog/venue-cover/100.jpg"))
    .thenReturn(new LetPubEnrichmentPersistPort.PersistStats(10, 5, 2, true));
// AFTER
when(persister.persist(100L, data, "catalog/venue-cover/100.jpg"))
    .thenReturn(new LetPubEnrichmentPersistPort.PersistStats(10, 5, 2, true));
```

`processVenue_persistThrows_propagatesException` 测试里的 `when(persistPort.persist(...)).thenThrow(...)` 也改为 `when(persister.persist(...)).thenThrow(...)`。

(e) 运行测试：
Run: `./gradlew :patra-catalog:patra-catalog-app:test --tests 'com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentWorkerTest' --rerun-tasks`
Expected: 全部 8 个测试 PASS。

- [ ] **Step 2.7: RED — 写 `ScopusEnrichmentPersisterTest`**

创建 `patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentPersisterTest.java`：

```java
package com.patra.catalog.app.usecase.venue.scopus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort.PersistStats;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// ScopusEnrichmentPersister 单元测试。
///
/// Persister 的唯一职责是**承载事务边界**并把调用转发给 PersistPort。
/// 单元测试只验证参数/返回值/异常透传；事务边界的实际生效留给集成测试。
@DisplayName("ScopusEnrichmentPersister 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class ScopusEnrichmentPersisterTest {

  @Mock ScopusEnrichmentPersistPort persistPort;

  ScopusEnrichmentPersister persister;

  @BeforeEach
  void setUp() {
    persister = new ScopusEnrichmentPersister(persistPort);
  }

  @Test
  @DisplayName("透传 venueId / data 参数到 PersistPort")
  void persist_delegatesArgumentsToPort() {
    ScopusVenueData data =
        new ScopusVenueData(
            "scopus-123", List.of(), 1.5, 2.1, null, "Medicine", "Q1", 90.0);
    PersistStats expected = PersistStats.of(3);
    when(persistPort.persist(200L, data)).thenReturn(expected);

    PersistStats actual = persister.persist(200L, data);

    assertThat(actual).isSameAs(expected);
    verify(persistPort).persist(200L, data);
  }

  @Test
  @DisplayName("PersistPort 抛异常 - 向上传播（由事务边界触发回滚）")
  void persist_portThrows_propagates() {
    ScopusVenueData data =
        new ScopusVenueData(
            "scopus-123", List.of(), 1.5, 2.1, null, "Medicine", "Q1", 90.0);
    when(persistPort.persist(200L, data))
        .thenThrow(new RuntimeException("db constraint violation"));

    assertThatThrownBy(() -> persister.persist(200L, data))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db constraint violation");
  }
}
```

**注意**：`ScopusVenueData` 的构造参数数量在 worktree 代码里可能和上面示例不一致。**在 Step 2.7 开始前必须 Read `ScopusVenueData.java` 确认当前 record 字段顺序**，然后调整 fixture 构造。如果它有 factory `.of(...)` 或 `.empty()`，优先用 factory。

- [ ] **Step 2.8: RED — 运行测试验证 Scopus Persister 不存在**

Run: `./gradlew :patra-catalog:patra-catalog-app:compileTestJava`
Expected: 编译错误 `cannot find symbol: class ScopusEnrichmentPersister`

- [ ] **Step 2.9: GREEN — 创建 `ScopusEnrichmentPersister`**

创建 `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentPersister.java`：

```java
package com.patra.catalog.app.usecase.venue.scopus;

import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort.PersistStats;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// Scopus 富化持久化事务边界 bean。
///
/// **唯一职责**：给 [ScopusEnrichmentPersistPort#persist] 的调用包一层
/// `@Transactional(REQUIRES_NEW)` 事务。自身没有任何业务逻辑。
///
/// **为什么需要这个 bean**：[ScopusEnrichmentWorker#processVenue] 要先做
/// Scopus Serial Title API 的 HTTP 调用，再写 DB。老实现把 `@Transactional`
/// 放在 `processVenue` 上会让 HTTP 响应等待期间占用 HikariCP 连接。把事务
/// 边界下移到 Persister，Worker 不再持有 DB 连接直到调用 `persister.persist(...)`。
/// 跨 bean 调用触发 Spring AOP 代理，事务语义不变但作用域更紧凑。
///
/// **为什么不直接在 PersistPort 上加 `@Transactional`**：项目规则
/// `rules/tech/jpa.md` 规定"只在 Application 层使用 `@Transactional`"。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class ScopusEnrichmentPersister {

  private final ScopusEnrichmentPersistPort persistPort;

  /// 在独立事务里持久化一个 venue 的 Scopus 富化结果。
  ///
  /// 调用方必须已经完成 HTTP 爬取——本方法只做 DB 写入。`REQUIRES_NEW` 确保
  /// 即便外层调用方已有事务，本次写入也在独立事务里，任何 `Exception` 触发
  /// 本事务回滚但不影响外层。
  ///
  /// @param venueId 目标 venue 主键
  /// @param data Scopus API 返回的原始数据，不为 null
  /// @return PersistPort 返回的 [PersistStats]，供调用方记录日志
  /// @throws RuntimeException 任何持久化异常都会向上传播并触发事务回滚
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public PersistStats persist(long venueId, ScopusVenueData data) {
    return persistPort.persist(venueId, data);
  }
}
```

- [ ] **Step 2.10: GREEN — 运行 `ScopusEnrichmentPersisterTest`**

Run: `./gradlew :patra-catalog:patra-catalog-app:test --tests 'com.patra.catalog.app.usecase.venue.scopus.ScopusEnrichmentPersisterTest' --rerun-tasks`
Expected: PASS 2/2

- [ ] **Step 2.11: RED — 改 `ScopusEnrichmentWorker`**

替换整个文件内容为：

```java
package com.patra.catalog.app.usecase.venue.scopus;

import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData;
import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 处理单个 venue 的 Scopus 富化编排单元（非事务）。
///
/// **架构分层**：
///
/// - **本类（Worker）**：非事务，负责编排顺序——检查 ISSN → HTTP 爬取 →
///   委托 [ScopusEnrichmentPersister] 持久化。HTTP 调用发生在事务外，
///   不占用 DB 连接。
/// - **[ScopusEnrichmentPersister]**：事务门面，`@Transactional(REQUIRES_NEW)`
///   包一层 `PersistPort.persist` 调用。
/// - **[ScopusEnrichmentRunner]**：外循环协调器 + 400ms rate limit。
///
/// 与 LetPub 版本相比更简单：Scopus API 不提供期刊封面，故无封面下载逻辑，
/// 依赖只剩 [ScopusEnrichmentPort] 和 [ScopusEnrichmentPersister] 两个。
///
/// **返回值语义**：三种 [Outcome] 用于 Runner 统计，不是失败标志。
/// 任何 `Exception` 都会向上传播，由 Runner 的 try/catch 计入 `failed`。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class ScopusEnrichmentWorker {

  private final ScopusEnrichmentPort scraperPort;
  private final ScopusEnrichmentPersister persister;

  /// 单 venue 处理结果的三种正常分支。
  public enum Outcome {
    /// 成功完成持久化（至少调用一次 persister.persist）
    PROCESSED,
    /// venue 在 Scopus 未检索到数据（scraperPort 返回 empty）
    NOT_FOUND_IN_SOURCE,
    /// venue 的 ISSN-L 为空或空白，无法查询
    MISSING_ISSN
  }

  /// 处理单个 venue 的 Scopus 富化编排。
  ///
  /// **本方法本身不是事务**——事务边界在 [ScopusEnrichmentPersister#persist] 里。
  /// HTTP 爬取在事务外执行，只有 `persister.persist(...)` 进入独立 `REQUIRES_NEW` 事务。
  ///
  /// 流程：检查 ISSN → 爬取 → 调 Persister 持久化。无封面下载路径。
  ///
  /// @param venue 待处理 venue 快照，`id` 不为 null；`issnL` 允许为 null（返回 MISSING_ISSN）
  /// @return 三种 [Outcome] 之一：PROCESSED / NOT_FOUND_IN_SOURCE / MISSING_ISSN
  /// @throws RuntimeException 任何下游 port 抛出的运行时异常都会向上传播
  public Outcome processVenue(VenueSnapshot venue) {
    if (venue.issnL() == null || venue.issnL().isBlank()) {
      log.debug("Venue [id={}] ISSN-L 为空，跳过 Scopus 富化", venue.id());
      return Outcome.MISSING_ISSN;
    }

    Optional<ScopusVenueData> result = scraperPort.findByIssn(venue.issnL());
    if (result.isEmpty()) {
      log.debug("Venue [id={}, issn={}] 未在 Scopus 找到数据", venue.id(), venue.issnL());
      return Outcome.NOT_FOUND_IN_SOURCE;
    }

    ScopusVenueData data = result.get();
    ScopusEnrichmentPersistPort.PersistStats stats = persister.persist(venue.id(), data);

    log.info(
        "Venue [id={}, issn={}] Scopus 富化成功 ratingsInserted={}",
        venue.id(),
        venue.issnL(),
        stats.scopusRatingsInserted());
    return Outcome.PROCESSED;
  }
}
```

- [ ] **Step 2.12: GREEN — 更新 `ScopusEnrichmentWorkerTest`**

(a) 把 `@Mock ScopusEnrichmentPersistPort persistPort;` 改为 `@Mock ScopusEnrichmentPersister persister;`

(b) `setUp()` 里 `worker = new ScopusEnrichmentWorker(scraperPort, persistPort);` → `worker = new ScopusEnrichmentWorker(scraperPort, persister);`

(c) 所有 `when(persistPort.persist(...))` 改为 `when(persister.persist(...))`；`verify(persistPort)` 改为 `verify(persister)`。返回类型 `ScopusEnrichmentPersistPort.PersistStats` 不变（仍需保留 `ScopusEnrichmentPersistPort` import 作为嵌套类型引用）。

(d) 运行测试：
Run: `./gradlew :patra-catalog:patra-catalog-app:test --tests 'com.patra.catalog.app.usecase.venue.scopus.ScopusEnrichmentWorkerTest' --rerun-tasks`
Expected: 全部测试 PASS（具体数量取决于现有测试，应该是 6-8 个）。

- [ ] **Step 2.13: 全量测试 + 编译验证**

Run: `./gradlew :patra-catalog:patra-catalog-app:test :patra-catalog:patra-catalog-adapter:compileJava --rerun-tasks`
Expected:
- `patra-catalog-app` 全部测试绿（~200+ 测试）
- `patra-catalog-adapter` clean compile（Runner 通过 Handler → Runner 的跨层调用关系未变，不受本 Task 影响）

也可以再 smoke check `infra`:
Run: `./gradlew :patra-catalog:patra-catalog-infra:compileJava`
Expected: clean compile

- [ ] **Step 2.14: Commit**

```bash
git -C /Users/linqibin/Desktop/Patra/patra-api-venue-worker-loop add \
  patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentPersister.java \
  patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentPersister.java \
  patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorker.java \
  patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentWorker.java \
  patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorkerTest.java \
  patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentWorkerTest.java \
  patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentPersisterTest.java \
  patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentPersisterTest.java

git -C /Users/linqibin/Desktop/Patra/patra-api-venue-worker-loop commit -m "refactor(catalog): Worker 事务边界收紧到 Persister bean 避免 HTTP 占用 DB 连接"
```

---

## 验收清单

整个 plan 执行完毕后必须满足：

1. ✅ `VenueSnapshot` record 带 3 个字段（`id` / `issnL` / `existingCoverKey`），`VenueEnrichmentReadPort` 不再有 `findExistingCoverKey` 方法
2. ✅ `LetPubEnrichmentWorker` 和 `ScopusEnrichmentWorker` 的 `processVenue` 方法上**没有 `@Transactional` 注解**
3. ✅ `LetPubEnrichmentPersister` 和 `ScopusEnrichmentPersister` 两个新 bean 存在于 app 层，方法级 `@Transactional(REQUIRES_NEW, rollbackFor = Exception.class)`
4. ✅ `patra-catalog-app` 测试套件全绿
5. ✅ `patra-catalog-adapter` / `patra-catalog-infra` 至少 clean compile
6. ✅ `git log --oneline bb08335c..HEAD` 里出现 **2 个新 commit**（Task 1 + Task 2），每个 commit 本身可独立 revert
7. ✅ `grep -rn "findExistingCoverKey" patra-catalog/` 无结果
8. ✅ `grep -rn "@Transactional" patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/` 只出现 **2 次**（两个 Persister 上各一次）——Worker 和 Runner 都不应出现 `@Transactional`

## 风险与回退

- **风险 1**：Persister bean 的 AOP 代理未生效 → 事务不会开启。
  - **检测**：集成环境下观察是否有 `TransactionalPersistenceAdvice` 或 Spring TX logs。
  - **回退**：给 Persister 类或 method 加 `@Transactional` + 确认它被 `@Service` 注解（Spring 扫描）。如果仍失败，检查 `@EnableTransactionManagement` 是否在 Boot 配置里生效。

- **风险 2**：`VenueSnapshot` 的新构造函数和 JPQL `SELECT new` 的参数顺序不匹配 → 运行时 `NoSuchMethodException`。
  - **检测**：`VenueEnrichmentReadAdapterIT` 会在启动时失败并报 `InstantiationException`。
  - **回退**：确保 JPQL 里 `SELECT new com.patra.catalog.domain.port.enrichment.VenueSnapshot(v.id, v.issnL, v.imageObjectKey)` 的参数顺序与 record 声明顺序 `(Long id, String issnL, String existingCoverKey)` 完全一致。

- **风险 3**：Scopus 的 `VenueSnapshot` projection 总是读一个 Scopus 不用的列 → 极微小的 SELECT 列数增加。
  - **评估**：`image_object_key` 是 `VARCHAR(255)`，额外读取成本可忽略。**不回退**。

## Commit Strategy

本 plan 产出 **2 个 commit**，都落在 `feat/venue-worker-loop` 分支：

1. `refactor(catalog): VenueSnapshot 投影 existingCoverKey 消除 Worker 的多余 PK 查询`
2. `refactor(catalog): Worker 事务边界收紧到 Persister bean 避免 HTTP 占用 DB 连接`

Plan 文件本身（本文档）应单独 commit 到 `main` 分支，与 `2026-04-11-venue-enrichment-worker-loop-migration.md` 并列，用于记录后续修复工作的决策过程。

## Deferred（明确不做的事）

- **Worker 事务拆分的集成测试**：验证 Persister 的 `@Transactional(REQUIRES_NEW)` 真正生效需要启动 Spring 容器并断言 `TransactionSynchronizationManager` 状态——复杂度大且对真实风险覆盖有限。单元测试 + 现有 `@DataJpaTest` 级别的 IT（VenueEnrichmentReadAdapterIT）已足以验证 plan 的大部分改动。如果上线后有疑虑再补充。
- **`idx_venue_id` 冗余索引清理**：见 "Rejected Findings #A 副产品发现"。
- **SELECT-before-insert → upsert**：见 "Rejected Findings #B"。
- **集成测试 scraper-mock 的端到端 run**：out-of-scope，plan 不覆盖。

## References

- 前序 plan：`docs/superpowers/plans/2026-04-11-venue-enrichment-worker-loop-migration.md`
- Code review findings：这次 simplify 的 3 个并行 review agent 报告（session history）
- 项目规则：
  - `rules/tech/jpa.md`（"只在 Application 层使用 `@Transactional`"）
  - `rules/tech/port-service.md`（Port 命名规范，Persister 作为 app 层编排类符合"`{Domain}Service`" 的精神）
  - `rules/code-style.md`（Javadoc `///` 格式，禁止 FQN）
