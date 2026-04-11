# Venue Enrichment: Spring Batch → Worker Loop Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 LetPub + Scopus 两个 Venue 富化 Job 从 Spring Batch (chunk=1, faultTolerant) 迁移到普通 Spring `@Service` 循环，消除 worker-queue 工作负载对 chunk-oriented ETL 框架的误用。

**Architecture:** App 层新增 `LetPub/ScopusEnrichmentRunner`（外循环、非事务）+ `LetPub/ScopusEnrichmentWorker`（`@Transactional(REQUIRES_NEW)` 单 venue 处理）。Domain 层新增 `VenueEnrichmentReadPort`（keyset pagination + NOT EXISTS 守卫）和 `LetPub/ScopusEnrichmentPersistPort`（封装 Mapper + 去重 + DAO 写入）。Infra 层把现有 Spring Batch `Processor/Writer` 里的逻辑搬到新的 `*PersistAdapter` 里，保留 `*DataMapper` 和 `*Dao`。Handler 保持为入口，但改为注入 Runner 直接同步执行。Result 类型从"返回 executionId"改为"返回 `(total, processed, skipped, failed)` 统计"。

**Tech Stack:** Java 25, Spring Boot 4.0.1, Spring Data JPA, Lombok, JUnit 5, Mockito, AssertJ. **不引入** `@Scheduled`/`@EnableScheduling`（项目使用 XXL-Job 作为调度层，触发层面零改动）。

---

## 上下文与前置约束

### 迁移范围

**迁移**（2 个 Job）：
- LetPub Venue Enrichment (chunk=1, 网络爬取 8-10s/条, faultTolerant skipAll)
- Scopus Venue Enrichment (chunk=1, API 限速 400ms/条, faultTolerant skipAll)

**保持不动**（5 个 Job，真正的 ETL）：
- `authorImportJob`、`meshDescriptorImportJob`、`meshScrImportJob`、`rorOrganizationImportJob`、`pubmedBaselineImportJob`
- starter-batch 模块继续保留，这 5 个 Job 继续走 `JobOperatorHelper`。

### 在飞修改避让

当前工作区有 LetPub 反爬相关的**未提交**修改，迁移**不得触碰**以下文件：

```
patra-catalog-domain/.../LetPubVenueData.java
patra-catalog-domain/.../LetPubVenueDataTest.java
patra-catalog-infra/.../LetPubDetailPageParser.java
patra-catalog-infra/.../LetPubDetailPageParserTest.java
patra-catalog-infra/.../LetPubEnrichmentAdapterIT.java
patra-catalog-infra/.../LetPubDataMapperTest.java
```

**策略**：迁移中保留 `LetPubDataMapper.java` 不动，新 `PersistAdapter` 直接 `import` 并调用它。迁移完成后这些测试应继续通过（Mapper 契约不变）。

### 10 年趋势填充特性（决策 3-A 保留）

LetPub 详情页返回 10 年 JCR 趋势 + 多版本 CAS 分区数据。当前 Writer 通过 `filterNewJcrRatings/filterNewCasRatings/filterNewCasWarnings` 在插入前过滤掉已存在的年份/版本组合，从而允许一次跑动机会主义填充多年历史数据。迁移后此特性由 `LetPubEnrichmentPersistAdapter` 内部的去重逻辑承载（同样的 `existingKeys` 查询），行为保持一致。

### 事务边界约束

依据 `.claude/rules/layers/application.md`：**Application 层是唯一管理事务的层级**。
- `Runner.run()`：**不加** `@Transactional`，外层循环非事务。
- `Worker.processVenue()`：**加** `@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)`，每个 venue 独占一个事务。
- `PersistAdapter` (Infra)：**不加** `@Transactional`，通过 Spring 事务传播在 Worker 的事务内运行。

### 测试策略

- **Worker 单测**（Mockito）：覆盖 happy path、venue 未在源找到、ISSN-L 为空、PersistPort 抛异常 3 种主要分支。
- **Runner 单测**（Mockito）：覆盖多页读取、跨页失败隔离、NOT EXISTS 动态收敛（keyset 前进）、空结果立即退出 4 种场景。
- **PersistAdapter IT**（`@DataJpaTest` + Testcontainers MySQL）：覆盖 Mapper + 去重 + DAO 写入，验证"重复年份不插入"。
- **Handler 单测**：仅验证"调用 Runner、异常包装成 ApplicationException"。
- **端到端 IT**：使用现有的 `LetPubEnrichmentAdapterIT` 作为下层验证，新增 `LetPubEnrichmentRunnerIT` 组装完整链路（Runner → Worker → PersistAdapter → 真 DB，LetPubEnrichmentPort mock）。

#### Mockito 约定按模块区分

项目里 **app-layer 和 infra-layer 的 Mockito 用法不同**，这是既成约定，不是疏忽：

- **App-layer 单测** → `@ExtendWith(MockitoExtension.class)` + `@Timeout(value = 2, unit = TimeUnit.SECONDS)`（JUnit 5 扩展点注入 mock，`STRICT_STUBS` 行为自动检测无效 stubbing）。参考 `VenueLetPubEnrichHandlerTest` / `RorOrganizationImportHandlerTest` / `MeshScrImportHandlerTest` 等 14+ 个测试。**Task 7-14 的 app-layer 测试必须使用这个模式。**
- **Infra-layer 单测** → `MockitoAnnotations.openMocks(this)` 在 `@BeforeEach` 里手动初始化（Task 5/6 的 PersistAdapter 测试符合此模式）。

混用是 Task 7 初次 dispatch 时踩过的坑——不要在 app-layer 测试里写 `MockitoAnnotations.openMocks(this)`。

### Javadoc 质量下限（所有 Task 遵守）

项目规则要求"所有方法（任何访问级别）必须编写 JavaDoc 注释"，但"有 javadoc"不等于"javadoc 合格"。本计划里所有方法 Javadoc 必须满足：

- **公共方法 / 静态工厂 / Port 接口方法**：除一行摘要外，必须标注每个参数契约（特别是 `null` 允许性）、返回值语义、可抛出的业务异常。例如 `of(Long id, String issnL)` 这类工厂方法要说明"`id` 不可为 null（紧凑构造器校验），`issnL` 允许为 null"而不是只写 `/// 工厂方法。`。
- **紧凑构造器 / private helper**：一行行为描述即可，但若含非显而易见的副作用或校验逻辑必须写清楚。
- **Record 的 `@param` 字段注释**：在 record header 的 javadoc 里写，不要在紧凑构造器上重复。

当 plan 正文里给出的 Javadoc 样本看起来过于简短时，**implementer 有义务按上述下限补齐**——plan 里的代码是"最少骨架"，不是"最大应该"。code-reviewer 会按这个下限审查，照抄骨架不算完成。

### Google Java Format 对 `///` 长行的陷阱

项目 pre-commit hook 会运行 Google Java Format。它对 `///` markdown javadoc **不感知语义**——遇到超过列宽（~100 字符）的行会机械地换行，结果可能把连续 `///` 块切断成 `///` + 普通 `//`，导致 `@return`/`@param` 描述在半句处截断且下一行脱离 javadoc 渲染。

**预防**：对 `@return`/`@param` 的描述，尽量用紧凑措辞让它**一行放得下**；如需详细示例（URL 模板、JPQL 片段等），把它放进正文段落而非 tag 行。task 5 的 `buildSourceUrl` 曾因为 `@return` 里嵌了完整 URL 示例被 formatter 切断，教训已吸收到当前骨架中。

---

## 文件结构总览

### 新建文件（Phase A + B + D）

**Domain 层**：
```
patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/
├── enrichment/
│   ├── VenueSnapshot.java                           # 新：读端 venue 快照 record
│   ├── LetPubEnrichmentPersistPort.java             # 新：LetPub 持久化端口
│   └── ScopusEnrichmentPersistPort.java             # 新：Scopus 持久化端口
└── read/
    └── VenueEnrichmentReadPort.java                 # 新：venue 工作队列读端口
```

**Infra 层**：
```
patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/
├── read/
│   └── VenueEnrichmentReadAdapter.java              # 新：ReadPort 实现（JPA + keyset）
└── enrichment/
    ├── letpub/
    │   └── LetPubEnrichmentPersistAdapter.java      # 新：PersistPort 实现（调 LetPubDataMapper + DAO）
    └── scopus/
        └── ScopusEnrichmentPersistAdapter.java      # 新：PersistPort 实现（调 ScopusDataMapper + DAO）
```

**App 层**：
```
patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/
├── letpub/
│   ├── LetPubEnrichmentRunner.java                  # 新：循环协调器
│   └── LetPubEnrichmentWorker.java                  # 新：@Transactional 单 venue worker
└── scopus/
    ├── ScopusEnrichmentRunner.java                  # 新：循环协调器（含 400ms 限速）
    └── ScopusEnrichmentWorker.java                  # 新：@Transactional 单 venue worker
```

### 修改文件（Phase C + D）

- `VenueLetPubEnrichHandler.java` — 注入 Runner 替代 BatchPort；Result 返回统计
- `VenueScopusEnrichHandler.java` — 同上
- `VenueLetPubEnrichResult.java` — `Long executionId` → `(int totalRead, int processed, int skipped, int failed)`
- `VenueScopusEnrichResult.java` — 同上
- Handler 相关的 Controller（如有）— 同步更新，详见 Task 18

### 删除文件（Phase E）

**LetPub Spring Batch 遗留**：
- `LetPubEnrichmentJobConfig.java`
- `LetPubVenueItemProcessor.java`（含单测）
- `LetPubVenueItemWriter.java`（含单测）
- `LetPubEnrichResult.java`（中间 DTO）
- `LetPubEnrichmentBatchAdapter.java`
- `patra-catalog-domain/.../port/batch/LetPubEnrichmentBatchPort.java`

**Scopus Spring Batch 遗留**：
- `ScopusEnrichmentJobConfig.java`
- `ScopusVenueItemProcessor.java`（含单测）
- `ScopusVenueItemWriter.java`
- `ScopusEnrichResult.java`
- `ScopusEnrichmentBatchAdapter.java`
- `patra-catalog-domain/.../port/batch/ScopusEnrichmentBatchPort.java`

**共用**：
- `VenueEnrichmentJobParams.java`
- `patra-catalog-domain/.../port/batch/` 包本身（若空）

**保留不动**：
- `LetPubDataMapper.java`（被 PersistAdapter 使用）
- `ScopusDataMapper.java`（同上）
- `LetPubEnrichmentPort.java` + `LetPubScrapingClient.java`（Domain 爬虫契约 + Infra 实现）
- `ScopusEnrichmentPort.java` + `ScopusEnrichmentAdapter.java`（同上）
- 所有 DAO / Entity

---

## Phase A: Domain + Infra 读端口搭建

### Task 1: 创建 `VenueSnapshot` 值记录

