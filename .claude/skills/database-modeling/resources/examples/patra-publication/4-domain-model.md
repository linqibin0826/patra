# 阶段 4：领域模型映射 - Patra 采集计划管理示例

> **生成说明**：根据阶段 3 的 SQL DDL，生成符合六边形架构 + DDD 的领域模型

---

## 🏗️ 架构分层

```
┌─────────────────────────────────────┐
│ Domain 层（patra-ingest-domain）     │
│ ✅ 无基础设施框架依赖                 │
│ ✅ 可使用: Hutool、Jackson、Lombok   │
│ ✅ 聚合根、值对象、枚举                │
└─────────────────────────────────────┘
              ↑ 依赖倒置
┌─────────────────────────────────────┐
│ Infra 层（patra-ingest-infra）       │
│ ✅ DO（继承 BaseDO）                  │
│ ✅ Mapper（BaseMapper）               │
│ ✅ RepositoryAdapter（适配器）           │
│ ✅ Converter（Entity ↔ DO）          │
└─────────────────────────────────────┘
```

**依赖说明**：
- Domain 层默认依赖 `patra-common-core`，可使用 Hutool-Core 和 Jackson
- 禁止依赖基础设施框架（Spring、MyBatis、Redis 等）
- Hutool 用于工具方法，Jackson 用于序列化

---

## 📦 聚合识别

### 聚合根：PlanAggregate（采集计划）

**聚合边界：**
- **聚合根**：PlanAggregate
- **值对象**：WindowSpec（窗口规范，支持 TIME/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/SINGLE）
- **枚举**：ProvenanceCode（数据来源）、OperationCode（操作类型）、PlanStatus（计划状态）
- **不变量**：
  - planKey 必须唯一（保证同一业务场景的计划幂等性）
  - windowSpec 在整个生命周期中保持不可变
  - 状态转换必须遵循预定义的状态机规则

**职责：**
- 管理数据采集计划的生命周期（DRAFT → SLICING → READY → ARCHIVED）
- 封装窗口规范、表达式快照和配置快照
- 提供状态转换接口（startSlicing、markReady、updateStatus）
- 维护业务幂等键（planKey = hash(provenance + operation + window + strategy)）

**业务规则：**
- 计划创建时处于 DRAFT 状态
- 切片生成开始时转换为 SLICING 状态
- 所有切片生成完成后转换为 READY 状态
- 根据任务执行结果聚合，最终转换为 ARCHIVED 状态

**状态转换：**
```
DRAFT → SLICING: 开始切片生成
SLICING → READY: 所有切片生成完成
READY → ARCHIVED: 所有任务执行完成
```

---

## 📂 Domain 层代码结构

```
patra-ingest-domain/
└── src/main/java/com/patra/ingest/domain/
    ├── model/
    │   ├── aggregate/
    │   │   └── PlanAggregate.java            # 聚合根
    │   ├── vo/
    │   │   └── plan/
    │   │       └── WindowSpec.java           # 值对象（Sealed Interface）
    │   └── enums/
    │       ├── OperationCode.java            # 枚举（操作类型）
    │       ├── PlanStatus.java               # 枚举（计划状态）
    │       └── SliceStrategy.java            # 枚举（切片策略）
    └── port/
        └── PlanRepository.java               # 仓储接口
```

**注**：`ProvenanceCode` 位于 `patra-common-core` 模块中，是跨领域共享的枚举。

---

## 💻 Domain 层代码示例

### 1. 聚合根：PlanAggregate.java

