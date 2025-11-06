# 依赖规则与 ArchUnit 验证

> **2 分钟快速启动** → 运行 ArchUnit 测试,立即验证架构规则

---

## 🚀 快速启动

### 运行 ArchUnit 测试 (3 步)

```bash
# 步骤 1: 运行所有测试
./mvnw test

# 步骤 2: 仅运行 ArchUnit 测试
./mvnw test -Dtest=ArchitectureTest

# 步骤 3: 如果失败,查看详细输出
./mvnw clean compile test -Dtest=ArchitectureTest
```

✅ **完成!** ArchUnit 自动验证所有架构规则。

---

## 📊 依赖规则速查表

| 层 | 可以依赖 | 禁止依赖 |
|-----|----------|----------|
| **Adapter** | ✅ App, Api, Domain, Spring | ❌ Infra (基础设施层) |
| **App** | ✅ Domain, patra-common, Spring | ❌ Adapter, Infra |
| **Domain** | ✅ patra-common, Lombok, Hutool | ❌ Spring, MyBatis, 任何框架 |
| **Infra** | ✅ Domain, MyBatis, Spring | ❌ Adapter, App |

---

## 🎯 核心依赖规则

### 规则 1: 领域层独立性 ⭐

**规则**: 领域层不得依赖任何基础设施或框架代码。

**✅ 允许**: `java.*`, `patra-common`, `lombok.*`, `cn.hutool.*`, `com.fasterxml.jackson.*`

**❌ 禁止**: `org.springframework.*`, `com.baomidou.mybatisplus.*`, `jakarta.persistence.*`

<details>
<summary><b>查看 ArchUnit 测试代码</b></summary>

```java
@ArchTest
static final ArchRule domainLayerIsIndependent =
    classes()
        .that().resideInAPackage("..domain..")
        .should().onlyDependOnClassesThat(
            resideInAnyPackage(
                "java..",
                "lombok..",
                "cn.hutool..",
                "com.fasterxml.jackson..",
                "com.patra.common..",
                "..domain.."
            )
        )
        .because("领域层必须框架无关");
```
</details>

<details>
<summary><b>查看违规示例与修复</b></summary>

**❌ 违规示例**:
```java
// ❌ 错误: 在领域中使用 Spring 注解
package com.patra.registry.domain.model.vo.provenance;

import org.springframework.stereotype.Component;  // ← 违规!

@Component  // ← 违规!
public record Provenance(...) {
    // ...
}
```

**修复**:
```java
// ✅ 正确: 纯 Java record
package com.patra.registry.domain.model.vo.provenance;

public record Provenance(
    Long id,
    String code,
    String name,
    String baseUrlDefault,
    String timezoneDefault,
    String docsUrl,
    boolean active,
    String lifecycleStatusCode
) {
    // 纯业务逻辑,无框架依赖
}
```
</details>

---

### 规则 2: 依赖方向 (六边形架构) ⭐

**规则**: 依赖向内流动: Adapter → App → Domain ← Infra

```
adapter  →  app + api
app      →  domain
infra    →  domain
domain   →  仅 patra-common
```

<details>
<summary><b>查看 ArchUnit 测试代码</b></summary>

```java
@ArchTest
static final ArchRule adapterDependsOnApp =
    classes()
        .that().resideInAPackage("..adapter..")
        .should().onlyAccessClassesThat(
            resideInAnyPackage(
                "..app..",
                "..api..",
                "..domain..",
                "org.springframework..",
                "java.."
            )
        )
        .because("适配器层应仅依赖应用层和领域层");

@ArchTest
static final ArchRule appDependsOnDomain =
    classes()
        .that().resideInAPackage("..app..")
        .should().onlyAccessClassesThat(
            resideInAnyPackage(
                "..domain..",
                "com.patra.common..",
                "org.springframework..",
                "java.."
            )
        )
        .because("应用层应仅依赖领域层");

@ArchTest
static final ArchRule infraDependsOnDomain =
    classes()
        .that().resideInAPackage("..infra..")
        .should().onlyAccessClassesThat(
            resideInAnyPackage(
                "..domain..",
                "com.patra.common..",
                "com.baomidou.mybatisplus..",
                "org.springframework..",
                "java.."
            )
        )
        .because("基础设施层应仅依赖领域层");
```
</details>