**Files:**
- Create: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/enrichment/VenueSnapshot.java`

- [ ] **Step 1: 写失败测试**

Create: `patra-catalog-domain/src/test/java/com/patra/catalog/domain/port/enrichment/VenueSnapshotTest.java`

```java
package com.patra.catalog.domain.port.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VenueSnapshotTest {

  @Test
  void of_validArguments_createsSnapshot() {
    VenueSnapshot snapshot = VenueSnapshot.of(42L, "1234-5678");
    assertThat(snapshot.id()).isEqualTo(42L);
    assertThat(snapshot.issnL()).isEqualTo("1234-5678");
  }

  @Test
  void of_nullIssnL_allowedForDebugging() {
    VenueSnapshot snapshot = VenueSnapshot.of(42L, null);
    assertThat(snapshot.issnL()).isNull();
  }

  @Test
  void of_nullId_rejected() {
    assertThatThrownBy(() -> VenueSnapshot.of(null, "1234-5678"))
        .isInstanceOf(NullPointerException.class);
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `./gradlew :patra-catalog:patra-catalog-domain:test --tests VenueSnapshotTest`
Expected: FAIL with "cannot find symbol: class VenueSnapshot"

- [ ] **Step 3: 实现 `VenueSnapshot`**

```java
package com.patra.catalog.domain.port.enrichment;

import java.util.Objects;

/// Venue 工作队列读端的轻量投影。
///
/// 用于 [VenueEnrichmentReadPort] 返回"需要富化的 venue"列表，
/// 只包含 Runner/Worker 实际需要的字段：`id`（keyset 游标 + 持久化目标）
/// 和 `issnL`（富化查询键）。
///
/// @param id 聚合根主键，非 null
/// @param issnL ISSN-L，若缺失则调用方应跳过该 venue
/// @author linqibin
/// @since 0.1.0
public record VenueSnapshot(Long id, String issnL) {

  public VenueSnapshot {
    Objects.requireNonNull(id, "VenueSnapshot.id 不可为 null");
  }

  /// 创建 [VenueSnapshot]。
  ///
  /// @param id 聚合根主键，不可为 null（由紧凑构造器校验）
  /// @param issnL ISSN-L，允许为 null
  public static VenueSnapshot of(Long id, String issnL) {
    return new VenueSnapshot(id, issnL);
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew :patra-catalog:patra-catalog-domain:test --tests VenueSnapshotTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/enrichment/VenueSnapshot.java \
        patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/port/enrichment/VenueSnapshotTest.java
git commit -m "feat(catalog): 新增 VenueSnapshot 读端快照记录"
```

---

### Task 2: 创建 `VenueEnrichmentReadPort` 接口

**Files:**
- Create: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/VenueEnrichmentReadPort.java`

- [ ] **Step 1: 定义接口**

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

  /// 读取指定 venue 已有的封面对象键。
  ///
  /// 用于 [LetPubEnrichmentWorker] 判断是否需要重新下载封面（幂等跳过）。
  /// 放在 ReadPort 而不是 PersistPort，是因为这是一次纯读取，语义上属于
  /// "venue 工作队列的附加元数据查询"。
  ///
  /// @param venueId 目标 venue 主键
  /// @return 封面对象键；若 venue 无封面返回 empty
  java.util.Optional<String> findExistingCoverKey(long venueId);
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :patra-catalog:patra-catalog-domain:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/VenueEnrichmentReadPort.java
git commit -m "feat(catalog): 新增 VenueEnrichmentReadPort 读端口"
```

---

### Task 3: 实现 `VenueEnrichmentReadAdapter`（Infra）

**Files:**
- Create: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueEnrichmentReadAdapter.java`
- Test: `patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueEnrichmentReadAdapterIT.java`

- [ ] **Step 1: 写失败 IT**

测试骨架（使用项目现有的 `@DataJpaTest` + Testcontainers 约定，参考 `AuthorRepositoryAdapterIT` 的初始化方式）：

```java
package com.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// ... 项目既定的 Testcontainers 配置注解

class VenueEnrichmentReadAdapterIT /* extends IntegrationTestBase */ {

  @Autowired VenueEnrichmentReadAdapter adapter;
  @Autowired VenueDao venueDao;
  @Autowired JcrRatingDao jcrRatingDao;

  @Test
  void findNeedingLetPubEnrichment_returnsVenuesMissingTargetYear() {
    // Given: 3 个 JOURNAL venue，全有 ISSN-L
    VenueEntity v1 = persistJournal("1000-0001", 100);
    VenueEntity v2 = persistJournal("1000-0002", 200);
    VenueEntity v3 = persistJournal("1000-0003", 300);
    // v2 已有 2025 的 JCR 评级 → 应被过滤掉
    persistJcrRating(v2.getId(), (short) 2025);

    // When
    List<VenueSnapshot> result = adapter.findNeedingLetPubEnrichment(
        (short) 2025, 0, 0L, 50);

    // Then: 返回 v1 和 v3，按 id 升序
    assertThat(result).hasSize(2);
    assertThat(result).extracting(VenueSnapshot::id)
        .containsExactly(v1.getId(), v3.getId());
  }

  @Test
  void findNeedingLetPubEnrichment_keysetAdvancesBeyondLastId() {
    VenueEntity v1 = persistJournal("2000-0001", 0);
    VenueEntity v2 = persistJournal("2000-0002", 0);

    // When: lastId = v1.id
    List<VenueSnapshot> result = adapter.findNeedingLetPubEnrichment(
        (short) 2025, 0, v1.getId(), 50);

    // Then: 只返回 v2
    assertThat(result).extracting(VenueSnapshot::id).containsExactly(v2.getId());
  }

  @Test
  void findNeedingLetPubEnrichment_respectsMinCitedByCount() {
    persistJournal("3000-0001", 50);
    VenueEntity v2 = persistJournal("3000-0002", 150);

    List<VenueSnapshot> result = adapter.findNeedingLetPubEnrichment(
        (short) 2025, 100, 0L, 50);

    assertThat(result).extracting(VenueSnapshot::id).containsExactly(v2.getId());
  }

  @Test
  void findNeedingLetPubEnrichment_skipsVenuesWithoutIssnL() {
    persistJournal(null, 500);
    VenueEntity v2 = persistJournal("4000-0002", 500);

    List<VenueSnapshot> result = adapter.findNeedingLetPubEnrichment(
        (short) 2025, 0, 0L, 50);

    assertThat(result).extracting(VenueSnapshot::id).containsExactly(v2.getId());
  }

  @Test
  void findNeedingLetPubEnrichment_respectsLimit() {
    for (int i = 0; i < 10; i++) persistJournal("5000-" + i, 0);

    List<VenueSnapshot> result = adapter.findNeedingLetPubEnrichment(
        (short) 2025, 0, 0L, 3);

    assertThat(result).hasSize(3);
  }

  @Test
  void findExistingCoverKey_returnsKeyWhenPresent() {
    VenueEntity v = persistJournalWithCover("6000-0001", "catalog/venue-cover/999.jpg");
    assertThat(adapter.findExistingCoverKey(v.getId())).contains("catalog/venue-cover/999.jpg");
  }

  @Test
  void findExistingCoverKey_returnsEmptyWhenAbsent() {
    VenueEntity v = persistJournal("6000-0002", 0);
    assertThat(adapter.findExistingCoverKey(v.getId())).isEmpty();
  }

  // findNeedingScopusEnrichment 的 2 个代表性测试：结构与 LetPub 版本相同，
  // 仅验证 NOT EXISTS 指向 ScopusRatingEntity 的正确性 + 两个表相互独立。
  // LetPub 的 5 个测试已覆盖 keyset/limit/minCitedByCount 等通用分支，无需重复。

  // 辅助方法 persistJournal / persistJournalWithCover / persistJcrRating：
  // 参考项目中其他 Repository IT 的 setup 风格
}
```

- [ ] **Step 2: 运行 IT 验证失败**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests VenueEnrichmentReadAdapterIT`
Expected: FAIL with "cannot find symbol: class VenueEnrichmentReadAdapter"

- [ ] **Step 3: 实现 Adapter**

```java
package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
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

  @PersistenceContext
  private EntityManager em;

  @Override
  public List<VenueSnapshot> findNeedingLetPubEnrichment(
      short targetYear, int minCitedByCount, long lastId, int limit) {
    return em.createQuery(
            """
            SELECT new com.patra.catalog.domain.port.enrichment.VenueSnapshot(v.id, v.issnL)
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

  @Override
  public List<VenueSnapshot> findNeedingScopusEnrichment(
      short targetYear, int minCitedByCount, long lastId, int limit) {
    return em.createQuery(
            """
            SELECT new com.patra.catalog.domain.port.enrichment.VenueSnapshot(v.id, v.issnL)
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

  @Override
  public Optional<String> findExistingCoverKey(long venueId) {
    List<String> results =
        em.createQuery(
                "SELECT v.imageObjectKey FROM VenueEntity v WHERE v.id = :venueId",
                String.class)
            .setParameter("venueId", venueId)
            .setMaxResults(1)
            .getResultList();
    return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.get(0));
  }
}
```

注意：`Optional` 需补一行 `import java.util.Optional;`。`findExistingCoverKey` 的 JPQL 投影拿单列 `imageObjectKey`——若 venue 存在但字段为 null，`getResultList()` 返回 `[null]`，`Optional.ofNullable` 正确处理为 empty；若 venue 不存在则返回空 list，同样处理为 empty。

**字段名验证**（已核对真实 Entity）：
- `JcrRatingEntity.venueId: Long`、`year: Short` ✓
- `ScopusRatingEntity.venueId: Long`、`year: Short` ✓
- `VenueEntity.venueType: String`（JOURNAL 等）、`issnL: String`、`citedByCount: Integer`、`imageObjectKey: String` ✓

- [ ] **Step 4: 运行 IT 验证通过**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests VenueEnrichmentReadAdapterIT`
Expected: PASS (12 tests)

- [ ] **Step 5: Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueEnrichmentReadAdapter.java \
        patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueEnrichmentReadAdapterIT.java
git commit -m "feat(catalog): 新增 VenueEnrichmentReadAdapter（keyset + NOT EXISTS）"
```

---

### Task 4: 创建 `LetPubEnrichmentPersistPort` 接口

**Files:**
- Create: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/enrichment/LetPubEnrichmentPersistPort.java`

- [ ] **Step 1: 定义接口**

```java
package com.patra.catalog.domain.port.enrichment;

/// LetPub 富化数据持久化端口。
///
/// 实现类负责：
///
/// 1. 调用 `LetPubDataMapper` 把 [LetPubVenueData] 展开成 JCR/CAS/CasWarning 多行
/// 2. 对每种实体做 `(venue_id, year[:edition])` 去重过滤（防 UK 冲突）
/// 3. 通过对应 DAO 执行批量插入
/// 4. 若 `coverObjectKey` 非 null，UPDATE `cat_venue.image_object_key`
///
/// **事务约束**：本方法**不自带事务**，必须被 App 层的
/// `@Transactional(REQUIRES_NEW)` worker 调用，依赖事务传播执行。
///
/// @author linqibin
/// @since 0.1.0
public interface LetPubEnrichmentPersistPort {

  /// 持久化一个 venue 的 LetPub 富化结果。
  ///
  /// @param venueId 目标 venue 主键，不可为 null
  /// @param data LetPub 爬取到的原始数据，不可为 null
  /// @param coverObjectKey 新下载的封面对象键；**允许为 null**
  ///     （null 表示本轮未下载或 venue 已存在封面不需重下，不更新 image_object_key 列）
  /// @return 插入统计（便于日志和测试断言），不为 null
  PersistStats persist(long venueId, LetPubVenueData data, String coverObjectKey);

  /// LetPub 持久化的插入统计结果。
  ///
  /// @param jcrInserted 本次真正插入 `cat_venue_jcr_rating` 的行数（去重后）
  /// @param casInserted 本次真正插入 `cat_venue_cas_rating` 的行数（去重后）
  /// @param warningInserted 本次真正插入 `cat_venue_cas_warning` 的行数（去重后）
  /// @param coverUpdated 是否更新了 `cat_venue.image_object_key` 列
  record PersistStats(int jcrInserted, int casInserted, int warningInserted, boolean coverUpdated) {

    /// 创建 [PersistStats]。
    ///
    /// @param jcr 插入的 JCR 行数
    /// @param cas 插入的 CAS 评级行数
    /// @param warning 插入的 CAS 预警行数
    /// @param cover 是否更新了封面对象键
    public static PersistStats of(int jcr, int cas, int warning, boolean cover) {
      return new PersistStats(jcr, cas, warning, cover);
    }
  }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :patra-catalog:patra-catalog-domain:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/enrichment/LetPubEnrichmentPersistPort.java
git commit -m "feat(catalog): 新增 LetPubEnrichmentPersistPort 持久化端口"
```

---

### Task 5: 实现 `LetPubEnrichmentPersistAdapter`（Infra）

**Files:**
- Create: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/enrichment/letpub/LetPubEnrichmentPersistAdapter.java`
- Test: `patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/enrichment/letpub/LetPubEnrichmentPersistAdapterTest.java`

**要点**：这个类的逻辑从 `LetPubVenueItemWriter` 搬过来 + 调用 `LetPubDataMapper`（之前在 Processor 里做）。合并后：Mapper 展开 → filterNewXxx 去重 → DAO saveAll → 可选封面 UPDATE。单测使用 Mockito mock DAO，聚焦"去重逻辑正确 + 封面条件分支正确"。

- [ ] **Step 1: 写失败单测（最小覆盖 3 个分支）**

```java
package com.patra.catalog.infra.adapter.enrichment.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort.PersistStats;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.infra.batch.venue.letpub.LetPubDataMapper; // 保留旧位置
import com.patra.catalog.infra.persistence.dao.CasRatingDao;
import com.patra.catalog.infra.persistence.dao.CasWarningDao;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class LetPubEnrichmentPersistAdapterTest {

  @Mock JcrRatingDao jcrDao;
  @Mock CasRatingDao casDao;
  @Mock CasWarningDao warnDao;
  @Mock VenueDao venueDao;

  LetPubEnrichmentPersistAdapter adapter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    adapter = new LetPubEnrichmentPersistAdapter(
        new LetPubDataMapper(), jcrDao, casDao, warnDao, venueDao);
  }

  @Test
  void persist_filtersOutExistingYears() {
    // Given: Mapper 会生成 10 年 JCR 行
    LetPubVenueData data = TestFixtures.letPubDataWith10YearTrend();
    when(jcrDao.findYearsByVenueId(100L))
        .thenReturn(Set.of((short) 2020, (short) 2021, (short) 2022));
    when(casDao.findKeysByVenueId(100L)).thenReturn(Set.of());
    when(warnDao.findKeysByVenueId(100L)).thenReturn(Set.of());

    // When
    PersistStats stats = adapter.persist(100L, data, null);

    // Then: 只插入 10 - 3 = 7 年
    assertThat(stats.jcrInserted()).isEqualTo(7);
    verify(jcrDao).saveAll(argThat(list -> ((List<?>) list).size() == 7));
    verify(venueDao, never()).updateImageObjectKey(anyLong(), any());
  }

  @Test
  void persist_updatesCoverImageWhenKeyProvided() {
    LetPubVenueData data = TestFixtures.letPubDataSingleYear();
    when(jcrDao.findYearsByVenueId(anyLong())).thenReturn(Set.of());
    when(casDao.findKeysByVenueId(anyLong())).thenReturn(Set.of());
    when(warnDao.findKeysByVenueId(anyLong())).thenReturn(Set.of());
    when(venueDao.updateImageObjectKey(100L, "catalog/venue-cover/100.jpg")).thenReturn(1);

    PersistStats stats = adapter.persist(100L, data, "catalog/venue-cover/100.jpg");

    assertThat(stats.coverUpdated()).isTrue();
    verify(venueDao).updateImageObjectKey(100L, "catalog/venue-cover/100.jpg");
  }

  @Test
  void persist_allYearsExisting_savesNothing() {
    LetPubVenueData data = TestFixtures.letPubDataWith10YearTrend();
    Set<Short> allYears = Set.of((short) 2016, (short) 2017, (short) 2018, (short) 2019,
        (short) 2020, (short) 2021, (short) 2022, (short) 2023, (short) 2024, (short) 2025);
    when(jcrDao.findYearsByVenueId(100L)).thenReturn(allYears);
    when(casDao.findKeysByVenueId(100L)).thenReturn(Set.of());
    when(warnDao.findKeysByVenueId(100L)).thenReturn(Set.of());

    PersistStats stats = adapter.persist(100L, data, null);

    assertThat(stats.jcrInserted()).isEqualTo(0);
    verify(jcrDao, never()).saveAll(any());
  }
}
```

`TestFixtures` 提供构造 `LetPubVenueData` 的辅助方法 —— 参考现有 `LetPubVenueItemProcessorTest` 的 fixture 构造方式直接复用。

- [ ] **Step 2: 运行测试验证失败**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests LetPubEnrichmentPersistAdapterTest`
Expected: FAIL with "cannot find symbol: class LetPubEnrichmentPersistAdapter"

- [ ] **Step 3: 实现 Adapter**

```java
package com.patra.catalog.infra.adapter.enrichment.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.infra.batch.venue.letpub.LetPubDataMapper;
import com.patra.catalog.infra.persistence.dao.CasRatingDao;
import com.patra.catalog.infra.persistence.dao.CasWarningDao;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// [LetPubEnrichmentPersistPort] 的 JPA 实现。
///
/// **职责边界**：单一持久化单元——Mapper 展开 [LetPubVenueData] 为 JCR/CAS/Warning
/// 多行，`writeXxx` 方法按 `(venue_id, year[:edition])` 剔除重复，最后通过
/// DAO 批量写入。封面对象键若非 null，UPDATE `cat_venue.image_object_key`。
///
/// **10 年趋势填充**：LetPub 详情页返回 10 年 IF 趋势 + 多版本 CAS 分区数据，
/// Mapper 会全部展开，Adapter 按键去重——单次调用 `target_year=2025` 可以
/// 机会主义填充 2016-2024 的历史数据（仅插入之前缺失的年份）。
///
/// **事务**：本类**不加** `@Transactional`，由调用方（App 层 Worker）的
/// `REQUIRES_NEW` 事务边界包裹。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class LetPubEnrichmentPersistAdapter implements LetPubEnrichmentPersistPort {

  private final LetPubDataMapper dataMapper;
  private final JcrRatingDao jcrRatingDao;
  private final CasRatingDao casRatingDao;
  private final CasWarningDao casWarningDao;
  private final VenueDao venueDao;

  @Override
  public PersistStats persist(long venueId, LetPubVenueData data, String coverObjectKey) {
    String sourceUrl = buildSourceUrl(data.basicInfo().letPubJournalId());

    int jcrCount = writeJcr(venueId, dataMapper.mapToJcrRatings(data, venueId, sourceUrl));
    int casCount = writeCas(venueId, dataMapper.mapToCasRatings(data, venueId, sourceUrl));
    int warnCount = writeWarnings(venueId, dataMapper.mapToCasWarnings(data, venueId, sourceUrl));

    boolean coverUpdated = false;
    if (coverObjectKey != null) {
      int updated = venueDao.updateImageObjectKey(venueId, coverObjectKey);
      if (updated == 0) {
        log.warn("Venue [id={}] 封面对象键 UPDATE 影响 0 行", venueId);
      } else {
        coverUpdated = true;
        log.debug("Venue [id={}] 已更新封面对象键: {}", venueId, coverObjectKey);
      }
    }
    return PersistStats.of(jcrCount, casCount, warnCount, coverUpdated);
  }

  /// 将 JCR 评级行按"年份已存在"去重后批量插入。
  ///
  /// 先从 `cat_venue_jcr_rating` 捞已有年份集合，再剔除 Mapper 生成的重复年份行。
  /// 空集合直接跳过写库（避免 no-op SQL 往返）。
  ///
  /// @param venueId 目标 venue 主键
  /// @param all Mapper 展开的完整 JCR 行列表（可能含历史年份），不为 null
  /// @return 实际插入的行数
  private int writeJcr(long venueId, List<JcrRatingEntity> all) {
    if (all.isEmpty()) return 0;
    Set<Short> existing = jcrRatingDao.findYearsByVenueId(venueId);
    List<JcrRatingEntity> toInsert = existing.isEmpty()
        ? all
        : all.stream().filter(r -> !existing.contains(r.getYear())).toList();
    if (toInsert.isEmpty()) return 0;
    jcrRatingDao.saveAll(toInsert);
    log.debug("Venue [id={}] 插入 {} 条 JCR 评级（跳过 {} 条已存在年份）",
        venueId, toInsert.size(), all.size() - toInsert.size());
    return toInsert.size();
  }

  /// 将 CAS 评级行按 `(year, edition)` 组合去重后批量插入。
  ///
  /// CAS 分区每年可发布多个版本（基础版 / 升级版），因此去重键包含版本号。
  /// 已有组合以 `year:edition` 字符串形式从 DAO 投影查询返回。
  ///
  /// @param venueId 目标 venue 主键
  /// @param all Mapper 展开的完整 CAS 评级行列表，不为 null
  /// @return 实际插入的行数
  private int writeCas(long venueId, List<CasRatingEntity> all) {
    if (all.isEmpty()) return 0;
    Set<String> existing = casRatingDao.findKeysByVenueId(venueId);
    List<CasRatingEntity> toInsert = existing.isEmpty()
        ? all
        : all.stream()
            .filter(r -> !existing.contains(r.getYear() + ":" + r.getEdition()))
            .toList();
    if (toInsert.isEmpty()) return 0;
    casRatingDao.saveAll(toInsert);
    log.debug("Venue [id={}] 插入 {} 条 CAS 评级（跳过 {} 条已存在版本）",
        venueId, toInsert.size(), all.size() - toInsert.size());
    return toInsert.size();
  }

  /// 将 CAS 预警行按 `(publishedYear, editionLabel)` 组合去重后批量插入。
  ///
  /// 预警数据使用"发布年份 + 版本标签"作为唯一键——与 CAS 评级的 `(year, edition)`
  /// 并行但字段名不同。
  ///
  /// @param venueId 目标 venue 主键
  /// @param all Mapper 展开的完整 CAS 预警行列表，不为 null
  /// @return 实际插入的行数
  private int writeWarnings(long venueId, List<CasWarningEntity> all) {
    if (all.isEmpty()) return 0;
    Set<String> existing = casWarningDao.findKeysByVenueId(venueId);
    List<CasWarningEntity> toInsert = existing.isEmpty()
        ? all
        : all.stream()
            .filter(w -> !existing.contains(w.getPublishedYear() + ":" + w.getEditionLabel()))
            .toList();
    if (toInsert.isEmpty()) return 0;
    casWarningDao.saveAll(toInsert);
    log.debug("Venue [id={}] 插入 {} 条 CAS 预警（跳过 {} 条已存在）",
        venueId, toInsert.size(), all.size() - toInsert.size());
    return toInsert.size();
  }

  /// 构建 LetPub 详情页 URL 作为数据溯源，用于写入评级行的 `source_url` 列。
  ///
  /// @param journalId LetPub 站内的期刊 ID；若为 null 或空白返回 null（调用方宽容处理）
  /// @return LetPub 期刊详情页的完整 URL；`journalId` 为空时返回 null
  private static String buildSourceUrl(String journalId) {
    if (journalId == null || journalId.isBlank()) return null;
    return "https://www.letpub.com.cn/index.php?journalid=" + journalId + "&page=journalapp&view=detail";
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests LetPubEnrichmentPersistAdapterTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/enrichment/letpub/LetPubEnrichmentPersistAdapter.java \
        patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/enrichment/letpub/LetPubEnrichmentPersistAdapterTest.java
git commit -m "feat(catalog): 实现 LetPubEnrichmentPersistAdapter"
```

---

### Task 6: 创建 Scopus 的 `ScopusEnrichmentPersistPort` + Adapter

**Files:**
- Create: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/enrichment/ScopusEnrichmentPersistPort.java`
- Create: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/enrichment/scopus/ScopusEnrichmentPersistAdapter.java`
- Test: `patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/enrichment/scopus/ScopusEnrichmentPersistAdapterTest.java`

结构与 Task 4-5 完全平行，替换：
- `LetPubVenueData` → `ScopusVenueData`
- `LetPubDataMapper` → `ScopusDataMapper`
- JCR/CAS/Warning DAO → `ScopusRatingDao`（单一表）
- 无封面下载逻辑

PersistStats 简化为 `record PersistStats(int scopusRatingsInserted)`。

- [ ] **Step 1: 定义 `ScopusEnrichmentPersistPort`**

接口方法：`PersistStats persist(long venueId, ScopusVenueData data)`

- [ ] **Step 2: 写失败单测**（2 个 case：新 venue 插入全部 / 已有年份的 venue 过滤重复）

- [ ] **Step 3: 运行失败**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests ScopusEnrichmentPersistAdapterTest`
Expected: FAIL with "cannot find symbol"

- [ ] **Step 4: 实现 `ScopusEnrichmentPersistAdapter`**

从 `ScopusVenueItemWriter` 搬 `filterNewScopusRatings` 逻辑（按 `year` 去重，单一 DAO）。

- [ ] **Step 5: 运行通过 + Commit**

```bash
git add patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/enrichment/ScopusEnrichmentPersistPort.java \
        patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/enrichment/scopus/ScopusEnrichmentPersistAdapter.java \
        patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/enrichment/scopus/ScopusEnrichmentPersistAdapterTest.java
git commit -m "feat(catalog): 新增 ScopusEnrichmentPersist{Port,Adapter}"
```

---

## Phase B: App 层 Runner + Worker 实现（LetPub）

### Task 7: 创建 `LetPubEnrichmentWorker`（@Transactional 单 venue 处理）

**Files:**
- Create: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorker.java`
- Test: `patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorkerTest.java`

**职责**：处理单个 venue（抓数据 → 下载封面 → 调 PersistPort），用 `@Transactional(REQUIRES_NEW)` 限定事务边界。封面下载失败隔离逻辑从 `LetPubVenueItemProcessor.downloadCoverIfNeeded()` 原样搬过来。

**返回值**：`Outcome` 枚举 `{ PROCESSED, NOT_FOUND_IN_SOURCE, MISSING_ISSN }`，便于 Runner 统计。未捕获的异常向上抛，由 Runner 的 try/catch 计为 `failed`。

- [ ] **Step 1: 写失败单测**

```java
package com.patra.catalog.app.usecase.venue.letpub;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentWorker.Outcome;
import com.patra.catalog.domain.port.enrichment.*;
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;
import com.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("LetPubEnrichmentWorker 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class LetPubEnrichmentWorkerTest {

  @Mock LetPubEnrichmentPort scraperPort;
  @Mock LetPubEnrichmentPersistPort persistPort;
  @Mock VenueCoverImageDownloadPort coverPort;
  @Mock VenueEnrichmentReadPort readPort;

  LetPubEnrichmentWorker worker;

  @BeforeEach
  void setUp() {
    worker = new LetPubEnrichmentWorker(scraperPort, persistPort, coverPort, readPort);
  }

  @Test
  void processVenue_happyPath_returnsPROCESSED() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678");
    LetPubVenueData data = TestFixtures.letPubDataWithCoverUrl();
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.of(data));
    when(readPort.findExistingCoverKey(100L)).thenReturn(Optional.empty());
    when(coverPort.downloadAndStore(any(), eq("catalog/venue-cover/100.jpg")))
        .thenReturn("catalog/venue-cover/100.jpg");
    when(persistPort.persist(100L, data, "catalog/venue-cover/100.jpg"))
        .thenReturn(new LetPubEnrichmentPersistPort.PersistStats(10, 5, 2, true));

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.PROCESSED);
    verify(persistPort).persist(100L, data, "catalog/venue-cover/100.jpg");
  }

  @Test
  void processVenue_missingIssn_returnsMISSING_ISSN() {
    VenueSnapshot v = VenueSnapshot.of(100L, null);
    Outcome outcome = worker.processVenue(v);
    assertThat(outcome).isEqualTo(Outcome.MISSING_ISSN);
    verifyNoInteractions(scraperPort, persistPort);
  }

  @Test
  void processVenue_notFoundOnSource_returnsNOT_FOUND_IN_SOURCE() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678");
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.empty());

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.NOT_FOUND_IN_SOURCE);
    verifyNoInteractions(persistPort);
  }

  @Test
  void processVenue_coverDownloadFails_continuesWithoutCover() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678");
    LetPubVenueData data = TestFixtures.letPubDataWithCoverUrl();
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.of(data));
    when(readPort.findExistingCoverKey(100L)).thenReturn(Optional.empty());
    when(coverPort.downloadAndStore(any(), any()))
        .thenThrow(new com.patra.catalog.domain.exception.FileDownloadException("network"));
    when(persistPort.persist(eq(100L), eq(data), isNull()))
        .thenReturn(new LetPubEnrichmentPersistPort.PersistStats(1, 0, 0, false));

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.PROCESSED);
    verify(persistPort).persist(100L, data, null);
  }

  @Test
  void processVenue_scraperThrows_propagatesException() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678");
    when(scraperPort.findByIssn("1234-5678"))
        .thenThrow(new RuntimeException("rate limited"));

    assertThatThrownBy(() -> worker.processVenue(v))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("rate limited");
  }
}
```

- [ ] **Step 2: 运行失败**

Run: `./gradlew :patra-catalog:patra-catalog-app:test --tests LetPubEnrichmentWorkerTest`
Expected: FAIL

- [ ] **Step 3: 实现 Worker**

**关键注意点**：Worker 需要读取 venue 的现有 `imageObjectKey`（判断是否跳过下载）。有两个选项：
- **A**：注入 `VenueDao` 并调用 `findImageObjectKey(long)` —— 需要在 VenueDao 上新增该方法
- **B**：在 `VenueSnapshot` 里加上 `imageObjectKey` 字段 —— 侵入读端口

**选 A**：VenueDao 新增 `Optional<String> findImageObjectKey(Long venueId)`（简单投影查询）。App 层直接注入 Infra DAO 违反 hexagonal，但这是项目既存模式（LetPubVenueItemProcessor 原本也是读 Entity 字段）。**或者更干净**：在 `LetPubEnrichmentPersistPort` 上加一个 `shouldDownloadCover(long venueId)` 方法让 Adapter 代答 —— 但这个是持久化端口，查询 cover 语义不对。

**最干净方案**：在 `VenueEnrichmentReadPort` 上加 `Optional<String> findExistingCoverKey(long venueId)`，保持读端口职责单一。

**`findExistingCoverKey` 方法**已包含在 Task 2（ReadPort 接口）和 Task 3（ReadAdapter 实现 + IT 测试）中。Worker 直接注入 `VenueEnrichmentReadPort` 并调用即可。

Worker 实现代码：

```java
package com.patra.catalog.app.usecase.venue.letpub;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.enrichment.*;
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;
import com.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 处理单个 venue 的 LetPub 富化事务单元。
///
/// **为什么与 [LetPubEnrichmentRunner] 分离**：
///
/// Spring AOP 自调用不会触发代理，`Runner.run()` 内直接调 `this.processVenue()`
/// 不会激活 `@Transactional`。拆成两个 Spring bean，通过依赖注入跨类调用，
/// 才能让每个 venue 真正运行在独立事务里。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class LetPubEnrichmentWorker {

  private final LetPubEnrichmentPort scraperPort;
  private final LetPubEnrichmentPersistPort persistPort;
  private final VenueCoverImageDownloadPort coverImageDownloadPort;
  private final VenueEnrichmentReadPort readPort;

  public enum Outcome { PROCESSED, NOT_FOUND_IN_SOURCE, MISSING_ISSN }

  /// 处理单个 venue。**事务边界**：每次调用独占一个 REQUIRES_NEW 事务。
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
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
    String coverObjectKey = downloadCoverIfNeeded(venue.id(), data);
    LetPubEnrichmentPersistPort.PersistStats stats = persistPort.persist(venue.id(), data, coverObjectKey);

    log.info(
        "Venue [id={}, issn={}] LetPub 富化成功，JCR:{} CAS:{} Warning:{} cover:{}",
        venue.id(), venue.issnL(),
        stats.jcrInserted(), stats.casInserted(), stats.warningInserted(), stats.coverUpdated());
    return Outcome.PROCESSED;
  }

  private String downloadCoverIfNeeded(long venueId, LetPubVenueData data) {
    Optional<String> existing = readPort.findExistingCoverKey(venueId);
    if (existing.isPresent()) {
      log.debug("Venue [id={}] 已存在封面对象键，跳过下载", venueId);
      return null;
    }
    String sourceUrl = data.basicInfo().coverImageSourceUrl();
    if (sourceUrl == null || sourceUrl.isBlank()) return null;

    String stableKey = "catalog/venue-cover/" + venueId + ".jpg";
    URI sourceUri;
    try {
      sourceUri = URI.create(sourceUrl);
    } catch (IllegalArgumentException e) {
      log.warn("venue 封面 URL 格式非法（继续）: venueId={} sourceUrl={}", venueId, sourceUrl);
      return null;
    }
    try {
      return coverImageDownloadPort.downloadAndStore(sourceUri, stableKey);
    } catch (FileDownloadException e) {
      log.warn("venue 封面下载失败（继续）: venueId={} trait={} reason={}",
          venueId, e.getErrorTraits(), e.getMessage());
      return null;
    } catch (RuntimeException e) {
      log.warn("venue 封面下载意外异常（继续）: venueId={}", venueId, e);
      return null;
    }
  }
}
```

- [ ] **Step 4: 运行通过**

Run: `./gradlew :patra-catalog:patra-catalog-app:test --tests LetPubEnrichmentWorkerTest`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorker.java \
        patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentWorkerTest.java
git commit -m "feat(catalog): 新增 LetPubEnrichmentWorker（单 venue 事务单元）"
```