```java
package com.patra.ingest.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import lombok.Getter;
import java.time.Instant;

/// 采集计划聚合根。封装单个数据采集计划的蓝图及其状态机流转。
/// 
/// 一致性边界：
/// 
/// - 计划的窗口规范、表达式快照、配置快照在整个生命周期中保持不可变
///   - 状态转换必须遵循预定义的状态机规则
///   - 计划键 (planKey) 保证同一业务场景的计划幂等性
/// 
/// 业务规则：
/// 
/// - 计划创建时处于 `DRAFT` 状态，包含窗口边界、切片策略和配置快照
///   - 切片生成开始时转换为 `SLICING` 状态
///   - 所有切片生成完成后转换为 `READY` 状态，准备任务调度
///   - 根据任务执行结果聚合，最终转换为 `ARCHIVED` 状态
///   - 计划键 = hash(provenance + operation + window + strategy) 确保幂等性
/// 
/// @author linqibin
/// @since 0.1.0
@Getter
public class PlanAggregate {

  /// 主键标识
  private Long id;

  /// 关联的调度实例标识（外部触发源）。
  private final Long scheduleInstanceId;

  /// 业务幂等键，用于计划去重（hash(provenance + operation + window + strategy)）。
  private final String planKey;

  /// 数据来源代码（如：pubmed、epmc）。
  private final ProvenanceCode provenanceCode;

  /// 操作类型（全量采集、增量采集、补偿采集等）。
  private final OperationCode operationCode;

  /// 表达式原型哈希值，用于变更检测。
  private final String exprProtoHash;

  /// 表达式原型快照（JSON 格式，编译前的原始表达式）。
  private final String exprProtoSnapshotJson;

  /// 数据来源配置快照（执行时捕获的不可变配置）。
  private final String provenanceConfigSnapshotJson;

  /// 数据来源配置哈希值，用于变更检测。
  private final String provenanceConfigHash;

  /// 窗口边界规范（支持 TIME/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/SINGLE 策略）。
  private final WindowSpec windowSpec;

  /// 切片策略代码（如：TIME、DATE、SINGLE）。
  private final String sliceStrategyCode;

  /// 切片策略参数 JSON 载荷。
  private final String sliceParamsJson;

  /// 计划当前状态。
  private PlanStatus status;

  /// 乐观锁版本
  private Long version;

  private PlanAggregate(
      Long id,
      Long scheduleInstanceId,
      String planKey,
      ProvenanceCode provenanceCode,
      OperationCode operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson,
      PlanStatus status) {
    this.id = id;
    Assert.notNull(scheduleInstanceId, "scheduleInstanceId must not be null");
    Assert.notNull(planKey, "planKey must not be null");
    Assert.notNull(windowSpec, "windowSpec must not be null");

    this.scheduleInstanceId = scheduleInstanceId;
    this.planKey = planKey;
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.exprProtoHash = exprProtoHash;
    this.exprProtoSnapshotJson = exprProtoSnapshotJson;
    this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
    this.provenanceConfigHash = provenanceConfigHash;
    this.windowSpec = windowSpec;
    this.sliceStrategyCode = sliceStrategyCode;
    this.sliceParamsJson = sliceParamsJson;
    this.status = status == null ? PlanStatus.DRAFT : status;
  }

  /// 创建全新的计划蓝图聚合根，初始状态为 {@link PlanStatus#DRAFT DRAFT}。
/// 
/// @param scheduleInstanceId 调度实例标识
/// @param planKey 幂等键
/// @param provenanceCode 数据来源代码
/// @param operationCode 操作代码
/// @param exprProtoHash 表达式原型哈希
/// @param exprProtoSnapshotJson 表达式原型快照 JSON
/// @param provenanceConfigSnapshotJson 数据来源配置快照 JSON
/// @param provenanceConfigHash 数据来源配置哈希
/// @param windowSpec 窗口边界规范
/// @param sliceStrategyCode 切片策略代码
/// @param sliceParamsJson 切片策略参数 JSON
/// @return 新创建的计划聚合根
  public static PlanAggregate create(
      Long scheduleInstanceId,
      String planKey,
      ProvenanceCode provenanceCode,
      String operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson) {
    OperationCode op = operationCode == null ? null : OperationCode.fromCode(operationCode);
    return new PlanAggregate(
        null,
        scheduleInstanceId,
        planKey,
        provenanceCode,
        op,
        exprProtoHash,
        exprProtoSnapshotJson,
        provenanceConfigSnapshotJson,
        provenanceConfigHash,
        windowSpec,
        sliceStrategyCode,
        sliceParamsJson,
        PlanStatus.DRAFT);
  }

  /// 从持久化状态重建已存在的计划聚合根（由仓储层使用）。
/// 
/// @param id 主键标识
/// @param scheduleInstanceId 调度实例标识
/// @param planKey 计划幂等键
/// @param provenanceCode 数据来源代码
/// @param operationCode 操作代码字符串
/// @param exprProtoHash 表达式哈希
/// @param exprProtoSnapshotJson 表达式快照 JSON
/// @param provenanceConfigSnapshotJson 配置快照 JSON
/// @param provenanceConfigHash 配置快照哈希
/// @param windowSpec 窗口边界规范
/// @param sliceStrategyCode 切片策略代码
/// @param sliceParamsJson 切片策略参数 JSON
/// @param status 当前计划状态
/// @param version 乐观锁版本
/// @return 从持久化重建的计划聚合根
  public static PlanAggregate restore(
      Long id,
      Long scheduleInstanceId,
      String planKey,
      ProvenanceCode provenanceCode,
      String operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson,
      PlanStatus status,
      long version) {
    OperationCode op = operationCode == null ? null : OperationCode.fromCode(operationCode);
    PlanAggregate aggregate =
        new PlanAggregate(
            id,
            scheduleInstanceId,
            planKey,
            provenanceCode,
            op,
            exprProtoHash,
            exprProtoSnapshotJson,
            provenanceConfigSnapshotJson,
            provenanceConfigHash,
            windowSpec,
            sliceStrategyCode,
            sliceParamsJson,
            status);
    aggregate.version = version;
    return aggregate;
  }

  /// 将计划从 DRAFT 状态转换为 SLICING 状态。
/// 
/// @throws IllegalStateException 如果计划不处于 DRAFT 状态
  public void startSlicing() {
    if (this.status != PlanStatus.DRAFT) {
      throw new IllegalStateException("计划状态无效，无法开始切片生成");
    }
    this.status = PlanStatus.SLICING;
  }

  /// 在所有切片生成完成后，将计划标记为就绪状态。
  public void markReady() {
    this.status = PlanStatus.READY;
  }

  /// 更新计划状态为指定值。
/// 
/// 此方法由事件处理器使用，根据聚合的切片状态更新计划状态。
/// 
/// @param newStatus 要设置的新状态
/// @throws IllegalArgumentException 如果 newStatus 为 null
  public void updateStatus(PlanStatus newStatus) {
    Assert.notNull(newStatus, "newStatus 不能为 null");
    this.status = newStatus;
  }

  /// 便捷访问器，当使用 TIME 策略时返回窗口起始时间。
/// 对于不包含时间窗口的策略返回 `null`。
/// 
/// @return 窗口起始时间或 `null`
  public Instant getWindowFrom() {
    if (windowSpec instanceof WindowSpec.Time timeSpec) {
      return timeSpec.from();
    }
    return null;
  }

  /// 便捷访问器，当使用 TIME 策略时返回窗口结束时间。
/// 对于不暴露时间窗口的策略返回 `null`。
/// 
/// @return 窗口结束时间或 `null`
  public Instant getWindowTo() {
    if (windowSpec instanceof WindowSpec.Time timeSpec) {
      return timeSpec.to();
    }
    return null;
  }

  /// 获取数据来源代码字符串值。
/// 
/// @return 数据来源代码字符串
  public String getProvenanceCodeValue() {
    return provenanceCode.getCode();
  }

  /// 获取操作代码字符串（如果存在）。
/// 
/// @return 操作代码或 `null`
  public String getOperationCodeValue() {
    return operationCode == null ? null : operationCode.getCode();
  }
}
```