---

### 规则 3: 端口接口方向 ⭐

**规则**: 领域定义端口(接口),基础设施实现它们。

### ArchUnit 测试

```java
@ArchTest
static final ArchRule portsDefinedInDomain =
    classes()
        .that().haveSimpleNameEndingWith("Port")
        .or().haveSimpleNameEndingWith("Repository")
        .and().areInterfaces()
        .should().resideInAPackage("..domain.port..")
        .because("端口接口必须在领域层定义");

@ArchTest
static final ArchRule portsImplementedInInfra =
    classes()
        .that().implement(DescribedPredicate.describe(
            "端口接口",
            clazz -> clazz.getSimpleName().endsWith("Port")
                  || clazz.getSimpleName().endsWith("Repository")
        ))
        .and().areNotInterfaces()
        .should().resideInAPackage("..infra..")
        .because("端口实现必须在基础设施层");
```

**示例**:
```java
// ✅ 领域中的端口
package com.patra.ingest.domain.port;

public interface PatraRegistryPort {
    String fetchConfigSnapshot(String provenanceCode, String operationCode);
    String fetchExprSnapshot(String provenanceCode, String operationCode, Instant at);
}

// ✅ 基础设施中的实现
package com.patra.ingest.infra.adapter.registry;

@Component
public class PatraRegistryAdapter implements PatraRegistryPort {
    // 使用 Feign 客户端的实现
}
```

---

### 规则 4: 分层架构验证

**规则**: 强制执行严格的层边界。

### ArchUnit 测试

```java
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@ArchTest
static final ArchRule layersAreRespected = layeredArchitecture()
    .consideringAllDependencies()

    .layer("Adapter").definedBy("..adapter..")
    .layer("App").definedBy("..app..")
    .layer("Domain").definedBy("..domain..")
    .layer("Infra").definedBy("..infra..")

    .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
    .whereLayer("App").mayOnlyBeAccessedByLayers("Adapter")
    .whereLayer("Domain").mayOnlyBeAccessedByLayers("App", "Infra")
    .whereLayer("Infra").mayNotBeAccessedByAnyLayer()

    .because("层必须遵守六边形架构边界");
```

---

## 命名约定规则

### 规则 5: 编排者命名

**规则**: 应用服务必须以 `Orchestrator` 结尾。

### ArchUnit 测试

```java
@ArchTest
static final ArchRule orchestratorsAreNamedCorrectly =
    classes()
        .that().resideInAPackage("..app..")
        .and().areAnnotatedWith(Service.class)
        .should().haveSimpleNameEndingWith("Orchestrator")
        .because("应用服务应命名为 *Orchestrator");
```

**实际示例**:
- `PlanIngestionOrchestrator` (patra-ingest-app)
- `ProvenanceConfigOrchestrator` (patra-registry-app)
- `ExprQueryOrchestrator` (patra-registry-app)
- `RecordUploadOrchestrator` (patra-storage-app)

---

### 规则 6: 仓储命名

**规则**: 仓储实现必须以 `RepositoryImpl` 或 `RepositoryMpImpl` 结尾。

### ArchUnit 测试

```java
@ArchTest
static final ArchRule repositoriesAreNamedCorrectly =
    classes()
        .that().implement(DescribedPredicate.describe(
            "仓储接口",
            clazz -> clazz.getSimpleName().endsWith("Repository")
        ))
        .and().resideInAPackage("..infra..")
        .should().haveSimpleNameMatching(".*Repository(Impl|MpImpl)")
        .because("仓储实现应以 RepositoryImpl 或 RepositoryMpImpl 结尾");
```

---

### 规则 7: DO (数据对象) 命名

**规则**: MyBatis-Plus 实体必须以 `DO` 结尾。

### ArchUnit 测试