---

### Task 8: 创建 `LetPubEnrichmentRunner`（外循环）

**Files:**
- Create: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentRunner.java`
- Test: `patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentRunnerTest.java`

- [ ] **Step 1: 写失败单测**

```java
package com.patra.catalog.app.usecase.venue.letpub;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentRunner.RunStats;
import com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentWorker.Outcome;
import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("LetPubEnrichmentRunner 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class LetPubEnrichmentRunnerTest {

  @Mock VenueEnrichmentReadPort readPort;
  @Mock LetPubEnrichmentWorker worker;
  LetPubEnrichmentRunner runner;

  @BeforeEach
  void setUp() {
    runner = new LetPubEnrichmentRunner(readPort, worker);
  }

  @Test
  void run_emptyResultsFromStart_returnsZeroStats() {
    when(readPort.findNeedingLetPubEnrichment(anyShort(), anyInt(), anyLong(), anyInt()))
        .thenReturn(List.of());

    RunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.totalRead()).isZero();
    assertThat(stats.processed()).isZero();
  }

  @Test
  void run_singleBatch_processesAll() {
    List<VenueSnapshot> batch = List.of(
        VenueSnapshot.of(1L, "A"),
        VenueSnapshot.of(2L, "B"),
        VenueSnapshot.of(3L, "C"));
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50))
        .thenReturn(batch);
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 3L, 50))
        .thenReturn(List.of()); // empty on next keyset page
    when(worker.processVenue(any())).thenReturn(Outcome.PROCESSED);

    RunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.totalRead()).isEqualTo(3);
    assertThat(stats.processed()).isEqualTo(3);
    assertThat(stats.failed()).isZero();
    verify(worker, times(3)).processVenue(any());
  }

  @Test
  void run_workerThrowsOnOneVenue_othersContinue() {
    List<VenueSnapshot> batch = List.of(
        VenueSnapshot.of(1L, "A"),
        VenueSnapshot.of(2L, "B"),
        VenueSnapshot.of(3L, "C"));
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50))
        .thenReturn(batch);
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 3L, 50))
        .thenReturn(List.of());
    when(worker.processVenue(VenueSnapshot.of(1L, "A"))).thenReturn(Outcome.PROCESSED);
    when(worker.processVenue(VenueSnapshot.of(2L, "B"))).thenThrow(new RuntimeException("crash"));
    when(worker.processVenue(VenueSnapshot.of(3L, "C"))).thenReturn(Outcome.PROCESSED);

    RunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.totalRead()).isEqualTo(3);
    assertThat(stats.processed()).isEqualTo(2);
    assertThat(stats.failed()).isEqualTo(1);
  }

  @Test
  void run_skipsCountedByOutcome() {
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50))
        .thenReturn(List.of(
            VenueSnapshot.of(1L, null),          // MISSING_ISSN
            VenueSnapshot.of(2L, "B"),           // NOT_FOUND_IN_SOURCE
            VenueSnapshot.of(3L, "C")));         // PROCESSED
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 3L, 50))
        .thenReturn(List.of());
    when(worker.processVenue(VenueSnapshot.of(1L, null))).thenReturn(Outcome.MISSING_ISSN);
    when(worker.processVenue(VenueSnapshot.of(2L, "B"))).thenReturn(Outcome.NOT_FOUND_IN_SOURCE);
    when(worker.processVenue(VenueSnapshot.of(3L, "C"))).thenReturn(Outcome.PROCESSED);

    RunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.skipped()).isEqualTo(2);
    assertThat(stats.processed()).isEqualTo(1);
  }

  @Test
  void run_keysetAdvancesAcrossPages() {
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50))
        .thenReturn(List.of(VenueSnapshot.of(10L, "A"), VenueSnapshot.of(20L, "B")));
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 20L, 50))
        .thenReturn(List.of(VenueSnapshot.of(30L, "C")));
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 30L, 50))
        .thenReturn(List.of());
    when(worker.processVenue(any())).thenReturn(Outcome.PROCESSED);

    RunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.totalRead()).isEqualTo(3);
    verify(readPort).findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50);
    verify(readPort).findNeedingLetPubEnrichment((short) 2025, 0, 20L, 50);
    verify(readPort).findNeedingLetPubEnrichment((short) 2025, 0, 30L, 50);
  }
}
```

- [ ] **Step 2: 运行失败**

Run: `./gradlew :patra-catalog:patra-catalog-app:test --tests LetPubEnrichmentRunnerTest`
Expected: FAIL

- [ ] **Step 3: 实现 Runner**

```java
package com.patra.catalog.app.usecase.venue.letpub;

