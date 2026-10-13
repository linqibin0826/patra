# 配置迁移（一）实施计划（PAP-76）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** registry、object-storage、identity、gateway 改为从 Nacos 读配置，本机 IDE 和 mini 容器读到的配置只差 `PATRA_INFRA_HOST`、`PATRA_ADVERTISE_IP`、`PATRA_LOG_DIR` 三个本地变量。

**Architecture:** 构建约定插件 `linqibin.hexagonal-boot` 改为引入 `patra-spring-boot-starter-nacos`（PAP-74），4 个服务的启动类改用 `PatraApplication.run`，由启动器导入 Nacos 的 `patra-common.yaml` 和 `{服务名}.yaml`。jar 里只留 `application.yml`（随代码变的默认值），删掉两个 profile 文件；随环境变的值写进 `patra-infra/nacos/dev/`（PAP-75 的推送目录）。容器侧删掉 4 个服务专属环境文件。每个 boot 模块新增 `ApplicationConfigTest` 驱动迁移、防止回退。

**Tech Stack:** Java 25、Spring Boot 4.0.8、SCA 2025.1.0.0（nacos-client 3.1.1）、Nacos 3.2.4、Gradle 9.5 convention plugins、JUnit 5 + AssertJ、Docker Compose、GitHub Actions（`cd.yml`）、Playwright（门户 e2e）。

**Spec:** `docs/patra/specs/2026-10-13-config-migration-batch1-design.md`

## Global Constraints

- 服务名（也是 Nacos 服务文件的 dataId 前缀）：`patra-registry`、`patra-object-storage`、`patra-identity`、`patra-gateway`。
- 迁移顺序：registry → object-storage → identity → gateway。
- Nacos 文件内容逐字按 spec 第 5 节：数据库密码写 `${PATRA_DB_PASSWORD:123456}`；主机写 `${PATRA_INFRA_HOST:127.0.0.1}`；PG 端口 `15432`；Redis 端口 `16379`、用户名 `default`、密码 `${REDIS_PASSWORD}`；网关私钥写 `${PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY}`；每份文件保留签到行。
- jar 和 Nacos 分工（spec 决定 1）：端口、路由、连接池、超时、限流等留在 jar，不搬进 Nacos。
- 仓库是公开的：不提交真实密钥；命令输出不打印密码、令牌、私钥（看容器环境变量只取变量名）。
- **凡动 mini 的步骤（推送 Nacos、推送分支、触发 CD），执行前先征得用户同意。** 不自动推送分支；ship-gate 钩子拦截时请用户自己推送，不改钩子。
- 提交格式 `<type>(config-center): 中文动词开头… (PAP-76)`，subject 不以大写英文开头，结尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`；提交前 `./gradlew spotlessApply`，`git diff --cached --stat` 只含本任务的路径。
- 改了 `*.gradle.kts` 后：跑 `./gradlew dumpModuleGraph` 并提交 `patra-infra/cd/module-graph.json`；提醒用户在 IDEA 里点「加载 Gradle 更改」。
- 命令一律用绝对路径，不 `cd`。仓库根：`/Users/linqibin/Projects/Products/patra`（下文简称 `$R`，执行时写全路径）。
- 测试方法 snake_case，`@DisplayName` 写中文；Javadoc 用 `///`；不写全类名。

## Review Focus

1. **本机缺密钥文件**（新机器没拷 `redis.env`）：以前由 `application-dev.yml` 的显式导入报出缺的文件；迁移后由 starter 读目录，PAP-74 spec 承诺「用到它的服务启动失败，报出缺的变量名」。Spring 绑定配置时不严格解析占位符，可能拿字面量 `${REDIS_PASSWORD}` 当密码静默启动。测试：Task 9 Step 6。
2. **容器仍带 `SPRING_PROFILES_ACTIVE=container`**（`.env.common` 留给 PAP-77 清理）：迁完的服务没有 container profile 文件，期望照常启动、读 Nacos。测试：Task 9 Step 9（4 个容器健康）。
3. **没有 Redis 的服务在容器里解析不了 `${REDIS_PASSWORD}`**：registry、object-storage 的容器不加载 `redis.env`，公共配置里的 Redis 密码引用解析不了，期望不影响启动。本机有 `redis.env` 测不出来。测试：Task 9 Step 9（registry、object-storage 容器健康）+ Step 10 日志检查。
4. **registry 的 `classpath:registry-error-config.yaml` 导入与启动器的 Nacos 导入并存**：期望三份配置都被加载。测试：Task 9 Step 5 的 TRACE 日志。
5. **身份断言公钥从 3 份变 1 份**：identity、gateway 都从 `patra-common.yaml` 读，YAML 单引号里的 JSON 期望逐字不变。网关启动时用配置的公钥自签自验，公钥错了起不来；identity 用它验断言。测试：Task 9 Step 5（gateway 本机启动）+ Step 11（门户注册、登录、登出 e2e）。

---

### Task 1: 构建约定插件改为引入 Nacos starter

**Files:**
- Modify: `build-logic/src/main/kotlin/linqibin.hexagonal-boot.gradle.kts:71-73`
- Modify: `patra-infra/cd/module-graph.json`（由 `dumpModuleGraph` 生成）

**Interfaces:**
- Consumes：PAP-74 的 `:patra-starters:patra-spring-boot-starter-nacos`（`api` 依赖 nacos-config、nacos-discovery、spring-boot-starter）。
- Produces：6 个 boot 模块的编译 classpath 上有 `dev.linqibin.patra.starter.nacos.PatraApplication`，Task 3–6 的启动类用它。

这一步只换依赖，没有能先失败的测试：Task 3 起启动类 import `PatraApplication`，没有这一步编译不过；ingest、catalog 由它们现有的测试兜底（spec 第 4 节）。

- [ ] **Step 1: 换依赖**

把 `linqibin.hexagonal-boot.gradle.kts` 的

```kotlin
dependencies {
    // 服务发现
    implementation(libs.findLibrary("spring-cloud-starter-alibaba-nacos-discovery").get())
```

改为

```kotlin
dependencies {
    // Nacos 接入：服务发现、配置中心、启动器 PatraApplication（starter 里统一注入连接设置）
    implementation(project(":patra-starters:patra-spring-boot-starter-nacos"))
```

- [ ] **Step 2: 编译 6 个服务**

Run: `$R/gradlew -p $R compileJava compileTestJava -q > $SCRATCH/t1-compile.log 2>&1; echo "exit=$?"`（`$SCRATCH` 是会话 scratchpad 目录）

Expected: `exit=0`。

- [ ] **Step 3: 更新模块图**

Run: `$R/gradlew -p $R dumpModuleGraph -q && git -C $R diff --stat patra-infra/cd/module-graph.json`

Expected: `module-graph.json` 有改动；6 个 boot 模块的条目里 `impacts` 不变（每个仍只影响自己的单元），`patra-starters/patra-spring-boot-starter-nacos` 的 `impacts` 从 `["foundation"]` 变为包含 6 个服务单元和 `foundation`。

- [ ] **Step 4: ingest、catalog 不受影响**

Run: `$R/gradlew -p $R :patra-api:patra-ingest:patra-ingest-boot:check :patra-api:patra-catalog:patra-catalog-boot:check > $SCRATCH/t1-check.log 2>&1; echo "exit=$?"; tail -5 $SCRATCH/t1-check.log`

Expected: `exit=0`，`BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交**

```bash
$R/gradlew -p $R spotlessApply -q
git -C $R add build-logic/src/main/kotlin/linqibin.hexagonal-boot.gradle.kts patra-infra/cd/module-graph.json
git -C $R diff --cached --stat
git -C $R commit -m "build(config-center): 服务的构建约定插件改为引入 Nacos starter (PAP-76)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

提醒用户：IDEA 里点「加载 Gradle 更改」。

---

### Task 2: 填写 Nacos 配置

**Files:**
- Modify: `patra-infra/nacos/dev/patra-common.yaml`
- Modify: `patra-infra/nacos/dev/patra-registry.yaml`
- Modify: `patra-infra/nacos/dev/patra-object-storage.yaml`
- Modify: `patra-infra/nacos/dev/patra-identity.yaml`
- Modify: `patra-infra/nacos/dev/patra-gateway.yaml`
- Modify: `patra-infra/nacos/dev/README.md`
- Test: `patra-starters/patra-spring-boot-starter-nacos/src/test/java/dev/linqibin/patra/starter/nacos/NacosConfigFilesTest.java`（PAP-75 已有，`should_pass_for_repository_directory` 检查这个目录）