```java
@ArchTest
static final ArchRule dataObjectsAreNamedCorrectly =
    classes()
        .that().areAnnotatedWith(TableName.class)  // MyBatis-Plus 注解
        .should().haveSimpleNameEndingWith("DO")
        .because("MyBatis-Plus 实体应以 DO 结尾");
```

**实际示例**:
- `PlanDO` → `@TableName("ing_plan")`
- `PlanSliceDO` → `@TableName("ing_plan_slice")`
- `TaskDO` → `@TableName("ing_task")`
- `OutboxMessageDO` → `@TableName("ing_outbox_message")`
- `RegProvenanceDO` → `@TableName("reg_provenance")`

---

## 反模式检测

### 规则 8: 无循环依赖

**规则**: 防止包之间的循环依赖。

### ArchUnit 测试

```java
@ArchTest
static final ArchRule noCyclesInPackages =
    slices()
        .matching("com.patra.(*)..")
        .should().beFreeOfCycles()
        .because("循环依赖使代码难以理解和维护");
```

---

### 规则 9: 禁止字段注入

**规则**: 使用构造器注入,而非字段注入。

### ArchUnit 测试

```java
@ArchTest
static final ArchRule noFieldInjection =
    noFields()
        .that().areDeclaredInClassesThat().resideInAPackage("com.patra..")
        .should().beAnnotatedWith(Autowired.class)
        .because("使用构造器注入而非字段注入");
```

**示例**:
```java
// ❌ 错误: 字段注入
@Service
public class PlanIngestionOrchestrator {
    @Autowired  // ← 违规!
    private PatraRegistryPort registryPort;
}

// ✅ 正确: 构造器注入
@Service
@RequiredArgsConstructor  // Lombok 生成构造器
public class PlanIngestionOrchestrator {
    private final PatraRegistryPort registryPort;  // ← 通过构造器注入
}
```

---

### 规则 10: 仓储隔离

**规则**: 仓储不应在适配器层直接使用。

### ArchUnit 测试

```java
@ArchTest
static final ArchRule adapterDoesNotUseRepositories =
    noClasses()
        .that().resideInAPackage("..adapter..")
        .should().dependOnClassesThat().resideInAPackage("..infra..")
        .because("适配器层应仅使用应用层,而非基础设施层");
```

---

## 完整 ArchUnit 测试类

**位置**: `patra-{service}-boot/src/test/java/architecture/ArchitectureTest.java`

```java
package com.patra.registry.architecture;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.patra.registry")
class ArchitectureTest {

    // 领域独立性
    @ArchTest
    static final ArchRule domainLayerIsIndependent =
        classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat(
                resideInAnyPackage(
                    "java..",
                    "lombok..",
                    "cn.hutool..",
                    "com.fasterxml.jackson..",
                    "com.patra.common..",
                    "..domain.."
                )
            );

    // 分层架构
    @ArchTest
    static final ArchRule layersAreRespected = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Adapter").definedBy("..adapter..")
        .layer("App").definedBy("..app..")
        .layer("Domain").definedBy("..domain..")
        .layer("Infra").definedBy("..infra..")
        .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
        .whereLayer("App").mayOnlyBeAccessedByLayers("Adapter")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("App", "Infra")
        .whereLayer("Infra").mayNotBeAccessedByAnyLayer();

    // 命名约定
    @ArchTest
    static final ArchRule orchestratorsAreNamedCorrectly =
        classes()
            .that().resideInAPackage("..app..")
            .and().areAnnotatedWith(Service.class)
            .should().haveSimpleNameEndingWith("Orchestrator");

    @ArchTest
    static final ArchRule repositoriesAreNamedCorrectly =
        classes()
            .that().areAnnotatedWith(Repository.class)
            .and().resideInAPackage("..infra..")
            .should().haveSimpleNameMatching(".*Repository(Impl|MpImpl)");

    @ArchTest
    static final ArchRule dataObjectsAreNamedCorrectly =
        classes()
            .that().areAnnotatedWith(TableName.class)
            .should().haveSimpleNameEndingWith("DO");

    // 无循环依赖
    @ArchTest
    static final ArchRule noCyclesInPackages =
        slices()
            .matching("com.patra.(*)..")
            .should().beFreeOfCycles();

    // 禁止字段注入
    @ArchTest
    static final ArchRule noFieldInjection =
        noFields()
            .that().areDeclaredInClassesThat().resideInAPackage("com.patra..")
            .should().beAnnotatedWith(Autowired.class);

    // 端口和适配器
    @ArchTest
    static final ArchRule portsDefinedInDomain =
        classes()
            .that().haveSimpleNameEndingWith("Port")
            .or().haveSimpleNameEndingWith("Repository")
            .and().areInterfaces()
            .should().resideInAPackage("..domain.port..");

    @ArchTest
    static final ArchRule portsImplementedInInfra =
        classes()
            .that().implement(DescribedPredicate.describe(
                "端口接口",
                clazz -> clazz.getSimpleName().endsWith("Port")
                      || clazz.getSimpleName().endsWith("Repository")
            ))
            .and().areNotInterfaces()
            .should().resideInAPackage("..infra..");
}
```