---

### 2. 值对象：WindowSpec.java（Sealed Interface）

```java
package com.patra.ingest.domain.model.vo.plan;

import cn.hutool.core.lang.Assert;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import java.time.Instant;
import java.util.Map;

/// 窗口规范值对象密封接口,表示数据采集窗口的边界规范。
/// 
/// 密封继承层次确保编译时完备性检查。支持多种数据采集分区策略:
/// 
/// - TIME - 基于时间的窗口(from/to 时间戳)
///   - ID_RANGE - 基于ID范围的窗口(from/to ID)
///   - CURSOR_LANDMARK - 基于游标/分页的窗口
///   - VOLUME_BUDGET - 基于容量预算的窗口
///   - SINGLE - 单一窗口(无分区)
/// 
/// 不可变性:所有实现都是不可变的Record
/// 
/// @author linqibin
/// @since 0.1.0
public sealed interface WindowSpec
    permits WindowSpec.Time,
        WindowSpec.IdRange,
        WindowSpec.CursorLandmark,
        WindowSpec.VolumeBudget,
        WindowSpec.Single {

  /// 获取窗口规范的策略类型。
/// 
/// @return 切片策略枚举
  SliceStrategy strategy();

  /// 转换为可JSON序列化的Map,用于持久化层存储。
/// 
/// @return 包含策略代码和策略特定字段的JSON可序列化Map
  Map<String, Object> toMap();

  // ============ 策略实现 ============

  /// 基于时间的窗口规范值对象。
/// 
/// 业务约束:
/// 
/// - from和to必须非空
///   - from必须早于或等于to
/// 
/// @param from 起始时间戳(闭区间)
/// @param to 结束时间戳(开区间)
  record Time(Instant from, Instant to) implements WindowSpec {
    public Time {
      Assert.notNull(from, "时间窗口 from 不能为空");
      Assert.notNull(to, "时间窗口 to 不能为空");
      Assert.isTrue(!from.isAfter(to), "from 必须早于或等于 to");
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.TIME;
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> boundaryMap = Map.of("from", "CLOSED", "to", "OPEN");
      Map<String, Object> windowMap =
          Map.of(
              "from", from.toString(),
              "to", to.toString(),
              "boundary", boundaryMap,
              "timezone", "UTC");
      return Map.of("strategy", SliceStrategy.TIME.getCode(), "window", windowMap);
    }
  }

  /// 基于ID范围的窗口规范值对象。
/// 
/// @param from 起始ID(闭区间)
/// @param to 结束ID(闭区间)
  record IdRange(Long from, Long to) implements WindowSpec {
    public IdRange {
      Assert.notNull(from, "ID 范围 from 不能为空");
      Assert.notNull(to, "ID 范围 to 不能为空");
      Assert.isTrue(from <= to, "from 必须小于或等于 to");
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.ID_RANGE;
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> windowMap = Map.of("from", from, "to", to);
      return Map.of("strategy", SliceStrategy.ID_RANGE.getCode(), "window", windowMap);
    }
  }

  /// 单一窗口规范值对象(无分区)。
/// 
/// 使用场景:不需要窗口分区的简单采集场景
  record Single() implements WindowSpec {
    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.SINGLE;
    }

    @Override
    public Map<String, Object> toMap() {
      return Map.of("strategy", SliceStrategy.SINGLE.getCode());
    }
  }

  // ============ 工厂方法 ============

  /// 创建基于时间的窗口规范。
  static Time ofTime(Instant from, Instant to) {
    return new Time(from, to);
  }

  /// 创建基于ID范围的窗口规范。
  static IdRange ofIdRange(Long from, Long to) {
    return new IdRange(from, to);
  }

  /// 创建单一窗口规范(无分区)。
  static Single ofSingle() {
    return new Single();
  }
}
```