**Interfaces:**
- Consumes：PAP-75 的检查规则（签到行、敏感键只写引用、无重复键、LF）。
- Produces：Task 3–6 的服务启动时读到的属性：`spring.datasource.{url,username,password}`、`spring.data.redis.{host,port,username,password}`、`patra.security.identity-assertion.public-keys`、`patra.gateway.identity-assertion.private-key`。

纯配置文件，不走 TDD（项目规则）；由现有的 `NacosConfigFilesTest` 检查格式和密钥规则。

- [ ] **Step 1: 写 5 份文件**

`patra-common.yaml`（公钥值从 `patra-infra/docker/.env.common` 的 `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS` 原样复制，外面加单引号）：

```yaml
patra.config-source.common: patra-common.yaml

spring:
  datasource:
    username: postgres
    password: ${PATRA_DB_PASSWORD:123456}
  data:
    redis:
      host: ${PATRA_INFRA_HOST:127.0.0.1}
      port: 16379
      username: default
      password: ${REDIS_PASSWORD}

patra:
  security:
    identity-assertion:
      public-keys: '{"keys":[{"kty":"EC","crv":"P-256","kid":"8q4oW5O78zImMpqUwOfEXz3miOfVc2XPS9F_vMPsczk","x":"A-1wOkD67cIDybmSkE_9iQSgw3Rk4U189p6TsFZZFA4","y":"eYzvHkiL_bApKcLXgDq2fQmQ43zYOqqtzlt2ThbDMh8"}]}'
```

`patra-registry.yaml`：

```yaml
patra.config-source.service: patra-registry.yaml

spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_registry
```

`patra-object-storage.yaml`：

```yaml
patra.config-source.service: patra-object-storage.yaml

spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_storage
```

`patra-identity.yaml`：

```yaml
patra.config-source.service: patra-identity.yaml

spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_identity
```

`patra-gateway.yaml`：

```yaml
patra.config-source.service: patra-gateway.yaml

patra:
  gateway:
    identity-assertion:
      private-key: ${PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY}
```

核对：公钥与 `.env.common`、identity 和 gateway 的 `application-dev.yml` 三处逐字相同：

```bash
grep -ho '{"keys".*}' $R/patra-infra/docker/.env.common $R/patra-infra/nacos/dev/patra-common.yaml \
  $R/patra-api/patra-identity/patra-identity-boot/src/main/resources/application-dev.yml \
  $R/patra-api/patra-gateway-boot/src/main/resources/application-dev.yml | sort -u | wc -l
```

Expected: `1`。

- [ ] **Step 2: README 补一条**

在 `patra-infra/nacos/dev/README.md` 第一组列表「文件名即 dataId」那一条后面加：

```markdown
- **只放随环境变化的值**：基础设施坐标、账号、公钥、密钥引用。端口、路由、连接池、超时等和代码一起变的默认值，留在各服务 jar 里的 `application.yml`。`patra-common.yaml` 放各服务共用的数据库账号、Redis 坐标和身份断言公钥；服务文件放本服务的库地址等。
```

- [ ] **Step 3: 跑配置检查**

Run: `$R/gradlew -p $R :patra-starters:patra-spring-boot-starter-nacos:test --tests '*NacosConfigFilesTest' > $SCRATCH/t2.log 2>&1; echo "exit=$?"; tail -5 $SCRATCH/t2.log`

Expected: `exit=0`，`BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git -C $R add patra-infra/nacos/dev/
git -C $R diff --cached --stat
git -C $R commit -m "feat(config-center): 补全 registry、object-storage、identity、gateway 的 Nacos 配置 (PAP-76)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: registry 迁移

**Files:**
- Create: `patra-api/patra-registry/patra-registry-boot/src/test/java/dev/linqibin/patra/registry/ApplicationConfigTest.java`
- Delete: `patra-api/patra-registry/patra-registry-boot/src/main/resources/application-dev.yml`
- Delete: `patra-api/patra-registry/patra-registry-boot/src/main/resources/application-container.yml`
- Modify: `patra-api/patra-registry/patra-registry-boot/src/main/resources/application.yml`
- Modify: `patra-api/patra-registry/patra-registry-boot/src/main/java/dev/linqibin/patra/registry/PatraRegistryApplication.java`

**Interfaces:**
- Consumes：Task 1 的 `PatraApplication.run(String applicationName, Class<?> source, String... args)`；Task 2 的 `patra-registry.yaml`。
- Produces：`ApplicationConfigTest` 的写法，Task 4–6 照抄（只改 package）。

- [ ] **Step 1: 写第一条失败的测试（没有 profile 文件）**

```java
package dev.linqibin.patra.registry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// jar 里配置文件的约束（PAP-76）：随环境变的值在 Nacos，jar 里没有 profile 文件，
/// 服务名由启动器传入，Nacos 接入由 starter 注入。
@DisplayName("jar 内配置文件约束")
class ApplicationConfigTest {

  @Test
  @DisplayName("classpath 上没有 profile 文件")
  void should_not_ship_profile_files() {
    assertThat(getClass().getResource("/application-dev.yml")).isNull();
    assertThat(getClass().getResource("/application-container.yml")).isNull();
  }
}
```

- [ ] **Step 2: 看它失败**

Run: `$R/gradlew -p $R :patra-api:patra-registry:patra-registry-boot:test --tests '*ApplicationConfigTest' > $SCRATCH/t3.log 2>&1; echo "exit=$?"; grep -E "FAILED|Expecting" $SCRATCH/t3.log | head`

Expected: `exit=1`，`should_not_ship_profile_files() FAILED`，断言信息是期望 null、实际是 `application-dev.yml` 的 URL。

- [ ] **Step 3: 删掉两个 profile 文件**

```bash
git -C $R rm -q patra-api/patra-registry/patra-registry-boot/src/main/resources/application-dev.yml \
  patra-api/patra-registry/patra-registry-boot/src/main/resources/application-container.yml
```

- [ ] **Step 4: 看它通过**

Run: 同 Step 2 的命令。

Expected: `exit=0`。如果仍然失败、实际值指向 `build/resources/main/`，说明构建目录留着旧文件：先跑 `$R/gradlew -p $R :patra-api:patra-registry:patra-registry-boot:cleanProcessResources` 再重跑，并在 ledger 记一条 Ruling。

- [ ] **Step 5: 加第二条测试（`application.yml` 不写服务名、profile、Nacos）**

在 `ApplicationConfigTest` 里加 import 和下面的测试、辅助方法：

```java
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;
```

```java
  @Test
  @DisplayName("application.yml 不写服务名、激活的 profile 和 Nacos 接入")
  void should_leave_name_profile_and_nacos_to_launcher_and_starter() throws IOException {
    assertThat(propertyNames("application.yml"))
        .noneMatch(
            name ->
                name.startsWith("spring.application.name")
                    || name.startsWith("spring.profiles.active")
                    || name.startsWith("spring.cloud.nacos"));
  }

  /// 读出 classpath 上一份 YAML 的全部属性名，多文档合在一起。
  ///
  /// @param resource classpath 上的文件名
  /// @return 扁平化后的属性名
  /// @throws IOException 读取失败时
  private static List<String> propertyNames(String resource) throws IOException {
    return new YamlPropertySourceLoader()
        .load(resource, new ClassPathResource(resource)).stream()
            .map(source -> (EnumerablePropertySource<?>) source)
            .flatMap(source -> Arrays.stream(source.getPropertyNames()))
            .toList();
  }
```

- [ ] **Step 6: 看它失败**

Run: 同 Step 2 的命令。

Expected: `exit=1`，`should_leave_name_profile_and_nacos_to_launcher_and_starter() FAILED`，断言信息里列出 `spring.application.name`、`spring.profiles.active`、`spring.cloud.nacos.username` 等。

- [ ] **Step 7: 改 `application.yml` 和启动类**

`application.yml` 的 `spring:` 段：删掉 `application.name`、`profiles.active` 及其注释、整个「Nacos Service Discovery」小节（标题注释和 `cloud:` 块）；`config.import` 的 `classpath:registry-error-config.yaml` 保留。数据源的注释改为指向 Nacos。改完 `spring:` 段开头是：

```yaml
spring:
  # Configuration imports (order matters: later configs override earlier ones)
  # Nacos 的 patra-common.yaml、patra-registry.yaml 由启动器 PatraApplication 导入，这里不写
  config:
    import:
      # Local error-handling configuration
      - "classpath:registry-error-config.yaml"

  # ========================================
  # JSON Serialization Configuration
  # ========================================