---

## 本地运行 ArchUnit 测试

### Maven 命令

```bash
# 运行所有测试,包括 ArchUnit
./mvnw test

# 仅运行 ArchUnit 测试
./mvnw test -Dtest=ArchitectureTest

# 编译并测试(如果测试失败并显示"找不到类")
./mvnw clean compile test
```

### Maven 配置(可选)

如果想在 Maven Surefire 中明确包含 ArchUnit 测试:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
            <include>**/ArchitectureTest.java</include>
        </includes>
    </configuration>
</plugin>
```

---

## 最佳实践

### 1. 立即修复违规

**不要忽略 ArchUnit 失败**。它们表明架构漂移,随着时间推移会变得更难修复。

### 2. 提交前运行测试

养成在提交代码变更前运行 `./mvnw test` 的习惯,以便及早发现违规。

### 3. 随着架构演进更新规则

如果架构决策发生变化,请更新 ArchUnit 规则以匹配。规则应反映当前商定的架构。

### 4. 记录例外情况

如果需要对规则进行例外处理,请记录原因:

```java
@ArchTest
static final ArchRule domainLayerIsIndependent =
    classes()
        .that().resideInAPackage("..domain..")
        .and().haveSimpleNameNotEndingWith("JsonSerializer")  // ← 记录例外
        .should().onlyDependOnClassesThat(...)
        .because("领域层必须框架无关(事件的 JSON 序列化器除外)");
```

### 5. 逐步处理遗留违规

如果现有违规太多,冻结当前状态并防止新违规:

```java
@ArchTest
static final ArchRule domainLayerIsIndependent =
    classes()
        .that().resideInAPackage("..domain..")
        .should().onlyDependOnClassesThat(...)
        .allowEmptyShould(true);  // ← 暂时允许当前违规
```

然后在单独的重构工作中逐步修复违规。

---

## 总结

**ArchUnit 的好处**:
- ✅ 在测试时自动验证架构
- ✅ 在提交前防止架构漂移
- ✅ 将架构决策记录为可执行代码
- ✅ 对架构违规提供快速反馈

**涵盖的关键规则**:
1. **领域层独立性** - 领域中无框架依赖
2. **依赖方向** - 六边形架构边界 (Adapter → App → Domain ← Infra)
3. **端口/适配器模式** - 端口在领域中,实现在基础设施中
4. **命名约定** - Orchestrators、Repositories、DOs 正确命名
5. **反模式** - 无循环依赖、无字段注入、无层违规

**运行测试**:
```bash
# 运行所有测试
./mvnw test

# 仅运行 ArchUnit 测试
./mvnw test -Dtest=ArchitectureTest
```

**开发工作流**:
1. 遵循架构原则编写代码
2. 提交前本地运行 `./mvnw test`
3. 立即修复任何 ArchUnit 违规
4. 当架构演进时更新规则

**另见**:
- [architecture-overview.md](architecture-overview.md) - 架构原则和模式
- [testing-guide.md](testing-guide.md) - 其他测试策略(单元测试、集成测试、E2E)