---

### 3. 枚举：OperationCode.java

```java
package com.patra.ingest.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 采集操作类型 (字典: ing_operation)。
/// 
/// **持久化映射**
/// 
/// - ing_plan.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
///   - ing_task.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
/// 
/// @author linqibin
/// @since 0.1.0
@Getter
public enum OperationCode {
  /// 全量采集;初次运行或重建窗口的全量数据采集。
  HARVEST("HARVEST", "Full ingestion"),
  /// 历史回填;填补数据缺口或修正历史数据。
  BACKFILL("BACKFILL", "Backfill ingestion"),
  /// 增量更新;基于游标推进的增量数据更新。
  UPDATE("UPDATE", "Incremental update"),
  /// 指标采集;面向指标统计的操作(读取密集型)。
  METRICS("METRICS", "Metrics collection");

  private final String code;
  private final String description;

  OperationCode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 将提供的代码解析为枚举值。
/// 
/// @param value 字符串代码
/// @return 匹配的枚举值
/// @throws IllegalArgumentException 当值为 null 或未知时
  public static OperationCode fromCode(String value) {
    Assert.notNull(value, "操作代码不能为 null");
    String normalized = value.trim().toUpperCase();
    for (OperationCode oc : values()) {
      if (oc.code.equals(normalized)) {
        return oc;
      }
    }
    throw new IllegalArgumentException("未知的操作代码: " + value);
  }
}
```