```

数据源一段改为：

```yaml
  datasource:
    driver-class-name: org.postgresql.Driver
    # 地址、账号、密码随环境变，在 Nacos：patra-registry.yaml、patra-common.yaml（源文件在 patra-infra/nacos/dev/）
    hikari:
```

`management.metrics.tags.application: ${spring.application.name}` 保留：服务名由启动器放进 default properties，占位符照样能解析。

启动类改为：

```java
package dev.linqibin.patra.registry;

import dev.linqibin.patra.starter.nacos.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// （类 Javadoc 原样保留）
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class PatraRegistryApplication {

  /// 应用程序入口点：经启动器启动，服务名 `patra-registry`，配置从 Nacos 读。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    PatraApplication.run("patra-registry", PatraRegistryApplication.class, args);
  }
}
```

启动类没有单独的测试：去掉 `spring.application.name` 后服务名只能由启动器给，Task 9 Step 5 的本机启动验证它。

- [ ] **Step 8: 看它通过，跑模块检查**

Run: `$R/gradlew -p $R :patra-api:patra-registry:patra-registry-boot:check > $SCRATCH/t3-check.log 2>&1; echo "exit=$?"; tail -5 $SCRATCH/t3-check.log`

Expected: `exit=0`，`BUILD SUCCESSFUL`，`ApplicationConfigTest` 两条都通过。

- [ ] **Step 9: 变异检查**

临时在 `application.yml` 的 `spring:` 下加回一行 `profiles.active: dev`，跑 Step 2 的命令，Expected `exit=1`（第二条测试失败）；还原后再跑一次，Expected `exit=0`。结果记进 ledger。

- [ ] **Step 10: 提交**

```bash
$R/gradlew -p $R spotlessApply -q
git -C $R add patra-api/patra-registry/patra-registry-boot/
git -C $R diff --cached --stat
git -C $R commit -m "feat(config-center): registry 改为从 Nacos 读配置 (PAP-76)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: object-storage 迁移

**Files:**
- Create: `patra-api/patra-object-storage/patra-object-storage-boot/src/test/java/dev/linqibin/patra/objectstorage/ApplicationConfigTest.java`（模块原来没有 `src/test`；`testImplementation` 已由构建约定插件提供）
- Delete: `patra-api/patra-object-storage/patra-object-storage-boot/src/main/resources/application-dev.yml`
- Delete: `patra-api/patra-object-storage/patra-object-storage-boot/src/main/resources/application-container.yml`
- Modify: `patra-api/patra-object-storage/patra-object-storage-boot/src/main/resources/application.yml`
- Modify: `patra-api/patra-object-storage/patra-object-storage-boot/src/main/java/dev/linqibin/patra/objectstorage/PatraObjectStorageApplication.java`

**Interfaces:**
- Consumes：Task 1 的 `PatraApplication.run`；Task 2 的 `patra-object-storage.yaml`；Task 3 的 `ApplicationConfigTest` 写法。
- Produces：无。

`ApplicationConfigTest` 与 Task 3 只差 package。四个模块各放一份是 spec 决定 7 的做法（PAP-77 照抄到 ingest、catalog），不抽公共 fixture。

- [ ] **Step 1: 写第一条失败的测试**

```java
package dev.linqibin.patra.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// jar 里配置文件的约束（PAP-76）：随环境变的值在 Nacos，jar 里没有 profile 文件，
/// 服务名由启动器传入，Nacos 接入由 starter 注入。
@DisplayName("jar 内配置文件约束")
class ApplicationConfigTest {

  @Test
  @DisplayName("classpath 上没有 profile 文件")
  void should_not_ship_profile_files() {
    assertThat(getClass().getResource("/application-dev.yml")).isNull();
    assertThat(getClass().getResource("/application-container.yml")).isNull();
  }
}
```

- [ ] **Step 2: 看它失败**

Run: `$R/gradlew -p $R :patra-api:patra-object-storage:patra-object-storage-boot:test --tests '*ApplicationConfigTest' > $SCRATCH/t4.log 2>&1; echo "exit=$?"; grep -E "FAILED|Expecting" $SCRATCH/t4.log | head`

Expected: `exit=1`，`should_not_ship_profile_files() FAILED`。

- [ ] **Step 3: 删掉两个 profile 文件**

```bash
git -C $R rm -q patra-api/patra-object-storage/patra-object-storage-boot/src/main/resources/application-dev.yml \
  patra-api/patra-object-storage/patra-object-storage-boot/src/main/resources/application-container.yml
```

- [ ] **Step 4: 看它通过**

Run: 同 Step 2。Expected: `exit=0`。

- [ ] **Step 5: 加第二条测试**

与 Task 3 Step 5 相同的 import、测试方法和 `propertyNames` 辅助方法：

```java
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;
```

```java
  @Test
  @DisplayName("application.yml 不写服务名、激活的 profile 和 Nacos 接入")
  void should_leave_name_profile_and_nacos_to_launcher_and_starter() throws IOException {
    assertThat(propertyNames("application.yml"))
        .noneMatch(
            name ->
                name.startsWith("spring.application.name")
                    || name.startsWith("spring.profiles.active")
                    || name.startsWith("spring.cloud.nacos"));
  }

  /// 读出 classpath 上一份 YAML 的全部属性名，多文档合在一起。
  ///
  /// @param resource classpath 上的文件名
  /// @return 扁平化后的属性名
  /// @throws IOException 读取失败时
  private static List<String> propertyNames(String resource) throws IOException {
    return new YamlPropertySourceLoader()
        .load(resource, new ClassPathResource(resource)).stream()
            .map(source -> (EnumerablePropertySource<?>) source)
            .flatMap(source -> Arrays.stream(source.getPropertyNames()))
            .toList();
  }
```

- [ ] **Step 6: 看它失败**

Run: 同 Step 2。Expected: `exit=1`，`should_leave_name_profile_and_nacos_to_launcher_and_starter() FAILED`。

- [ ] **Step 7: 改 `application.yml` 和启动类**

`application.yml` 的 `spring:` 段删掉 `application`、`profiles`、`cloud` 三块，改完是：

```yaml
server:
  port: 6200

spring:
  # 数据源的地址、账号、密码随环境变，在 Nacos：patra-object-storage.yaml、patra-common.yaml
  # （源文件在 patra-infra/nacos/dev/），由启动器 PatraApplication 导入
  datasource:
    driver-class-name: org.postgresql.Driver
    hikari:
```

其余（hikari、flyway、logging、management）不动。

启动类改为（删掉设默认 profile 的分支）：

```java
package dev.linqibin.patra.objectstorage;

import dev.linqibin.patra.starter.nacos.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// 存储元数据服务的Spring Boot应用入口。
///
/// Patra对象存储元数据管理微服务的启动类,负责记录和管理上传到 外部对象存储提供商的文件元数据。
///
/// 配置从 Nacos 读（`patra-common.yaml`、`patra-object-storage.yaml`），由启动器导入。
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class PatraObjectStorageApplication {

  /// 应用程序入口点：经启动器启动，服务名 `patra-object-storage`。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    PatraApplication.run("patra-object-storage", PatraObjectStorageApplication.class, args);
  }
}
```

- [ ] **Step 8: 看它通过，跑模块检查**

Run: `$R/gradlew -p $R :patra-api:patra-object-storage:patra-object-storage-boot:check > $SCRATCH/t4-check.log 2>&1; echo "exit=$?"; tail -5 $SCRATCH/t4-check.log`

Expected: `exit=0`，`BUILD SUCCESSFUL`。

- [ ] **Step 9: 变异检查**

临时在 `application.yml` 的 `spring:` 下加回 `application.name: patra-object-storage`，跑 Step 2，Expected `exit=1`；还原后 Expected `exit=0`。记进 ledger。

- [ ] **Step 10: 提交**