import com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentWorker.Outcome;
import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// LetPub 富化 worker loop 协调器。
///
/// **外循环职责**：
///
/// 1. 用 keyset pagination 分批读取需要富化的 venue（lastId 前进）
/// 2. 对每条调用 [LetPubEnrichmentWorker]（运行在独立 REQUIRES_NEW 事务里）
/// 3. 捕获任何异常并计入 failed，保证整批 run 不中断
/// 4. 返回完整统计
///
/// **为什么不用 @Transactional**：外循环跨 batch 和 venue，事务边界应由
/// Worker 在 venue 粒度管理。Runner 本身是纯循环协调。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class LetPubEnrichmentRunner {

  private static final int BATCH_SIZE = 50;

  private final VenueEnrichmentReadPort readPort;
  private final LetPubEnrichmentWorker worker;

  public RunStats run(short targetYear, int minCitedByCount) {
    log.info(
        "LetPub 富化 Runner 启动: targetYear={} minCitedByCount={} batchSize={}",
        targetYear, minCitedByCount, BATCH_SIZE);

    int total = 0, processed = 0, skipped = 0, failed = 0;
    long lastId = 0L;

    while (true) {
      List<VenueSnapshot> batch =
          readPort.findNeedingLetPubEnrichment(targetYear, minCitedByCount, lastId, BATCH_SIZE);
      if (batch.isEmpty()) break;

      for (VenueSnapshot v : batch) {
        total++;
        lastId = v.id();
        try {
          Outcome outcome = worker.processVenue(v);
          switch (outcome) {
            case PROCESSED -> processed++;
            case MISSING_ISSN, NOT_FOUND_IN_SOURCE -> skipped++;
            // default 分支防御未来 Outcome 扩展（如 RATE_LIMITED），触发运行时立即暴露
            default -> throw new IllegalStateException("Unhandled Worker Outcome: " + outcome);
          }
        } catch (Exception e) {
          // 合作式中断：worker.processVenue 不声明 throws InterruptedException，
          // 所以直接 IE 实例几乎不可能到达这里——真实路径是底层爬虫在 Thread.sleep
          // backoff 里捕获 IE 并包装成 RuntimeException 再抛出。必须同时检查 cause。
          if (e instanceof InterruptedException
              || e.getCause() instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            log.warn(
                "LetPub 富化 Runner 被中断: venueId={} processedSoFar={}", v.id(), processed);
            return RunStats.of(total, processed, skipped, failed);
          }
          failed++;
          log.warn("LetPub 富化失败（继续）: venueId={} reason={}", v.id(), e.getMessage());
        }
      }
    }

    log.info(
        "LetPub 富化 Runner 结束: total={} processed={} skipped={} failed={}",
        total, processed, skipped, failed);
    return RunStats.of(total, processed, skipped, failed);
  }

  /// LetPub 富化 run 的完整统计结果。
  ///
  /// @param totalRead Reader 读取的候选 venue 总数
  /// @param processed 成功完成富化的 venue 数
  /// @param skipped MISSING_ISSN / NOT_FOUND_IN_SOURCE 的 venue 数
  /// @param failed 抛异常被 Runner 捕获的 venue 数
  public record RunStats(int totalRead, int processed, int skipped, int failed) {

    /// 创建 [RunStats]。
    public static RunStats of(int totalRead, int processed, int skipped, int failed) {
      return new RunStats(totalRead, processed, skipped, failed);
    }
  }
}
```

- [ ] **Step 4: 运行通过**

Run: `./gradlew :patra-catalog:patra-catalog-app:test --tests LetPubEnrichmentRunnerTest`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentRunner.java \
        patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/LetPubEnrichmentRunnerTest.java
git commit -m "feat(catalog): 新增 LetPubEnrichmentRunner（worker loop 协调器）"
```