---

### 4. 枚举：PlanStatus.java

```java
package com.patra.ingest.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 计划状态 (字典: ing_plan_status)。
/// 
/// 字段映射: `ing_plan.status_code → DRAFT/SLICING/READY/ARCHIVED`
/// 
/// 状态机语义:
/// 
/// - DRAFT → 新创建,尚未开始切片
///   - SLICING → 正在生成切片/任务
///   - READY → 切片和任务创建成功
///   - ARCHIVED → 生命周期已关闭,所有任务已完成
/// 
@Getter
public enum PlanStatus {
  /// 草稿;尚未开始切片。
  DRAFT("DRAFT", "Draft"),
  /// 切片进行中。
  SLICING("SLICING", "Slicing"),
  /// 切片已生成,任务已就绪可调度。
  READY("READY", "Ready"),
  /// 已归档;生命周期已关闭,所有任务已完成。
  ARCHIVED("ARCHIVED", "Archived");

  private final String code;
  private final String description;

  PlanStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static PlanStatus fromCode(String value) {
    Assert.notNull(value, "计划状态代码不能为 null");
    String n = value.trim().toUpperCase();
    for (PlanStatus e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("未知的计划状态代码: " + value);
  }
}
```

---

### 5. 枚举：ProvenanceCode.java（共享枚举）

```java
package com.patra.common.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 上游数据源(溯源/Provenance)枚举。
/// 
/// 为常见的文献和元数据源提供规范标识符。
@Getter
public enum ProvenanceCode {
  PUBMED("PUBMED", "PubMed"),
  PMC("PMC", "PubMed Central"),
  EPMC("EPMC", "Europe PMC"),
  OPENALEX("OPENALEX", "OpenAlex"),
  CROSSREF("CROSSREF", "Crossref"),
  UNPAYWALL("UNPAYWALL", "Unpaywall"),
  DOAJ("DOAJ", "DOAJ"),
  SEMANTIC_SCHOLAR("SEMANTICSCHOLAR", "Semantic Scholar"),
  CORE("CORE", "CORE"),
  DATACITE("DATACITE", "DataCite");

  /// 用于持久化和交换的大写代码。
  private final String code;

  /// 数据源的人类可读描述。
  private final String description;

  ProvenanceCode(String code, String display) {
    this.code = code;
    this.description = display;
  }

  /// 将字符串解析为 ProvenanceCode。
/// 
/// @param s 数据源标识符
/// @return 匹配的数据源代码
/// @throws IllegalArgumentException 如果标识符为 null 或未知
  public static ProvenanceCode parse(String s) {
    Assert.notNull(s, "数据源标识符不能为 null");
    String norm = s.trim().toUpperCase().replace('-', '_');
    for (ProvenanceCode code : values()) {
      if (code.code.equals(norm)) {
        return code;
      }
    }
    throw new IllegalArgumentException("未知的数据源: " + s);
  }
}
```

---

## ✅ 设计验证

### 1. 依赖方向检查 ✅
- ✅ Domain 层无基础设施框架依赖（禁止 Spring、MyBatis 等）
- ✅ 允许使用工具库：Hutool-Core、Jackson、Lombok
- ✅ 使用 Sealed Interface 和 Record 实现不可变值对象
- ✅ 聚合根封装业务规则和状态转换逻辑

### 2. 聚合边界检查 ✅
- ✅ PlanAggregate 是独立聚合根
- ✅ WindowSpec 是不可变值对象（Sealed Interface + Record）
- ✅ 枚举类型提供类型安全的代码值