```bash
$R/gradlew -p $R spotlessApply -q
git -C $R add patra-api/patra-object-storage/patra-object-storage-boot/
git -C $R diff --cached --stat
git -C $R commit -m "feat(config-center): object-storage 改为从 Nacos 读配置 (PAP-76)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: identity 迁移

**Files:**
- Create: `patra-api/patra-identity/patra-identity-boot/src/test/java/dev/linqibin/patra/identity/ApplicationConfigTest.java`
- Delete: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application-dev.yml`
- Delete: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application-container.yml`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application.yml`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/java/dev/linqibin/patra/identity/PatraIdentityApplication.java`
- Modify（仅当集成测试因缺服务名失败）：`patra-api/patra-identity/patra-identity-boot/src/integrationTest/resources/application-test.yml`

**Interfaces:**
- Consumes：Task 1 的 `PatraApplication.run`；Task 2 的 `patra-identity.yaml`、公共配置里的 Redis 与公钥；Task 3 的 `ApplicationConfigTest` 写法。
- Produces：无。

- [ ] **Step 1: 写第一条失败的测试**

```java
package dev.linqibin.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// jar 里配置文件的约束（PAP-76）：随环境变的值在 Nacos，jar 里没有 profile 文件，
/// 服务名由启动器传入，Nacos 接入由 starter 注入。
@DisplayName("jar 内配置文件约束")
class ApplicationConfigTest {

  @Test
  @DisplayName("classpath 上没有 profile 文件")
  void should_not_ship_profile_files() {
    assertThat(getClass().getResource("/application-dev.yml")).isNull();
    assertThat(getClass().getResource("/application-container.yml")).isNull();
  }
}
```

- [ ] **Step 2: 看它失败**

Run: `$R/gradlew -p $R :patra-api:patra-identity:patra-identity-boot:test --tests '*ApplicationConfigTest' > $SCRATCH/t5.log 2>&1; echo "exit=$?"; grep -E "FAILED|Expecting" $SCRATCH/t5.log | head`

Expected: `exit=1`，`should_not_ship_profile_files() FAILED`。

- [ ] **Step 3: 删掉两个 profile 文件**

```bash
git -C $R rm -q patra-api/patra-identity/patra-identity-boot/src/main/resources/application-dev.yml \
  patra-api/patra-identity/patra-identity-boot/src/main/resources/application-container.yml
```

- [ ] **Step 4: 看它通过**

Run: 同 Step 2。Expected: `exit=0`。

- [ ] **Step 5: 加第二条测试**

与 Task 3 Step 5 相同的 import、测试方法和 `propertyNames` 辅助方法：

```java
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;
```

```java
  @Test
  @DisplayName("application.yml 不写服务名、激活的 profile 和 Nacos 接入")
  void should_leave_name_profile_and_nacos_to_launcher_and_starter() throws IOException {
    assertThat(propertyNames("application.yml"))
        .noneMatch(
            name ->
                name.startsWith("spring.application.name")
                    || name.startsWith("spring.profiles.active")
                    || name.startsWith("spring.cloud.nacos"));
  }

  /// 读出 classpath 上一份 YAML 的全部属性名，多文档合在一起。
  ///
  /// @param resource classpath 上的文件名
  /// @return 扁平化后的属性名
  /// @throws IOException 读取失败时
  private static List<String> propertyNames(String resource) throws IOException {
    return new YamlPropertySourceLoader()
        .load(resource, new ClassPathResource(resource)).stream()
            .map(source -> (EnumerablePropertySource<?>) source)
            .flatMap(source -> Arrays.stream(source.getPropertyNames()))
            .toList();
  }
```

- [ ] **Step 6: 看它失败**

Run: 同 Step 2。Expected: `exit=1`，`should_leave_name_profile_and_nacos_to_launcher_and_starter() FAILED`。

- [ ] **Step 7: 改 `application.yml` 和启动类**

`application.yml` 的 `spring:` 段删掉 `application`、`profiles`、`cloud` 三块，在 `datasource:` 上面加一行注释，改完 `spring:` 段开头是：

```yaml
spring:
  # 数据源地址、账号、Redis 坐标、身份断言公钥随环境变，在 Nacos：patra-identity.yaml、patra-common.yaml
  # （源文件在 patra-infra/nacos/dev/），由启动器 PatraApplication 导入
  datasource:
    driver-class-name: org.postgresql.Driver
```

其余（jpa、`data.redis` 超时、`linqibin`、logging、management、`patra.identity`）不动。

启动类改为：

```java
package dev.linqibin.patra.identity;

import dev.linqibin.patra.starter.nacos.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// identity 服务的启动入口：前台用户的账号、凭据，以及以后的会话。
///
/// 配置从 Nacos 读（`patra-common.yaml`、`patra-identity.yaml`），由启动器导入。
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class PatraIdentityApplication {

  /// 经启动器启动应用，服务名 `patra-identity`。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    PatraApplication.run("patra-identity", PatraIdentityApplication.class, args);
  }
}
```

- [ ] **Step 8: 看它通过，跑模块检查和集成测试**

Run: `$R/gradlew -p $R :patra-api:patra-identity:patra-identity-boot:check :patra-api:patra-identity:patra-identity-boot:integrationTest > $SCRATCH/t5-check.log 2>&1; echo "exit=$?"; tail -5 $SCRATCH/t5-check.log`

Expected: `exit=0`，`BUILD SUCCESSFUL`。

如果集成测试因为缺 `spring.application.name` 失败（spec 第 4 节第 4 条），在 `src/integrationTest/resources/application-test.yml` 的 `spring:` 下补：

```yaml
  application:
    name: patra-identity
```

重跑，Expected `exit=0`，并在 ledger 记一条 Ruling（哪个测试、什么报错）。

- [ ] **Step 9: 变异检查**

临时在 `application.yml` 的 `spring:` 下加回 `cloud.nacos.discovery.fail-fast: false`，跑 Step 2，Expected `exit=1`；还原后 Expected `exit=0`。记进 ledger。

- [ ] **Step 10: 提交**

```bash
$R/gradlew -p $R spotlessApply -q
git -C $R add patra-api/patra-identity/patra-identity-boot/
git -C $R diff --cached --stat
git -C $R commit -m "feat(config-center): identity 改为从 Nacos 读配置 (PAP-76)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: gateway 迁移

**Files:**
- Create: `patra-api/patra-gateway-boot/src/test/java/dev/linqibin/patra/gateway/ApplicationConfigTest.java`
- Delete: `patra-api/patra-gateway-boot/src/main/resources/application-dev.yml`
- Delete: `patra-api/patra-gateway-boot/src/main/resources/application-container.yml`
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application.yml`
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/PatraGatewayApplication.java`
- Modify（仅当集成测试因缺服务名失败）：`patra-api/patra-gateway-boot/src/integrationTest/resources/application-test.yml`

**Interfaces:**
- Consumes：Task 1 的 `PatraApplication.run`；Task 2 的 `patra-gateway.yaml`、公共配置里的 Redis 与公钥；Task 3 的 `ApplicationConfigTest` 写法。
- Produces：无。

gateway 的 `application-dev.yml` 里的 DEBUG 日志随文件删掉，两种运行方式都用 jar 里的 INFO（spec 决定 4）。

- [ ] **Step 1: 写第一条失败的测试**

```java
package dev.linqibin.patra.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// jar 里配置文件的约束（PAP-76）：随环境变的值在 Nacos，jar 里没有 profile 文件，
/// 服务名由启动器传入，Nacos 接入由 starter 注入。
@DisplayName("jar 内配置文件约束")
class ApplicationConfigTest {

  @Test
  @DisplayName("classpath 上没有 profile 文件")
  void should_not_ship_profile_files() {
    assertThat(getClass().getResource("/application-dev.yml")).isNull();
    assertThat(getClass().getResource("/application-container.yml")).isNull();
  }
}
```

- [ ] **Step 2: 看它失败**

Run: `$R/gradlew -p $R :patra-api:patra-gateway-boot:test --tests '*ApplicationConfigTest' > $SCRATCH/t6.log 2>&1; echo "exit=$?"; grep -E "FAILED|Expecting" $SCRATCH/t6.log | head`

Expected: `exit=1`，`should_not_ship_profile_files() FAILED`。

- [ ] **Step 3: 删掉两个 profile 文件**

```bash
git -C $R rm -q patra-api/patra-gateway-boot/src/main/resources/application-dev.yml \
  patra-api/patra-gateway-boot/src/main/resources/application-container.yml
```

- [ ] **Step 4: 看它通过**

Run: 同 Step 2。Expected: `exit=0`。

- [ ] **Step 5: 加第二条测试**

与 Task 3 Step 5 相同的 import、测试方法和 `propertyNames` 辅助方法：

```java
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;
```