---

## Phase C: LetPub Handler 与 Result 契约

### Task 9: 定义 `VenueLetPubEnrichResult`

**Files:**
- Modify: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/command/VenueLetPubEnrichResult.java`

- [ ] **Step 1: 替换 Record 定义**

```java
package com.patra.catalog.app.usecase.venue.letpub.command;

/// LetPub 期刊富化命令执行结果。
///
/// @param totalRead Reader 读取的候选 venue 总数
/// @param processed 成功完成富化的 venue 数
/// @param skipped 被跳过的 venue 数（ISSN-L 为空 / LetPub 未找到）
/// @param failed 处理过程中抛异常的 venue 数
/// @author linqibin
/// @since 0.1.0
public record VenueLetPubEnrichResult(
    int totalRead, int processed, int skipped, int failed) {

  public static VenueLetPubEnrichResult of(int totalRead, int processed, int skipped, int failed) {
    return new VenueLetPubEnrichResult(totalRead, processed, skipped, failed);
  }
}
```

- [ ] **Step 2: 编译 app 模块会因 Handler 引用 `of(Long)` 而失败 —— 留到 Task 10 一起修复**

---

### Task 10: 实现 `VenueLetPubEnrichHandler`

**Files:**
- Modify: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/VenueLetPubEnrichHandler.java`
- Modify: `patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/VenueLetPubEnrichHandlerTest.java`