### 3. 值对象不变性 ✅
- ✅ WindowSpec 的所有实现都是不可变的 Record
- ✅ 使用 Java 17+ Sealed Interface 确保编译时完备性
- ✅ 构造函数中验证业务约束

### 4. 状态机正确性 ✅
- ✅ 状态转换逻辑封装在聚合根内部
- ✅ 提供明确的状态转换方法（startSlicing、markReady）
- ✅ 使用 IllegalStateException 防止非法状态转换

---

## 💡 Domain 层依赖原则

### 允许的依赖（通过 patra-common-core）

**✅ Hutool-Core**：
```java
// ✅ 参数校验（替代 Objects.requireNonNull）
Assert.notNull(scheduleInstanceId, "scheduleInstanceId must not be null");
Assert.notBlank(planKey, "planKey must not be blank");
Assert.isTrue(limit > 0, "limit must be positive");

// ✅ 字符串工具
StrUtil.isBlank(str)
StrUtil.format("Plan key: {}", planKey)

// ✅ 集合工具
CollUtil.isEmpty(list)
CollUtil.newArrayList(1, 2, 3)

// ✅ 日期工具
DateUtil.parse("2024-01-01")
DateUtil.format(instant, "yyyy-MM-dd")

// ✅ 加密/哈希工具
SecureUtil.md5(content)
DigestUtil.sha256(data)
```

**✅ Jackson**：
```java
// ✅ JSON 序列化注解
@JsonCreator
@JsonValue
@JsonProperty
@JsonIgnore

// ✅ 在值对象中使用
public record WindowSpec(...) {
    @JsonCreator
    public static WindowSpec fromMap(Map<String, Object> map) {
        // 反序列化逻辑
    }
}
```

**✅ Lombok**：
```java
// ✅ 减少样板代码
@Getter
@ToString
@EqualsAndHashCode

// ❌ 避免使用会破坏不可变性的注解
// @Setter（聚合根字段应该是 private 或 final）
// @Data（包含 @Setter）
```

### 禁止的依赖

**❌ 基础设施框架**：
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

1. **工具 vs 框架**：工具提供纯函数，框架需要容器托管
2. **序列化 vs 持久化**：Jackson 用于序列化，不涉及数据库
3. **业务逻辑优先**：当工具方法不够用时，自己实现领域逻辑

---

## 📝 设计亮点

### 1. Sealed Interface + Record 模式
使用 Java 17 的 Sealed Interface 和 Record 实现 WindowSpec：
- **编译时完备性**：switch 表达式会检查所有可能的情况
- **不可变性**：Record 自动提供不可变语义
- **类型安全**：密封继承层次确保只有预定义的实现

### 2. 业务幂等键设计
`planKey = hash(provenance + operation + window + strategy)` 确保：
- 同一业务场景的计划可以去重
- 支持幂等性重试
- 便于调试和追踪

### 3. 状态机封装
聚合根内部封装状态转换逻辑：
- 防止外部直接修改状态
- 提供领域语义清晰的方法（startSlicing vs setStatus）
- 便于维护和测试

### 4. 快照模式
保存表达式和配置的快照：
- 支持审计和回溯
- 隔离运行时变更
- 便于问题排查

### 5. 工具库优先原则
优先使用 Hutool 替代 JDK API：
- **参数校验**：`Assert.notNull()` 替代 `Objects.requireNonNull()`
- **字符串判空**：`StrUtil.isBlank()` 替代 `str == null || str.isEmpty()`
- **集合操作**：`CollUtil.isEmpty()` 替代 `list == null || list.isEmpty()`
- **统一风格**：保持项目代码风格一致性

---

## 📝 后续步骤

- ✅ 阶段 4 完成：领域模型已生成
- ⬜ 如需记录设计决策 → **[阶段 5：设计决策记录](5-decisions.md)**
- ⬜ 如需调整领域模型，请提供反馈

---

**示例完成！** 🎉

本示例展示了 Patra 采集计划管理的完整领域模型设计，包含聚合根、值对象和枚举。