```java
  @Test
  @DisplayName("application.yml 不写服务名、激活的 profile 和 Nacos 接入")
  void should_leave_name_profile_and_nacos_to_launcher_and_starter() throws IOException {
    assertThat(propertyNames("application.yml"))
        .noneMatch(
            name ->
                name.startsWith("spring.application.name")
                    || name.startsWith("spring.profiles.active")
                    || name.startsWith("spring.cloud.nacos"));
  }

  /// 读出 classpath 上一份 YAML 的全部属性名，多文档合在一起。
  ///
  /// @param resource classpath 上的文件名
  /// @return 扁平化后的属性名
  /// @throws IOException 读取失败时
  private static List<String> propertyNames(String resource) throws IOException {
    return new YamlPropertySourceLoader()
        .load(resource, new ClassPathResource(resource)).stream()
            .map(source -> (EnumerablePropertySource<?>) source)
            .flatMap(source -> Arrays.stream(source.getPropertyNames()))
            .toList();
  }
```

- [ ] **Step 6: 看它失败**

Run: 同 Step 2。Expected: `exit=1`，`should_leave_name_profile_and_nacos_to_launcher_and_starter() FAILED`。

- [ ] **Step 7: 改 `application.yml` 和启动类**

`application.yml`：

1. 删掉 `spring.application`、`spring.profiles` 两块。
2. `spring.data.redis` 上面的注释「会话存储：地址按 profile 给（dev 默认本机 16379，container 由环境变量注入）。」改为「会话存储：地址、账号在 Nacos 公共配置 patra-common.yaml。」，下一行「命令超时 2 秒……」保留。
3. `spring.cloud` 下删掉 `# Nacos Service Discovery` 注释和整个 `nacos:` 块，`gateway:` 块原样保留。改完是：

```yaml
  cloud:
    # Gateway Route Configuration（WebMVC 版）
    gateway:
```

4. 文件头注释在「鉴权：……」一行后加一行：`# 随环境变的值（Redis、身份断言公钥、私钥引用）在 Nacos：patra-common.yaml、patra-gateway.yaml`。

启动类：

```java
package dev.linqibin.patra.gateway;

import dev.linqibin.patra.starter.nacos.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// （类 Javadoc 前面部分原样保留，最后一段改为：）
///
/// 默认端口 9528。路由与客户端配置见 `application.yml`，随环境变的值在 Nacos（`patra-common.yaml`、
/// `patra-gateway.yaml`），设计见 `docs/patra/specs/2026-10-09-gateway-webmvc-design.md` 与
/// `docs/patra/specs/2026-10-09-gateway-auth-design.md`。
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class PatraGatewayApplication {

  /// 经启动器启动应用，服务名 `patra-gateway`。
  ///
  /// @param args 传递给应用的命令行参数
  public static void main(String[] args) {
    PatraApplication.run("patra-gateway", PatraGatewayApplication.class, args);
  }
}
```

- [ ] **Step 8: 看它通过，跑模块检查和集成测试**

Run: `$R/gradlew -p $R :patra-api:patra-gateway-boot:check :patra-api:patra-gateway-boot:integrationTest > $SCRATCH/t6-check.log 2>&1; echo "exit=$?"; tail -5 $SCRATCH/t6-check.log`

Expected: `exit=0`，`BUILD SUCCESSFUL`。

如果集成测试因为缺 `spring.application.name` 失败，在 `src/integrationTest/resources/application-test.yml` 的 `spring:` 下补：

```yaml
  application:
    name: patra-gateway
```

重跑，Expected `exit=0`，并在 ledger 记一条 Ruling。

- [ ] **Step 9: 变异检查**

临时在 `application.yml` 的 `spring.cloud` 下加回 `nacos.discovery.fail-fast: false`，跑 Step 2，Expected `exit=1`；还原后 Expected `exit=0`。记进 ledger。

- [ ] **Step 10: 提交**

```bash
$R/gradlew -p $R spotlessApply -q
git -C $R add patra-api/patra-gateway-boot/
git -C $R diff --cached --stat
git -C $R commit -m "feat(config-center): gateway 改为从 Nacos 读配置 (PAP-76)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: 容器侧清理

**Files:**
- Modify: `patra-infra/docker/docker-compose.apps.yaml`（头部注释；registry、object-storage、identity、gateway 的 `env_file`）
- Delete: `patra-infra/docker/.env.registry`、`.env.object-storage`、`.env.identity`、`.env.gateway`
- Modify: `patra-infra/docker/.env.common`（删身份断言公钥）

**Interfaces:**
- Consumes：Task 2 的 Nacos 文件（库地址、账号、Redis、公钥已在里面）。
- Produces：Task 9 的 mini 部署用的 compose。

纯配置，不走 TDD；用 `docker compose config --quiet` 和路由测试检查。

- [ ] **Step 1: 改 compose**

4 个服务的 `env_file` 各删一行 `- .env.<svc>`（`.env.registry`、`.env.object-storage`、`.env.identity`、`.env.gateway`），其余条目不动。

头部注释第 7–9 行改为：

```yaml
# env_file 按「共享坐标、服务专属、密钥」叠加（后者覆盖同名）：
#   .env.common   共享基建坐标（mini 主机名 + 宿主机映射端口，与 IDE 一致），dev 默认
#   .env.<svc>    服务专属（DB / bucket / 日志路径，dev 默认）。只剩 catalog、ingest；
#                 registry、object-storage、identity、gateway 的配置在 Nacos（patra-infra/nacos/dev/）
```

- [ ] **Step 2: 删环境文件，改 `.env.common`**

```bash
git -C $R rm -q patra-infra/docker/.env.registry patra-infra/docker/.env.object-storage \
  patra-infra/docker/.env.identity patra-infra/docker/.env.gateway
```

`.env.common` 删掉末尾「身份断言公钥」一段（`# --- 身份断言公钥` 注释起到 `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS=` 那一行，含其上方的 3 行注释）。`SPRING_PROFILES_ACTIVE`、`REDIS_HOST`、`REDIS_PORT`、MinIO 坐标保留（PAP-77 清理）。

- [ ] **Step 3: 校验 compose**

Run: `docker compose -f $R/patra-infra/docker/docker-compose.apps.yaml config --quiet; echo "exit=$?"`

Expected: `exit=0`（本机 `~/.patra/secrets/` 有 `nacos.env`、`redis.env`、`gateway.env`）。不要去掉 `--quiet`：完整输出会把密钥打到终端。本机没有 docker 命令时，改为 `ssh linqibin@linqibins-mac-mini` 不可行（mini 上的仓库还没有本分支），就在 ledger 记一条 Ruling，留给 Task 9 Step 9 的部署兜底。

- [ ] **Step 4: 没有遗留引用**

```bash
grep -rn --exclude-dir=node_modules --exclude-dir=build --exclude-dir=.git --exclude-dir=worktrees \
  -e '\.env\.registry' -e '\.env\.object-storage' -e '\.env\.identity' -e '\.env\.gateway' \
  -e 'PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS' -e 'REGISTRY_DB_' -e 'STORAGE_DB_' -e 'IDENTITY_DB_' \
  $R/patra-infra $R/patra-api $R/.github
```

Expected: 只剩 Task 8 要改的文档行（`patra-infra/CLAUDE.md`、`patra-infra/docker/README.md`、`patra-api/patra-identity/README.md`、`patra-api/patra-gateway-boot/README.md`、`patra-api/README.md`、`patra-infra/docker/secrets/*.example`），没有代码、compose、脚本、workflow 的命中。

- [ ] **Step 5: 路由测试**

Run: `bash $R/patra-infra/cd/detect-changes.test.sh 2>&1 | tail -2`

Expected: `通过 60 / 失败 0`（用例只引用 `.env.catalog`、`.env.common`、`.env.dev`，不受影响）。

- [ ] **Step 6: 提交**