- [ ] **Step 1: 更新 Handler 测试**

```java
// 改 Mock：LetPubEnrichmentBatchPort → LetPubEnrichmentRunner
// 改断言：verify(runner).run(targetYear, minCited)
//        result.totalRead() / result.processed() / result.skipped() / result.failed()
```

单测至少覆盖：
1. 正常调用 → Runner 返回 stats → 转成 Result 返回
2. Runner 抛 `DomainException` → 直接传播
3. Runner 抛 `RuntimeException` → 包装成 `ApplicationException(CAT_1302)`

- [ ] **Step 2: 运行测试验证失败**

Run: `./gradlew :patra-catalog:patra-catalog-app:test --tests VenueLetPubEnrichHandlerTest`
Expected: FAIL（编译错或 mock 不匹配）

- [ ] **Step 3: 改写 Handler**

```java
package com.patra.catalog.app.usecase.venue.letpub;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichCommand;
import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichResult;
import com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentRunner.RunStats;
import com.patra.common.cqrs.CommandHandler;
import com.patra.common.error.ApplicationException;
import com.patra.common.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// LetPub 期刊富化命令处理器。
///
/// 委托 [LetPubEnrichmentRunner] 同步执行 worker loop。
///
/// **事务说明**：本方法**不使用** `@Transactional`——外层循环非事务，
/// 事务边界由 [LetPubEnrichmentWorker.processVenue]（REQUIRES_NEW）管理。
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueLetPubEnrichHandler
    implements CommandHandler<VenueLetPubEnrichCommand, VenueLetPubEnrichResult> {

  private final LetPubEnrichmentRunner runner;

  @Override
  public VenueLetPubEnrichResult handle(VenueLetPubEnrichCommand command) {
    log.info("启动 LetPub 富化: targetYear={} minCitedByCount={}",
        command.targetYear(), command.minCitedByCount());
    try {
      RunStats stats = runner.run(command.targetYear(), command.minCitedByCount());
      log.info("LetPub 富化完成: {}", stats);
      return VenueLetPubEnrichResult.of(
          stats.totalRead(), stats.processed(), stats.skipped(), stats.failed());
    } catch (DomainException | ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ApplicationException(CatalogErrorCode.CAT_1302, "LetPub 期刊富化失败: " + e.getMessage(), e);
    }
  }
}
```

- [ ] **Step 4: 运行测试通过**

Run: `./gradlew :patra-catalog:patra-catalog-app:test --tests VenueLetPubEnrichHandlerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/VenueLetPubEnrichHandler.java \
        patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/letpub/command/VenueLetPubEnrichResult.java \
        patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/letpub/VenueLetPubEnrichHandlerTest.java
git commit -m "feat(catalog): VenueLetPubEnrichHandler 实现与 Result 契约"
```

---

### Task 11: 接入 Controller 层并验证编译

**Files:**
- 先搜索：`./gradlew :patra-catalog:patra-catalog-adapter:compileJava`（会报错指出所有旧引用）
- 预期涉及：Controller、可能的 XXL-Job handler（若已有）

- [ ] **Step 1: 运行 adapter 模块编译定位失败点**

Run: `./gradlew :patra-catalog:patra-catalog-adapter:compileJava`
Expected: BUILD FAILED，列出所有引用 `executionId()` 的文件

- [ ] **Step 2: 修改每个引用**

把"返回 executionId 供追踪"的语义改成"返回同步统计"。如果 Controller 返回的 JSON 里有 `executionId` 字段，改成 `{totalRead, processed, skipped, failed}` 对象。

- [ ] **Step 3: 编译 adapter + 运行 adapter 测试**

Run: `./gradlew :patra-catalog:patra-catalog-adapter:test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add -A patra-catalog/patra-catalog-adapter/
git commit -m "feat(catalog): 接入 LetPub 富化到 Controller 层"
```

---

## Phase D: Scopus Worker + Runner + Handler 实现

### Task 12: 创建 `ScopusEnrichmentWorker`

**Files:**
- Create: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentWorker.java`
- Test: `patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentWorkerTest.java`

结构与 Task 7 的 LetPubEnrichmentWorker 完全平行，差异：
- 无封面下载（省 3 个依赖）
- 注入 `ScopusEnrichmentPort` + `ScopusEnrichmentPersistPort`
- Outcome 同样是 `{ PROCESSED, NOT_FOUND_IN_SOURCE, MISSING_ISSN }`

- [ ] **Step 1-5: TDD 周期**

4 个测试 case：happy path / missing ISSN / not found / scraper throws

---

### Task 13: 创建 `ScopusEnrichmentRunner`（含 400ms 限速）

**Files:**
- Create: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentRunner.java`
- Test: `patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/venue/scopus/ScopusEnrichmentRunnerTest.java`

**关键差异**：Scopus Serial Title API 免费版有 2-3 req/s 速率限制。Runner 必须在连续两次 `worker.processVenue()` 之间插入 400ms 停顿（首次调用前不等待），才能确保整体 < 2.5 req/s。

**限速实现策略：protected 方法模板模式**，NOT 独立的 `RateLimiter` 工具类。理由：
- 独立 `RateLimiter` 类若作为 Runner 字段会导致 **singleton 状态泄漏**（`lastCallAt` 字段跨 `run()` 调用共享，并发/重入时计算错位）
- Protected 方法模板把限速行为放在 Runner 类内部，状态是**方法局部**的（`firstVenue` 标志），天然线程安全
- 测试通过**匿名子类重写** `doRateLimitSleep()` 避免真实 sleep，`AtomicInteger` 计数

**Runner 实现骨架**（与 `LetPubEnrichmentRunner` 完全对称，差异仅为限速钩子）：

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ScopusEnrichmentRunner {

  private static final int BATCH_SIZE = 50;
  /// Scopus Serial Title API 免费版速率限制（约 2.5 req/s）
  private static final long RATE_LIMIT_INTERVAL_MS = 400;

  private final VenueEnrichmentReadPort readPort;
  private final ScopusEnrichmentWorker worker;

  public RunStats run(short targetYear, int minCitedByCount) {
    log.info(
        "Scopus 富化 Runner 启动: targetYear={} minCitedByCount={} batchSize={} rateLimitMs={}",
        targetYear, minCitedByCount, BATCH_SIZE, RATE_LIMIT_INTERVAL_MS);

    int total = 0, processed = 0, skipped = 0, failed = 0;
    long lastId = 0L;
    boolean firstVenue = true;

    while (true) {
      List<VenueSnapshot> batch =
          readPort.findNeedingScopusEnrichment(targetYear, minCitedByCount, lastId, BATCH_SIZE);
      if (batch.isEmpty()) break;

      for (VenueSnapshot v : batch) {
        total++;
        lastId = v.id();

        // Rate limit: 除首次外每条间隔 400ms
        if (!firstVenue) {
          try {
            doRateLimitSleep();
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Scopus 富化 Runner 被中断: venueId={} processedSoFar={}", v.id(), processed);
            return RunStats.of(total, processed, skipped, failed);
          }
        }
        firstVenue = false;

        try {
          Outcome outcome = worker.processVenue(v);
          switch (outcome) {
            case PROCESSED -> processed++;
            case MISSING_ISSN, NOT_FOUND_IN_SOURCE -> skipped++;
            default ->
                throw new IllegalStateException("Unhandled Scopus Worker Outcome: " + outcome);
          }
        } catch (Exception e) {
          // 合作式中断：worker.processVenue 不声明 throws InterruptedException，
          // 所以真实路径是底层 HTTP client 在 Thread.sleep backoff 里把 IE 包装成
          // RuntimeException。必须同时检查 cause 才能正确识别。
          if (e instanceof InterruptedException
              || e.getCause() instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            log.warn("Scopus 富化 Runner 被中断: venueId={} processedSoFar={}", v.id(), processed);
            return RunStats.of(total, processed, skipped, failed);
          }
          failed++;
          log.warn("Scopus 富化失败（继续）: venueId={} reason={}", v.id(), e.getMessage());
        }
      }
    }

    log.info(
        "Scopus 富化 Runner 结束: total={} processed={} skipped={} failed={}",
        total, processed, skipped, failed);
    return RunStats.of(total, processed, skipped, failed);
  }

  /// 限速钩子：默认 `Thread.sleep(RATE_LIMIT_INTERVAL_MS)`，测试可匿名子类重写。
  ///
  /// 设计理由：避免独立 `RateLimiter` 类作为 singleton 字段导致 `lastCallAt` 状态跨
  /// `run()` 调用泄漏。protected 方法模板把限速行为内聚在 Runner 内部，状态只在
  /// `run()` 方法局部（`firstVenue` 标志），天然线程安全。
  ///
  /// @throws InterruptedException 若线程被中断（由调用方恢复 interrupt flag 并提前返回）
  protected void doRateLimitSleep() throws InterruptedException {
    Thread.sleep(RATE_LIMIT_INTERVAL_MS);
  }

  /// Scopus 富化 run 的完整统计结果。
  public record RunStats(int totalRead, int processed, int skipped, int failed) {
    public static RunStats of(int totalRead, int processed, int skipped, int failed) {
      return new RunStats(totalRead, processed, skipped, failed);
    }
  }
}
```

**测试结构**（6 个测试，5 个继承自 LetPub + 1 个专测限速）：

```java
@DisplayName("ScopusEnrichmentRunner 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class ScopusEnrichmentRunnerTest {

  @Mock VenueEnrichmentReadPort readPort;
  @Mock ScopusEnrichmentWorker worker;
  ScopusEnrichmentRunner runner;
  AtomicInteger sleepCallCount;

  @BeforeEach
  void setUp() {
    sleepCallCount = new AtomicInteger();
    runner = new ScopusEnrichmentRunner(readPort, worker) {
      @Override
      protected void doRateLimitSleep() {
        // 跳过真实 sleep，只计数
        sleepCallCount.incrementAndGet();
      }
    };
  }

  // Test 1-5: 与 LetPubEnrichmentRunnerTest 的 5 个测试完全对称
  //   - run_emptyResultsFromStart_returnsZeroStats
  //   - run_singleBatch_processesAll
  //   - run_workerThrowsOnOneVenue_othersContinue
  //   - run_skipsCountedByOutcome
  //   - run_keysetAdvancesAcrossPages
  // （换 readPort 调用名为 findNeedingScopusEnrichment）

  @Test
  @DisplayName("Rate limit - 3 个 venue 之间 sleep 被调用 2 次（首条不等）")
  void run_rateLimit_sleepsBetweenVenues() {
    List<VenueSnapshot> batch = List.of(
        VenueSnapshot.of(1L, "A"),
        VenueSnapshot.of(2L, "B"),
        VenueSnapshot.of(3L, "C"));
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 0L, 50)).thenReturn(batch);
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 3L, 50)).thenReturn(List.of());
    when(worker.processVenue(any())).thenReturn(Outcome.PROCESSED);

    runner.run((short) 2025, 0);

    // 3 个 venue，只有首条不等 → 第 2 条前等 + 第 3 条前等 = 2 次
    assertThat(sleepCallCount.get()).isEqualTo(2);
  }
}
```

**为什么匿名子类而不是 `@Mock` 或 `@Spy`**：
- `@Mock` 只能 mock 类依赖注入点，无法替换方法实现
- `@Spy` 能 partial mock，但要求正确拦截 protected 方法，配置比匿名子类啰嗦
- 匿名子类是 Java 标准的 template method override 模式，与 `@BeforeEach` 手动构造 runner 的约定一致

---

### Task 14: `VenueScopusEnrichResult` 与 Handler 实现

**Files:**
- Modify: `patra-catalog-app/.../venue/scopus/command/VenueScopusEnrichResult.java`
- Modify: `patra-catalog-app/.../venue/scopus/VenueScopusEnrichHandler.java`
- Modify: test

与 Task 9 + 10 结构相同，换成 Scopus。错误码保持 `CAT_1303`。

- [ ] **Step 1-5**: 改 Result 形状、切 Handler、改单测、编译 adapter 模块、修 Controller 引用、Commit

---

## Phase E: 清理未使用代码

### Task 15: 清理 LetPub 批处理组件

**Files to delete:**
```
patra-catalog-infra/.../batch/venue/letpub/LetPubEnrichmentJobConfig.java
patra-catalog-infra/.../batch/venue/letpub/LetPubVenueItemProcessor.java
patra-catalog-infra/.../batch/venue/letpub/LetPubVenueItemWriter.java
patra-catalog-infra/.../batch/venue/letpub/LetPubEnrichResult.java
patra-catalog-infra/.../batch/venue/letpub/LetPubEnrichmentBatchAdapter.java
patra-catalog-infra/src/test/.../batch/venue/letpub/LetPubVenueItemProcessorTest.java
patra-catalog-infra/src/test/.../batch/venue/letpub/LetPubVenueItemWriterTest.java
patra-catalog-domain/.../port/batch/LetPubEnrichmentBatchPort.java
```

**KEEP**：
- `LetPubDataMapper.java`（还被 PersistAdapter 用）
- `LetPubDataMapperTest.java`（在飞修改，不动）

**决定**：`LetPubDataMapper` 当前位置是 `infra/batch/venue/letpub/`。删除该包下其它文件后，`LetPubDataMapper` 成为这个包下的唯一类。留在原位不动（不要移动位置以免破坏在飞测试）。

- [ ] **Step 1: 执行删除**

Run: `git rm <每个文件>`

- [ ] **Step 2: 编译 infra 模块**

Run: `./gradlew :patra-catalog:patra-catalog-infra:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 运行 infra 所有测试**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test`
Expected: PASS（包括在飞修改的 LetPubDataMapperTest / LetPubDetailPageParserTest / LetPubEnrichmentAdapterIT）

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(catalog): 清理未使用的 LetPub 批处理组件"
```