```bash
git -C $R add patra-infra/docker/
git -C $R diff --cached --stat
git -C $R commit -m "chore(config-center): 删除已搬进 Nacos 的服务环境文件和公钥 (PAP-76)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: 文档

**Files:**
- Modify: `patra-infra/CLAUDE.md`（第 45、59、71 行）
- Modify: `patra-infra/docker/README.md`（第 102、153、165、192–197、219、224、231 行，「应用侧配置」一节）
- Modify: `patra-infra/docker/secrets/gateway.env.example`、`nacos.env.example`、`redis.env.example`（「谁读」几行）
- Modify: `patra-api/patra-identity/README.md`（第 7 节）
- Modify: `patra-api/patra-gateway-boot/README.md`（文件树、「配置」一节）
- Modify: `patra-api/README.md`（「环境配置文件」一节）
- Modify: `docs/patra/specs/2026-10-13-config-migration-batch1-design.md`（第 10 节加一条）

**Interfaces:**
- Consumes：Task 2–7 的结果。
- Produces：无。

纯文档，不走 TDD。行号是写计划时（`fd919acb8`）的位置，按内容定位。

- [ ] **Step 1: `patra-infra/CLAUDE.md`**

第 45 行（非显性约束 1）的「各服务 yml 为 `${NACOS_HOST:${PATRA_INFRA_HOST:127.0.0.1}}`，与其它基建同一个开关；」改为：

```markdown
地址缺省为 `${PATRA_INFRA_HOST:127.0.0.1}:8848`，与其它基建同一个开关：registry、object-storage、identity、gateway 由 Nacos starter 注入（`PATRA_NACOS_ADDR` 可整体覆盖），ingest、catalog 迁移前仍写在 yml 里（`${NACOS_HOST:${PATRA_INFRA_HOST:127.0.0.1}}`）；
```

第 59 行「**加新服务 = 加一个条目**（再在 compose 加 service 块 + 建 `.env.<svc>`）」改为「**加新服务 = 加一个条目**（再在 compose 加 service 块、在 `patra-infra/nacos/dev/` 加 `<服务名>.yaml`）」。

第 71 行整条改为：

```markdown
- **环境文件，密钥在仓库外**：`env_file` 顺序叠加 `.env.common`（共享基建坐标：mini 主机名加映射端口、内网 dev 默认、Redis 地址）→ `.env.<svc>`（只剩 catalog、ingest 的服务专属 DB/bucket/日志路径；registry、object-storage、identity、gateway 的配置在 Nacos，源文件在 `nacos/dev/`，身份断言公钥也在那里的 `patra-common.yaml`）→ `${HOME}/.patra/secrets/` 下的密钥文件（`redis.env` 给 catalog/identity/gateway、`gateway.env` 给 gateway、`nacos.env` 给 6 个后端服务，三者必需；`<svc>.env` 可选；后者覆盖同名）。**Redis 密码、网关私钥、外部数据源 API key（Scopus / 青果 proxy / RocketMQ ACL 等）一律只进 `~/.patra/secrets/`**，committed 文件只放内网 dev 默认值和公钥，Nacos 配置里只写 `${变量}`。必需文件缺一个，compose 对整个 apps 项目的命令都会失败（portal 也部署不了）。
```

- [ ] **Step 2: `patra-infra/docker/README.md`**

第 102 行「各服务 yml 的 `NACOS_HOST` 缺省跟随 `PATRA_INFRA_HOST`。账号 `patra-app` 来自 `~/.patra/secrets/nacos.env`，由 dev profile 导入，缺文件时启动失败。」改为：

```markdown
Nacos 地址缺省跟随 `PATRA_INFRA_HOST`：registry、object-storage、identity、gateway 由 Nacos starter 注入，ingest、catalog 迁移前仍写在 yml 里。账号 `patra-app` 来自 `~/.patra/secrets/nacos.env`：前 4 个服务由 starter 读取，ingest、catalog 由 dev profile 导入；缺了启动失败。
```

密钥表 `redis.env` 一行的「谁加载」改为「core 栈的 redis；catalog、identity、gateway 的容器和本机进程」，「缺失时」改为「compose 报出路径并拒绝执行；本机进程启动失败，报出缺的文件路径或变量名」。

「两种进程怎么读到这些文件」第三条改为：

```markdown
- 本机进程：Nacos starter 把 `~/.patra/secrets/` 下的全部 `*.env` 读进来（ingest、catalog 迁移前仍由 `application-dev.yml` 的 `spring.config.import` 读），IDEA 和 shell 都不用设这些变量。
```

「公钥不是机密，提交在三处：」及其下的三条列表、「两份 YAML 里的值要用单引号包住。」整段改为：

```markdown
公钥不是机密，只有一处：Nacos 公共配置 `patra-infra/nacos/dev/patra-common.yaml` 的 `patra.security.identity-assertion.public-keys`，值用单引号包住。identity 和 gateway 都从这里读。
```

「本机以 dev profile 起的进程重启一次即可。」改为「本机进程重启一次即可。」

换签名密钥第 2 步改为：

```markdown
2. 把新公钥加进 `patra-common.yaml` 的 `keys` 数组，旧公钥保留。提交、推送：CD 只把配置推到 Nacos，不重新部署服务，配置也不会自动生效。再按「改了密钥文件之后」的命令重建 identity 和 gateway 容器，本机进程各重启一次。
```

第 5 步改为：

```markdown
5. 从 `patra-common.yaml` 删掉旧公钥，提交、推送，再同样重建 identity 和 gateway 容器、重启本机进程。
```

「应用侧配置」一节：示例 YAML 删掉整个 `cloud:` 块；示例上方的「`application.yml` 引用方式示例：」改为「配置（Nacos 里的文件或 `application.yml`）的引用方式示例：」；示例下方加一段：

```markdown
Nacos 的地址、账号和服务发现设置由 `patra-spring-boot-starter-nacos` 注入，服务的配置里不写（ingest、catalog 迁移前仍写在各自的 yml 里）。
```

- [ ] **Step 3: 三份密钥模板**

`secrets/gateway.env.example` 第 3–5 行改为：

```
# 谁读：gateway 的容器（compose env_file）和本机起的网关（Nacos starter 读 ~/.patra/secrets/）。
# 对应的公钥不是机密，提交在 Nacos 公共配置 patra-infra/nacos/dev/patra-common.yaml。
```

`secrets/nacos.env.example` 第 3–4 行改为：

```
# 谁读：6 个后端服务的容器（compose env_file，必需）；本机起的进程（registry、object-storage、identity、
#       gateway 由 Nacos starter 读，ingest、catalog 由 application-dev.yml 的 spring.config.import 读）；
#       scripts/nacos-init.sh 按它建账号，nacos-verify.sh 用它验权限。
```

`secrets/redis.env.example` 第 3–4 行改为：

```
# 谁读：core 栈的 redis（启动时设密码）；catalog、identity、gateway 的容器（compose env_file）
#       和这三个服务在本机起的进程（identity、gateway 由 Nacos starter 读，catalog 由 application-dev.yml 的 spring.config.import 读）。
```

- [ ] **Step 4: identity README 第 7 节**

第一条改为：

```markdown
- 配置从 mini 的 Nacos 读（`patra-common.yaml`、`patra-identity.yaml`，源文件在 `patra-infra/nacos/dev/`）：连 mini 上的 `patra_identity` 库和 Redis（`PATRA_INFRA_HOST`），和 mini 上的容器共用同一个 Redis 0 号库。Redis 密码在 Nacos 里写成 `${REDIS_PASSWORD}`，本机由 Nacos starter 从 `~/.patra/secrets/redis.env` 读；生成和复制见 `patra-infra/docker/README.md`「密钥」。
```

「identity 从网关签的身份断言取当前用户……」和「容器里的其余变量……」两段改为：

```markdown
identity 从网关签的身份断言取当前用户，要配网关的公钥：写在 Nacos 公共配置 `patra-common.yaml` 里，和网关共用一份，换密钥时改这一处。集成测试不读这些：安全 starter 的测试支持会自动注入测试公钥。

容器和本机读同一份 Nacos 配置，只差 `PATRA_INFRA_HOST` 等本地变量；容器里的 `REDIS_PASSWORD` 来自 compose 加载的 `~/.patra/secrets/redis.env`。
```

- [ ] **Step 5: gateway README**

文件树里 `src/main/resources/` 下三行改为一行：

```
│   └── application.yml                           # 路由、HTTP 客户端、超时、虚拟线程、Redis 超时、错误前缀；随环境变的值在 Nacos
```

「配置」一节的表格：删掉 `SPRING_PROFILES_ACTIVE`、`REDIS_HOST` / `REDIS_PORT`、`PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS` 三行；`NACOS_HOST` / `NACOS_PORT` 一行改为 `| PATRA_NACOS_ADDR | Nacos 地址 | ${PATRA_INFRA_HOST:127.0.0.1}:8848，由 Nacos starter 注入 |`（变量名和默认值加反引号）；`NACOS_USERNAME` / `NACOS_PASSWORD` 一行的默认值改为「无，来自 `~/.patra/secrets/nacos.env`（容器经 `env_file`，本机由 Nacos starter 读）」。

表格下面「端口 9528。」之后的列表改为：

```markdown
随环境变的值在 Nacos：Redis 地址和账号、身份断言公钥在 `patra-common.yaml`，私钥引用在 `patra-gateway.yaml`（源文件在 `patra-infra/nacos/dev/`）。其余默认值在 `application.yml`。

- 容器：`REDIS_PASSWORD` 和私钥由 compose 从 `~/.patra/secrets/` 加载。
- 本机：Nacos starter 读同一批文件，不用设环境变量。
- 要看路由和负载均衡的 DEBUG 日志：在 Nacos 的 `patra-gateway.yaml` 里临时加 `logging.level.org.springframework.cloud.gateway: DEBUG`，重启网关生效，看完删掉。
- 密钥的生成、复制和换密钥的顺序，见 `patra-infra/docker/README.md`「密钥」。
```

- [ ] **Step 6: `patra-api/README.md`「环境配置文件」一节**

在第一条前面加：

```markdown
- **registry、object-storage、identity、gateway**：配置从 Nacos 读（`patra-common.yaml` 加 `{服务名}.yaml`，源文件在 `patra-infra/nacos/dev/`），启动类用 `PatraApplication.run(服务名, …)`。jar 里只有 `application.yml`，放端口、连接池、超时等随代码变的默认值；库地址、Redis、公钥、密钥引用等随环境变的值在 Nacos。IDE 与容器读同一份配置，只差 `PATRA_INFRA_HOST`、`PATRA_ADVERTISE_IP`、`PATRA_LOG_DIR` 三个本地变量。设计见 `docs/patra/specs/2026-10-13-config-migration-batch1-design.md`。
```

原第一条开头的「Spring profiles:」改为「ingest、catalog（迁移前）的 Spring profiles:」；第二条「每个 `*-boot` 模块现在使用」改为「ingest、catalog 的 `*-boot` 模块使用」；第四条的例子「(例如: `REGISTRY_DB_URL`, `INGEST_DB_URL`)」改为「(例如: `INGEST_DB_URL`, `CATALOG_DB_URL`)」。

- [ ] **Step 7: spec 第 10 节加一条**

在 `docs/patra/specs/2026-10-13-config-migration-batch1-design.md` 第 10 节末尾加：

```markdown
- `.claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md` 仍按 dev / container 两个 profile 描述配置，ingest、catalog 迁完后改为 Nacos 的写法。
```

- [ ] **Step 8: 核对**

```bash
grep -rn --exclude-dir=node_modules --exclude-dir=build --exclude-dir=.git --exclude-dir=worktrees \
  -e 'application-dev' -e 'application-container' -e '三处' -e 'dev profile 起' -e '\.env\.registry' \
  -e '\.env\.identity' -e '\.env\.gateway' -e '\.env\.object-storage' -e 'PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS' \
  $R/patra-infra/CLAUDE.md $R/patra-infra/docker/README.md $R/patra-infra/docker/secrets \
  $R/patra-api/README.md $R/patra-api/patra-identity/README.md $R/patra-api/patra-gateway-boot/README.md
```

Expected: 剩下的命中都在说 ingest、catalog（例如「ingest、catalog 由 application-dev.yml 的 spring.config.import 读」），没有描述 registry、object-storage、identity、gateway 的旧写法。

- [ ] **Step 9: 提交**

```bash
git -C $R add patra-infra/CLAUDE.md patra-infra/docker/README.md patra-infra/docker/secrets/ \
  patra-api/README.md patra-api/patra-identity/README.md patra-api/patra-gateway-boot/README.md \
  docs/patra/specs/2026-10-13-config-migration-batch1-design.md
git -C $R diff --cached --stat
git -C $R commit -m "docs(config-center): 同步四个服务改从 Nacos 读配置后的说明 (PAP-76)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: 全量验证与 mini 验收

**Files:** 正常情况下无代码改动。只有 Step 10 判定快照日志刷屏时改 `patra-infra/docker/service.Dockerfile`。

**Interfaces:**
- Consumes：Task 1–8 的全部产物。
- Produces：验收记录，写进 ledger 和最终消息。

- [ ] **Step 1: 本机全量**

Run: `$R/gradlew -p $R check integrationTest e2eTest > $SCRATCH/t9-full.log 2>&1; echo "exit=$?"; tail -5 $SCRATCH/t9-full.log`

Expected: `exit=0`，`BUILD SUCCESSFUL`（覆盖 ingest、catalog）。失败时按 superpowers:systematic-debugging 查原因，不改测试迁就。

- [ ] **Step 2: 停下，征得用户同意后再动 mini 的 Nacos**

向用户说明 Step 3–6 会做什么：

- 把本分支 `patra-infra/nacos/dev/` 推到 mini 的 Nacos（预计 5 份更新、2 份未变化）。mini 上现有的旧镜像不导入 Nacos 配置，不受影响。
- 本机逐个启动 4 个服务，它们会注册到 mini 的 Nacos，期间 mini 的网关可能把少量请求转到本机实例；每个服务验完就停。

只有用户明确同意后才执行 Step 3。

- [ ] **Step 3: 推送 Nacos 配置**

```bash
NACOS_ADDR=http://100.103.73.27:8848 bash $R/patra-infra/scripts/nacos-push.sh --check; echo "exit=$?"
```

Expected: 5 行「更新」（`patra-common.yaml` 和 4 份服务文件），2 份未变化（`patra-catalog.yaml`、`patra-ingest.yaml`），没有「删除」，`exit=2`。出现「删除」就停下，把清单给用户看。

```bash
NACOS_ADDR=http://100.103.73.27:8848 bash $R/patra-infra/scripts/nacos-push.sh; echo "exit=$?"
```

Expected: `新增 0，更新 5，删除 0，未变化 2`，`exit=0`。人工核对输出里没有密码和令牌。

- [ ] **Step 4: 准备本机启动环境**

本机 JVM 会继承 macOS 系统代理，去 100.x 的请求会进代理（memory：macos-jvm-system-proxy-cidr），每条启动命令前都带：

```bash
NP='localhost|127.*|100.*|192.168.*|172.*|10.*'
export JAVA_TOOL_OPTIONS="-Dhttp.nonProxyHosts=$NP -Dhttps.nonProxyHosts=$NP -Dsocks.nonProxyHosts=$NP"
export PATRA_INFRA_HOST=100.103.73.27 PATRA_ADVERTISE_IP=100.70.109.37
```

- [ ] **Step 5: 本机逐个启动 4 个服务**

按 registry → object-storage → identity → gateway 的顺序，每个服务在后台运行（Bash `run_in_background`），日志写到 scratchpad。以 registry 为例：

```bash
NP='localhost|127.*|100.*|192.168.*|172.*|10.*'
JAVA_TOOL_OPTIONS="-Dhttp.nonProxyHosts=$NP -Dhttps.nonProxyHosts=$NP -Dsocks.nonProxyHosts=$NP" \
PATRA_INFRA_HOST=100.103.73.27 PATRA_ADVERTISE_IP=100.70.109.37 \
$R/gradlew -p $R :patra-api:patra-registry:patra-registry-boot:bootRun \
  --args='--logging.level.org.springframework.boot.context.config=TRACE' > $SCRATCH/run-registry.log 2>&1
```

另外三个把模块路径换成 `:patra-api:patra-object-storage:patra-object-storage-boot`、`:patra-api:patra-identity:patra-identity-boot`、`:patra-api:patra-gateway-boot`，日志名对应改。

每个服务等到日志出现 `Started Patra…Application` 或报错退出，然后检查：

```bash
grep -E "Adding imported property source|Started Patra|register finished|HikariPool-1 - Start completed" $SCRATCH/run-registry.log
curl -s http://127.0.0.1:6000/actuator/health
```

Expected（4 个服务都要满足）：

- 导入日志里有 `patra-common.yaml` 和本服务的 `{服务名}.yaml`；registry 另有 `registry-error-config.yaml`（Review Focus 4）。
- 有 `Started Patra…Application`，说明签到检查通过（不通过会启动失败）。
- 有本服务注册到 Nacos 的日志（`register finished`，地址是 `100.70.109.37`）。
- registry、object-storage、identity 有 `HikariPool-1 - Start completed`（数据库已连上）；gateway 没有数据源。
- `actuator/health` 返回 `{"status":"UP"}`（端口：registry 6000、object-storage 6200、identity 6400、gateway 9528）。identity、gateway 的 health 含 Redis，UP 即 Redis 已连上。gateway 能起来说明公钥与私钥成对（Review Focus 5）。