---

### Task 16: 清理 Scopus 批处理组件

**Files to delete:**
```
patra-catalog-infra/.../batch/venue/scopus/ScopusEnrichmentJobConfig.java
patra-catalog-infra/.../batch/venue/scopus/ScopusVenueItemProcessor.java
patra-catalog-infra/.../batch/venue/scopus/ScopusVenueItemWriter.java
patra-catalog-infra/.../batch/venue/scopus/ScopusEnrichResult.java
patra-catalog-infra/.../batch/venue/scopus/ScopusEnrichmentBatchAdapter.java
patra-catalog-infra/src/test/.../batch/venue/scopus/ScopusVenueItemProcessorTest.java
patra-catalog-infra/src/test/.../batch/venue/scopus/ScopusDataMapperTest.java (除非保留)
patra-catalog-domain/.../port/batch/ScopusEnrichmentBatchPort.java
```

**KEEP**：`ScopusDataMapper.java`（被 PersistAdapter 使用）+ `ScopusDataMapperTest.java`

- [ ] **Step 1-4**: 同 Task 15

---

### Task 17: 清理共用批处理文件

**Files to delete:**
- `patra-catalog-infra/.../batch/venue/VenueEnrichmentJobParams.java`
- `patra-catalog-domain/.../port/batch/` 目录（若已空）

- [ ] **Step 1: 删除文件**

- [ ] **Step 2: 全量构建验证**

Run: `./gradlew :patra-catalog:build`
Expected: BUILD SUCCESSFUL（全部测试通过）

- [ ] **Step 3: Commit**

```bash
git commit -m "chore(catalog): 移除 VenueEnrichmentJobParams 及空目录"
```

---

### Task 18: 检查 starter-batch 依赖

**目的**：确认 starter-batch 仍被保留的 5 个 ETL Job 使用，无遗留无用依赖。

- [ ] **Step 1: 搜索 starter-batch 引用**

Run: Grep `com.patra.starter.batch` in `patra-catalog/patra-catalog-infra/src/main/java`
Expected: 5 处引用（Author / Mesh / Organization / Publication 的 BatchAdapter + JobOperatorHelper）

- [ ] **Step 2: 无操作**，仅确认

- [ ] **Step 3: 若无需求，跳过 Commit**

---

## Phase F: Patra-docs 知识库重塑

> **绿地原则**：Patra-docs 最终状态必须读起来像 worker loop 从 day 1 就是唯一设计。不保留"曾经用过 Spring Batch"的任何痕迹（无 `superseded`、无 `historical`、无迁移对比）。本 Phase 执行两个动作：(1) 删除 Phase 3 写的批处理专题文档——它们描述的是一个**已不存在**的设计；(2) 在相同目录下从零写新的"富化运行时"专题，描述 Runner/Worker 架构。

**Patra-docs 仓库位置**：`/Users/linqibin/Desktop/Patra/Patra-docs`，venue 知识库位于 `content/catalog/venue/`。

### Task 19: 清理过时的批处理专题文档

**Files to delete:**
```
content/catalog/venue/decisions/ADR-004-batch-chunk-size-one.md
content/catalog/venue/decisions/ADR-005-writer-dedup-strategy.md
content/catalog/venue/30-batch-job-orchestration.md
content/catalog/venue/31-batch-chunk-size-tradeoff.md
content/catalog/venue/32-batch-idempotency.md
content/catalog/venue/33-batch-error-handling.md
content/catalog/venue/canvas/03-batch-flow.canvas
```

**理由**：这 7 个文件全部以"LetPub/Scopus 使用 Spring Batch"为前提。代码层已改为 Worker loop 架构，这些文档描述的设计在仓库里不存在——保留会让读者困惑。

`30-batch-job-orchestration.md` 原本讲 Spring Batch 元数据表机制——这部分内容属于保留的 5 个 ETL Job（Author/Mesh/Organization/Publication），但它们是**非 venue 范畴**，不应在 venue 知识库里。ETL Job 的 Spring Batch 机制如需文档化，应在另一个专题下处理，不在本计划范围。

- [ ] **Step 1: 执行删除**

```bash
cd /Users/linqibin/Desktop/Patra/Patra-docs
git rm content/catalog/venue/decisions/ADR-004-batch-chunk-size-one.md \
       content/catalog/venue/decisions/ADR-005-writer-dedup-strategy.md \
       content/catalog/venue/30-batch-job-orchestration.md \
       content/catalog/venue/31-batch-chunk-size-tradeoff.md \
       content/catalog/venue/32-batch-idempotency.md \
       content/catalog/venue/33-batch-error-handling.md \
       content/catalog/venue/canvas/03-batch-flow.canvas
```

- [ ] **Step 2: 全文扫描破损的 wiki-link**

Run: Grep `[[30-batch-job-orchestration|` `[[31-batch-chunk-size-tradeoff|` `[[32-batch-idempotency|` `[[33-batch-error-handling|` `[[decisions/ADR-004-batch-chunk-size-one|` `[[decisions/ADR-005-writer-dedup-strategy|` `[[canvas/03-batch-flow|` in `content/catalog/venue/`
Expected: 若有命中，记录下来——Task 22 会统一修复

- [ ] **Step 3: 尚不 commit**——等 Task 20-22 写完后统一一个提交

---

### Task 20: 新写 `ADR-004-worker-loop-for-venue-enrichment.md`

**Files:**
- Create: `/Users/linqibin/Desktop/Patra/Patra-docs/content/catalog/venue/decisions/ADR-004-worker-loop-for-venue-enrichment.md`

**内容框架**（按 ADR 标准结构写）：

- **frontmatter**: `type: adr` / `adr_status: accepted` / `domain: catalog` / `entity: venue` / `date: 2026-04-11`
- **背景**: Venue 富化（LetPub + Scopus）是 I/O bound 工作负载——每条目标 8-10s（LetPub 爬取）或 400ms（Scopus API），单次 run 处理数千条 venue。需要一个能"逐条读取 → 外部调用 → 持久化 → 独立事务"的执行模型
- **考虑的选项**（给出完整推理）:
  - **Option A: Spring Batch chunk=1 + faultTolerant + skipAll**——被拒绝的反方案。优点：现成框架，JobRepository 元数据表；缺点：chunk-oriented ETL 模型不匹配 worker queue 工作负载，chunk=1 是对框架的"曲用"，Reader/Processor/Writer 拆分让"一条 venue 的处理链"被分散到三处，故障隔离需要 `faultTolerant+skip(Exception.class)+skipLimit(MAX_VALUE)`——本质是"用一组权变参数把框架配成一个退化的循环"。附带负担：Spring Batch 元数据表 4 张、StepScope 晚绑定、Reader 分页和 UK 冲突等一系列可避免的复杂度
  - **Option B: Worker loop（采纳）**——App 层 Runner 循环读取 + Worker `@Transactional(REQUIRES_NEW)` 单 venue 处理。ReadPort 负责 keyset pagination + NOT EXISTS 动态守卫，PersistPort 负责 Mapper + 去重 + DAO 写入
  - **Option C: Kafka / RocketMQ 消息队列** —— 被拒。优点：横向扩展容易；缺点：引入 MQ 基础设施，消费者只有单机就没必要；失败 venue 需要死信队列 + 手工处理，违背"数据库本身就是失败清单"原则