验完停掉这个服务（TaskStop 停后台任务），再用 `lsof -nP -iTCP:<端口> -sTCP:LISTEN` 确认端口已释放；没释放就 `kill` 那个 PID。再启动下一个。

- [ ] **Step 6: 本机缺密钥文件时的表现（Review Focus 1）**

用一个只有 `nacos.env` 的临时 home 启动 identity 的 jar，看缺 `REDIS_PASSWORD` 时怎么失败（不复制密钥，只建软链接）：

```bash
mkdir -p $SCRATCH/home-no-redis/.patra/secrets
ln -sf ~/.patra/secrets/nacos.env $SCRATCH/home-no-redis/.patra/secrets/nacos.env
$R/gradlew -p $R :patra-api:patra-identity:patra-identity-boot:bootJar -q
NP='localhost|127.*|100.*|192.168.*|172.*|10.*'
PATRA_INFRA_HOST=100.103.73.27 java -Dhttp.nonProxyHosts="$NP" -Dhttps.nonProxyHosts="$NP" -Dsocks.nonProxyHosts="$NP" \
  -Duser.home=$SCRATCH/home-no-redis -Dspring.cloud.nacos.discovery.register-enabled=false \
  -jar $R/patra-api/patra-identity/patra-identity-boot/build/libs/*.jar > $SCRATCH/run-identity-no-redis.log 2>&1
```

后台运行，最多等 90 秒。

Expected：启动失败，日志里点出 `REDIS_PASSWORD`（PAP-74 spec 的承诺）。

如果它启动成功（Spring 绑定配置时把 `${REDIS_PASSWORD}` 当字面量用了），停掉进程，**不在本 Issue 里改 starter**：在 ledger 记一条 `Task 9: Ruling:`，把现象（启动成功、Redis 用字面量密码认证失败的日志）原样记下，最终消息里交给用户决定是否立 Issue，同时把 Task 8 里写的「本机进程启动失败，报出缺的文件路径或变量名」改成实际行为并单独提交 docs。

最后删掉临时目录：`rm -rf $SCRATCH/home-no-redis`。

- [ ] **Step 7: 停下，征得用户同意后再推送分支、部署到 mini**

向用户说明：

- 需要把 `feat/v0.9-config-center-api` 推到 GitHub（ship-gate 钩子拦截时请用户自己推送）；
- 然后在本分支手动触发 `cd.yml`（`service` 留空）：推送 Nacos 配置（应全部未变化）→ 构建并部署 6 个服务；
- 已知代价（spec 第 8 节）：部署后回滚不到迁移前的旧镜像；mini 上会短暂运行未合入 main 的代码，main 的下一次部署会覆盖回去。

只有用户明确同意后才执行 Step 8。

- [ ] **Step 8: 触发 CD**

分支推送后：

```bash
gh workflow run cd.yml --repo linqibin0826/patra --ref feat/v0.9-config-center-api
gh run list --repo linqibin0826/patra --workflow cd.yml --branch feat/v0.9-config-center-api --limit 1
```

用 `gh run watch <run-id> --repo linqibin0826/patra --exit-status`（后台运行）等它结束。

Expected：`detect-changes`、`nacos-push`、`build-deploy` 三个 job 都成功；`nacos-push` 日志是「未变化 7」；`build-deploy` 部署了 6 个服务，`deploy.sh` 没有触发回滚。

- [ ] **Step 9: 容器健康（Review Focus 2、3）**

```bash
ssh linqibin@linqibins-mac-mini 'PATH=/usr/local/bin:$PATH docker ps --filter name=patra- --format "{{.Names}} {{.Status}} {{.Image}}"'
```

Expected：`patra-registry`、`patra-object-storage`、`patra-identity`、`patra-gateway`、`patra-catalog`、`patra-ingest` 都是 `(healthy)`，镜像 tag 是本分支的提交 SHA。

- [ ] **Step 10: 快照日志（spec 第 6 节）与占位符告警**

```bash
ssh linqibin@linqibins-mac-mini 'PATH=/usr/local/bin:$PATH; for c in patra-registry patra-object-storage patra-identity patra-gateway; do echo "$c snapshot=$(docker logs $c 2>&1 | grep -ci snapshot) placeholder=$(docker logs $c 2>&1 | grep -c "Could not resolve placeholder")"; done'
```

Expected：`placeholder=0`。`snapshot` 计数记进 ledger，再看一条样例（`docker logs patra-registry 2>&1 | grep -i snapshot | head -3`，只看与快照相关的行）：

- 只有启动时零星几行、没有持续增长（隔 2 分钟再数一次，数字不变）：记录结论「不刷屏」，不改 Dockerfile。
- 持续增长：在 `patra-infra/docker/service.Dockerfile` 的 `ENTRYPOINT` 里加 `-DJM.SNAPSHOT.PATH=/app`（`/app` 已归 appuser 所有，nacos-client 会在下面建 `nacos/config`）：

  ```dockerfile
  ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-DJM.SNAPSHOT.PATH=/app", "-jar", "app.jar"]
  ```

  并在上方注释写明原因（appuser 没有 home 目录，nacos-client 默认把快照写到 `${user.home}/nacos/config`）。提交 `fix(config-center): 给 nacos-client 快照一个可写目录 (PAP-76)`，征得用户同意后再推送、再触发一次 CD，重复 Step 9–10。

- [ ] **Step 11: 门户行为与网关路由**

门户注册、登录、登出的 e2e 对准 mini 网关（剥掉代理变量，单 worker）：

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u http_proxy -u https_proxy -u ALL_PROXY -u all_proxy \
  PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 \
  pnpm -C $R/patra-portal test:e2e tests/e2e/auth.spec.ts --workers=1 > $SCRATCH/t9-e2e.log 2>&1; echo "exit=$?"; tail -15 $SCRATCH/t9-e2e.log
```

Expected：`exit=0`，用例全部通过，没有 skipped（skip 说明没连上网关）。冷编译挂一条时单独重跑那一条。

经网关访问有路由的 4 个服务：

```bash
for s in patra-registry patra-identity patra-catalog patra-ingest; do
  printf '%s %s\n' "$s" "$(curl -s -o /dev/null -w '%{http_code}' http://100.103.73.27:9528/$s/v3/api-docs)"
done
```

Expected：4 行都是 `200`。object-storage 没有网关路由，由 Step 9 的健康状态和 Nacos 注册覆盖。

- [ ] **Step 12: 两种运行方式一致（只看变量名）**

```bash
ssh linqibin@linqibins-mac-mini 'PATH=/usr/local/bin:$PATH; for c in patra-registry patra-object-storage patra-identity patra-gateway; do echo "== $c"; docker inspect -f "{{range .Config.Env}}{{println .}}{{end}}" $c | cut -d= -f1 | sort; done'
```

不要去掉 `cut`：完整输出会打印密码和私钥。

Expected：每个容器的变量名只有这几类：

- 本地变量：`PATRA_INFRA_HOST`、`PATRA_LOG_DIR`；
- Nacos 账号：`NACOS_USERNAME`、`NACOS_PASSWORD`；
- 密钥：identity、gateway 有 `REDIS_PASSWORD`，gateway 有 `PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY`；
- `.env.common` 里留给 PAP-77 清理的共享项：`SPRING_PROFILES_ACTIVE`、`REDIS_HOST`、`REDIS_PORT`、`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`；
- 镜像自带：`PATH`、`JAVA_HOME`、`JAVA_VERSION`、`LANG`、`LANGUAGE`、`LC_ALL` 等。

不应出现 `REGISTRY_DB_*`、`STORAGE_DB_*`、`IDENTITY_DB_*`、`PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS`。

- [ ] **Step 13: 收尾**

- 走 executing-plans / subagent-driven-development 的最终审查（换一个最强模型的 reviewer 审 `fd919acb8..HEAD`），修完再进入下一步。
- 把验收结论（快照日志、Step 6 的结果、e2e、变量名清单）写进 ledger 和最终消息。
- 问用户：是否把 PAP-76 置为 `Review`。
- 合入 main 之后 main 的 CD 会再部署一次，那次 `nacos-push` 应是「未变化 7」——作为待合入后核对的事项写进最终消息。