- **决策**: 采纳 Option B。核心理由：工作负载本质是 worker queue（串行、I/O bound、独立事务），不是 chunk-oriented ETL
- **后果**:
  - **正面**: 代码复杂度显著下降（无 Spring Batch 4 张元数据表 / 无 `@StepScope` 晚绑定 / 无 `chunk-oriented` Reader-Processor-Writer 拆分）；单测只需 Mock 2 个依赖（ReadPort + Worker），不需要 Spring Batch 测试框架
  - **负面**: 没有 `JobRepository` 内置的运行历史表——需要依赖 Runner 返回的 `RunStats` + 日志 + 通过 Micrometer 暴露指标
- **约束条件**:
  - 适用于"I/O bound + 单机执行 + 数据库即幂等守卫"的工作负载
  - 不适用于需要并发处理 / 分布式执行 / CPU bound 的 ETL 场景——后者仍应用 Spring Batch
- **关联资料**: [[31-worker-loop-design]] [[32-enrichment-idempotency]] [[33-error-handling]]

- [ ] **Step 1: 撰写 ADR 内容**（400-600 字）

- [ ] **Step 2: 暂存**，不 commit

---

### Task 21: 新写 30-block 三篇文档（替换原批处理专题）

**Files:**
- Create: `content/catalog/venue/30-enrichment-runtime.md` — 运行时总览
- Create: `content/catalog/venue/31-worker-loop-design.md` — Runner/Worker 架构详解
- Create: `content/catalog/venue/32-enrichment-idempotency.md` — keyset + NOT EXISTS 幂等机制
- Create: `content/catalog/venue/33-error-handling.md` — 错误处理与跨 run 自愈

**每篇文档的职责和大纲**：

#### `30-enrichment-runtime.md`（150-200 行）

- **定位**: 一句话——"所有 venue 富化 Job 都运行在一个轻量 worker loop 上，由 App 层 Runner 协调 + Worker 管理事务"
- **核心章节**:
  1. 运行时栈：CommandBus → Handler → Runner → Worker → PersistPort → DAO
  2. 三层角色职责对比表：Runner（循环、非事务）/ Worker（单 venue、REQUIRES_NEW 事务）/ PersistPort（Mapper + 去重 + DAO）
  3. 为什么拆 Runner + Worker：Spring AOP 自调用不触发代理，必须跨 bean 才能让 `@Transactional` 生效（附 Mermaid 示意）
  4. Batch size 50 的选择依据：keyset pagination 的数据库往返次数 vs 内存占用权衡
- frontmatter: `type: design` / `status: active`
- 关联 ADR: `[ADR-004]`

#### `31-worker-loop-design.md`（200-250 行）

- **定位**: Runner + Worker 的详细设计
- **核心章节**:
  1. 外循环时序（Mermaid sequence diagram）：Runner 分批读取 → Worker 处理 → 异常隔离 → keyset 前进
  2. Worker 的 5 种 Outcome（PROCESSED / NOT_FOUND_IN_SOURCE / MISSING_ISSN / 异常）及统计语义
  3. 事务边界严格控制在 venue 粒度的理由：封面下载不回滚、爬虫调用不回滚（I/O 已发生）、评级行全部回滚
  4. 并发模型：当前单线程串行，未来如需并发只需在 Runner 里换成线程池——worker 本身已经是"无状态可并发单元"
- frontmatter: `type: design` / `status: active`

#### `32-enrichment-idempotency.md`（200-250 行）

- **定位**: 为什么这个设计天然幂等
- **核心章节**:
  1. 两层叠加：`NOT EXISTS` 动态收敛 + keyset `id > lastId` 游标前进
  2. 动态收敛机制：成功 venue 写入数据后，下一批次的候选集合自动缩减——数据库本身就是"失败清单 + 重试队列"
  3. keyset 游标避免当次 run 内死循环：失败 venue 在当次 run 不被重捞，但下次 Job 调度时 `lastId` 从 0 重置自动重试
  4. (venue_id, year[:edition]) 去重：LetPub 多年趋势 / CAS 多版本的机会主义填充机制
  5. 对比反例：Spring Batch 的 chunk+NOT EXISTS+Writer 过滤组合（作为反面教材简述——但不提"我们曾经用过它"）
- frontmatter: `type: design` / `status: active`

#### `33-error-handling.md`（150-200 行）

- **定位**: 三层错误处理栈 + 跨 run 自愈
- **核心章节**:
  1. Layer 1 (Client proxy retry): 2s → 5s 瞬时抖动
  2. Layer 2 (Client rate-limit backoff): 30s → 60s → 120s → 240s 软限流
  3. Layer 3 (Runner try/catch): 单条失败计入 `failed`，继续下一条
  4. 跨 Job 自愈原理：`NOT EXISTS` 守卫让失败的 venue 在下次 Job 调度时自动再次进入候选集
  5. 监控 checklist：`failed / totalRead` 比例告警阈值
- frontmatter: `type: design` / `status: active`

- [ ] **Step 1: 撰写 30-enrichment-runtime.md**
- [ ] **Step 2: 撰写 31-worker-loop-design.md**
- [ ] **Step 3: 撰写 32-enrichment-idempotency.md**
- [ ] **Step 4: 撰写 33-error-handling.md**
- [ ] **Step 5: 暂存**，不 commit

---

### Task 22: 更新现有文档的交叉引用 + Canvas 重绘

**Files:**
- Modify: `content/catalog/venue/_MOC.md`
- Modify: `content/catalog/venue/03-enrichment-pipeline.md`
- Modify: `content/catalog/venue/21-pipeline-letpub.md`
- Modify: `content/catalog/venue/canvas/01-enrichment-pipeline.canvas`
- Modify: `content/catalog/venue/decisions/_MOC.md`

- [ ] **Step 1: 更新 `_MOC.md` 的"批处理"小节**

旧结构（Phase 3 写的）：
```markdown
## 批处理

- [[30-batch-job-orchestration|30 · Spring Batch 内部机制]]
- [[31-batch-chunk-size-tradeoff|31 · 为什么 chunk=1]]
- [[32-batch-idempotency|32 · 批处理幂等策略]]
- [[33-batch-error-handling|33 · 错误处理与监控]]
- [[canvas/03-batch-flow|Canvas #3 · 批处理时序]]
```

新结构：
```markdown
## 富化运行时

- [[30-enrichment-runtime|30 · 运行时总览]]
- [[31-worker-loop-design|31 · Runner/Worker 架构]]
- [[32-enrichment-idempotency|32 · 幂等机制]]
- [[33-error-handling|33 · 错误处理]]
```

同时修改 `_MOC.md` 顶部的 Phase 状态行（"Phase 3 已完成" → 改为适配新设计的措辞，或直接去掉 Phase 说明）。

- [ ] **Step 2: 更新 `03-enrichment-pipeline.md`**

原文把 "dispatch 三跳" 描述为：`Handler → BatchPort → Spring Batch Job`。改为：`Handler → Runner → Worker → PersistPort`。删除所有 Spring Batch 相关 Mermaid 节点，替换为 Worker loop 图。

- [ ] **Step 3: 更新 `21-pipeline-letpub.md`**

`21-` 是 LetPub 爬虫深度文档。原文结尾处有一节讲"Batch Job 配置"——整节删除。保留爬虫 Port 契约、两套重试模型、异常分类表等（这些是爬虫本身的设计，和运行时模型无关）。

- [ ] **Step 4: 重绘 `canvas/01-enrichment-pipeline.canvas`**

原 Canvas #1 的五组结构：触发源 / 编排层 / 富化通道 / 数据存储 / 查询暴露。需要修改的是"编排层"组内的卡片：把 "BatchPort + Spring Batch Job" 卡片换成 "Runner + Worker" 卡片。其他组保持不动。

- [ ] **Step 5: 更新 `decisions/_MOC.md`**

删除 ADR-004 / ADR-005 的旧行，新增 ADR-004（worker loop）行。

- [ ] **Step 6: 全量 wiki-link 校验**

Run: 用 Obsidian 或 grep 扫一遍是否还有指向已删除文档的 wiki-link，有则修正

- [ ] **Step 7: Commit Patra-docs 所有变更**

```bash
cd /Users/linqibin/Desktop/Patra/Patra-docs
git add -A content/catalog/venue/
git commit -m "docs(catalog/venue): 富化运行时专题（worker loop 架构）"
```

---

---

## 全量验证

### Task 23: 端到端构建与测试

- [ ] **Step 1**: 全量编译

Run: `./gradlew :patra-catalog:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2**: 运行完整测试套件

Run: `./gradlew :patra-catalog:test`
Expected: 所有测试通过，包括在飞修改的 LetPub 反爬相关测试

- [ ] **Step 3**: 确认无 Spring Batch 残留

Grep：`LetPubEnrichmentJob` / `ScopusEnrichmentJob` / `LetPubEnrichmentBatchPort` / `ScopusEnrichmentBatchPort` / `VenueEnrichmentJobParams`
Expected: 无匹配

- [ ] **Step 4**: 人工 review git log

Run: `git log --oneline main..HEAD`
Expected: 清晰的 commit 序列，按 Phase 分组

---

## 最终交付物

**代码侧**（全部位于 `patra-catalog/`）：
- `LetPubEnrichmentRunner` + `LetPubEnrichmentWorker`（App 层）
- `ScopusEnrichmentRunner` + `ScopusEnrichmentWorker`（App 层，Runner 内置 400ms 限速器）
- `VenueEnrichmentReadPort` + `VenueEnrichmentReadAdapter`（Domain + Infra，keyset + NOT EXISTS）
- `LetPubEnrichmentPersistPort` + `LetPubEnrichmentPersistAdapter`（Domain + Infra）
- `ScopusEnrichmentPersistPort` + `ScopusEnrichmentPersistAdapter`（Domain + Infra）
- `VenueLetPubEnrichHandler` / `VenueScopusEnrichHandler` 正式实现
- `VenueSnapshot` 值记录（Domain）
- 配套单测 + IT 完整覆盖

**清理掉的过渡代码**（不出现在最终 git log 的 feat 提交中，单独 chore 提交归档）：
- LetPub / Scopus 的 Spring Batch Job 配置 + Processor + Writer + BatchAdapter + BatchPort
- `VenueEnrichmentJobParams`

**文档侧**（Patra-docs/content/catalog/venue/）：
- `ADR-004-worker-loop-for-venue-enrichment.md`（新）
- `30-enrichment-runtime.md` / `31-worker-loop-design.md` / `32-enrichment-idempotency.md` / `33-error-handling.md`（新 30-block 专题）
- 更新的 `_MOC.md` / `03-enrichment-pipeline.md` / `21-pipeline-letpub.md` / `canvas/01-enrichment-pipeline.canvas`
- 删除的过时文档：旧 ADR-004 / ADR-005 / 30-33 批处理专题 / Canvas #3

**预期规模**：
- 新增代码：~650 行（Runner + Worker + Adapter + Port + 测试）
- 清理代码：~1100 行（旧 Spring Batch Job 栈 + 对应测试）
- **净减少：~450 行**
- Commit 数：~18 个（按 task 粒度，feat / chore 分离）
