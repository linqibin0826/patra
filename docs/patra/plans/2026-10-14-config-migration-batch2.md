# 配置迁移（二）实施计划（PAP-77）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ingest、catalog 改为从 Nacos 读配置，6 个服务全部迁完；compose 的环境文件只剩本地变量、Nacos 账号和密钥，删除不生效的 `.env.dev`。

**Architecture:** 与 PAP-76 相同：启动类改用 `PatraApplication.run`，由启动器导入 Nacos 的 `patra-common.yaml` 和 `{服务名}.yaml`；jar 里只留 `application.yml`，删掉两个 profile 文件；随环境变的值写进 `patra-infra/nacos/dev/`。每个 boot 模块新增 `ApplicationConfigTest`，除 PAP-76 的两条用例外，再加一条「Nacos 接管的键不写在 jar 里」，驱动删除旧的环境变量入口。容器侧删掉 `.env.ingest`、`.env.catalog`、`.env.dev`，`.env.common` 只留本地变量。

**Tech Stack:** Java 25、Spring Boot 4.0.8、SCA 2025.1.0.0（nacos-client 3.1.1）、Nacos 3.2.4、Gradle 9.5、JUnit 5 + AssertJ、Testcontainers、Docker Compose、GitHub Actions（`cd.yml`）、XXL-Job 3.2。

**Spec:** `docs/patra/specs/2026-10-14-config-migration-batch2-design.md`

## Global Constraints

- 服务名（也是 Nacos 服务文件的 dataId 前缀）：`patra-ingest`、`patra-catalog`。迁移顺序：ingest → catalog。
- Nacos 文件内容逐字按 spec 第 5 节：库地址写 `jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/<库名>`；批处理库密码写 `${PATRA_DB_PASSWORD:123456}`；MinIO 账号写 `${MINIO_ACCESS_KEY:minioadmin}`、`${MINIO_SECRET_KEY:minioadmin123}`；可选外部凭据写带空默认值的引用（`${SCOPUS_API_KEY:}`、`${PROXY_AUTH_KEY:}`、`${PROXY_AUTH_PWD:}`、`${ROCKETMQ_AK:}`、`${ROCKETMQ_SK:}`）；catalog 的 Redis 密码写 `${REDIS_PASSWORD}`；每份文件保留签到行。
- 公共配置不写没有默认值的引用（PAP-76 第 10 节）。
- spec 决定 2 的取值：ingest 表达式编译严格模式、`max-query-length: 5000`、`warn-param-count: 50`、`max-param-count: 100`，写在 jar；日志级别、catalog 导入上限、Redis 超时、调内部服务的连接超时都用默认值（不写）；代理开关 `false` 写在 Nacos。与 starter 默认值相同的值不写（spec 第 3 节末尾）。
- 仓库是公开的：不提交真实密钥；命令输出不打印密码、令牌、私钥（看容器环境变量只取变量名，查 MySQL 用容器里的 `$MYSQL_ROOT_PASSWORD`，不把值放进命令行）。
- **凡动 mini 的步骤（推送 Nacos、推送分支、触发 CD、触发 Job），执行前先征得用户同意。** 不自动推送分支；ship-gate 钩子拦截时请用户自己推送，不改钩子。命令和 heredoc 正文里不写字面量的推送命令。
- 提交格式 `<type>(config-center): 中文或小写服务名开头… (PAP-77)`，subject 不以大写英文开头，结尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`；提交前跑 `./gradlew spotlessApply`（改了 Java 时），`git diff --cached --stat` 只含本任务的路径。
- 命令一律用绝对路径，不 `cd`。仓库根：`/Users/linqibin/Projects/Products/patra`（下文简称 `$R`，执行时写全路径）；`$SCRATCH` 是会话 scratchpad 目录。
- 测试方法 snake_case，`@DisplayName` 写中文；Javadoc 用 `///`；不写全类名。

## Review Focus

1. **jar 和 Nacos 写了同一个键**：迁移后某个键同时出现在 jar 和 Nacos，哪个生效取决于导入顺序，改了 Nacos 可能不生效。期望 Nacos 接管的键在 jar 里一个都没有。测试：Task 2 Step 9、Task 3 Step 9（`should_leave_nacos_owned_keys_to_nacos`）。
2. **不经过 Nacos 的测试读不到搬走的键**：catalog 的 e2e 测试启动完整上下文，`ScopusConfiguration` 用 `@Value("${patra.catalog.scopus.api-key}")`（没有默认值）。jar 删掉这一项后测试上下文起不来。期望 e2e 测试照常通过。测试：Task 3 Step 12。
3. **Nacos 推送和部署之间的窗口**：推送公共配置后、新镜像部署前，mini 上的旧 ingest、catalog 镜像不读 Nacos 配置，PAP-76 的 4 个服务读到不绑定的 MinIO、xxl-job 键。期望 6 个容器都不受影响。测试：Task 6 Step 3（推送后立刻看 6 个容器健康）。
4. **遗留的引用**：删掉的环境文件、变量名还被脚本、compose、workflow 或代码引用，部署或排查时才暴露。期望只剩历史文档（runbook、旧 spec / plan）。测试：Task 4 Step 6。
5. **ingest 本机启动连 mini 的 RocketMQ、xxl-job**：IDE 里不再有 `application-dev.yml`，namesrv、xxl-job 地址都来自 Nacos。期望启动成功、执行器注册到 mini 的调度中心。测试：Task 6 Step 5。

---

### Task 1: 填写 Nacos 配置

**Files:**
- Modify: `patra-infra/nacos/dev/patra-common.yaml`
- Modify: `patra-infra/nacos/dev/patra-ingest.yaml`
- Modify: `patra-infra/nacos/dev/patra-catalog.yaml`
- Modify: `patra-infra/nacos/dev/README.md`
- Test: `patra-starters/patra-spring-boot-starter-nacos/src/test/java/dev/linqibin/patra/starter/nacos/NacosConfigFilesTest.java`（已有，检查这个目录）

**Interfaces:**
- Consumes：PAP-75 的检查规则（签到行、敏感键只写引用、无重复键、LF）。
- Produces：Task 2、3 的服务启动时读到的属性：
  - 公共：`linqibin.starter.object-storage.providers.minio.{endpoint,access-key,secret-key}`、`xxl.job.admin.addresses`；
  - ingest：`spring.datasource.url`、`linqibin.starter.object-storage.providers.minio.bucket`、`rocketmq.{name-server,access-key,secret-key}`；
  - catalog：`spring.datasource.url`、`spring.data.redis.password`、`patra.catalog.scopus.api-key`、`linqibin.starter.batch.datasource.{url,username,password}`、`linqibin.starter.object-storage.providers.minio.bucket`、`linqibin.starter.rest-client.proxy.tunnel.{enabled,auth-key,auth-pwd}`。

纯配置文件，不走 TDD；由 `NacosConfigFilesTest` 检查格式和密钥规则。

- [ ] **Step 1: 公共配置追加两段**

在 `patra-common.yaml` 末尾（`patra.security` 那段之后）追加，原有内容不动：

```yaml

linqibin:
  starter:
    object-storage:
      providers:
        minio:
          endpoint: http://${PATRA_INFRA_HOST:127.0.0.1}:19000
          access-key: ${MINIO_ACCESS_KEY:minioadmin}
          secret-key: ${MINIO_SECRET_KEY:minioadmin123}

xxl:
  job:
    admin:
      addresses: http://${PATRA_INFRA_HOST:127.0.0.1}:7070/xxl-job-admin
```

- [ ] **Step 2: 写两份服务文件**

`patra-ingest.yaml` 整个替换为：

```yaml
patra.config-source.service: patra-ingest.yaml

spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_ingest

linqibin:
  starter:
    object-storage:
      providers:
        minio:
          bucket: patra-ingest-cache

rocketmq:
  name-server: ${PATRA_INFRA_HOST:127.0.0.1}:9876
  access-key: ${ROCKETMQ_AK:}
  secret-key: ${ROCKETMQ_SK:}
```

`patra-catalog.yaml` 整个替换为：

```yaml
patra.config-source.service: patra-catalog.yaml

spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_catalog
  data:
    redis:
      password: ${REDIS_PASSWORD}

patra:
  catalog:
    scopus:
      api-key: ${SCOPUS_API_KEY:}

linqibin:
  starter:
    batch:
      datasource:
        url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_batch
        username: postgres
        password: ${PATRA_DB_PASSWORD:123456}
    object-storage:
      providers:
        minio:
          bucket: patra-catalog-cache
    rest-client:
      proxy:
        tunnel:
          enabled: false
          auth-key: ${PROXY_AUTH_KEY:}
          auth-pwd: ${PROXY_AUTH_PWD:}
```

- [ ] **Step 3: README 补公共配置的内容**

`patra-infra/nacos/dev/README.md` 第 6 行「`patra-common.yaml` 放各服务共用的数据库账号、Redis 坐标和身份断言公钥；」改为「`patra-common.yaml` 放各服务共用的数据库账号、Redis 坐标、身份断言公钥，以及 ingest、catalog 共用的 MinIO 坐标和 xxl-job 管理端地址；」，其余不动。

第 7 行末尾追加一句：「可以不配的外部凭据（Scopus key、青果代理、RocketMQ ACL）写成带空默认值的引用，例如 `${SCOPUS_API_KEY:}`：没配时服务照常启动，用到时才失败。」

- [ ] **Step 4: 跑配置检查**

Run: `$R/gradlew -p $R :patra-starters:patra-spring-boot-starter-nacos:test --tests '*NacosConfigFilesTest' > $SCRATCH/t1.log 2>&1; echo "exit=$?"; tail -5 $SCRATCH/t1.log`

Expected: `exit=0`，`BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交**

```bash
git -C $R add patra-infra/nacos/dev/
git -C $R diff --cached --stat
git -C $R commit -m "feat(config-center): 补全 ingest、catalog 的 Nacos 配置 (PAP-77)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected: 4 个文件。

---

### Task 2: ingest 迁移

**Files:**
- Create: `patra-api/patra-ingest/patra-ingest-boot/src/test/java/dev/linqibin/patra/ingest/ApplicationConfigTest.java`
- Delete: `patra-api/patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml`
- Delete: `patra-api/patra-ingest/patra-ingest-boot/src/main/resources/application-container.yml`
- Modify: `patra-api/patra-ingest/patra-ingest-boot/src/main/resources/application.yml`
- Modify: `patra-api/patra-ingest/patra-ingest-boot/src/main/resources/ingest-rocketmq.yaml`
- Modify: `patra-api/patra-ingest/patra-ingest-boot/src/main/java/dev/linqibin/patra/ingest/PatraIngestApplication.java`

**Interfaces:**
- Consumes：PAP-74 的 `PatraApplication.run(String applicationName, Class<?> source, String... args)`（`dev.linqibin.patra.starter.nacos.PatraApplication`，boot 模块已经能引用）；Task 1 的 `patra-ingest.yaml`。
- Produces：`ApplicationConfigTest` 第三条用例的写法，Task 3 照抄（改 package 和键清单）。

ingest-boot 没有 `@SpringBootTest`，`src/testFixtures` 里的测试 profile 没有测试在用（2026-10-14 核对），这里不改。

- [ ] **Step 1: 写第一条失败的测试（没有 profile 文件）**

```java
package dev.linqibin.patra.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// jar 里配置文件的约束（PAP-77）：随环境变的值在 Nacos，jar 里没有 profile 文件，
/// 服务名由启动器传入，Nacos 接入由 starter 注入，Nacos 接管的键不在 jar 里重复写。
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

Run: `$R/gradlew -p $R :patra-api:patra-ingest:patra-ingest-boot:test --tests '*ApplicationConfigTest' > $SCRATCH/t2.log 2>&1; echo "exit=$?"; grep -E "FAILED|Expecting" $SCRATCH/t2.log | head`

Expected: `exit=1`，`should_not_ship_profile_files() FAILED`，断言信息是期望 null、实际是 `application-dev.yml` 的 URL。

- [ ] **Step 3: 删掉两个 profile 文件**

```bash
git -C $R rm -q patra-api/patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml \
  patra-api/patra-ingest/patra-ingest-boot/src/main/resources/application-container.yml
```

- [ ] **Step 4: 看它通过**

Run: 同 Step 2。

Expected: `exit=0`。仍然失败、实际值指向 `build/resources/main/` 时，先跑 `$R/gradlew -p $R :patra-api:patra-ingest:patra-ingest-boot:cleanProcessResources` 再重跑，并在 ledger 记一条 Ruling。

- [ ] **Step 5: 加第二条测试（`application.yml` 不写服务名、profile、Nacos）**

加 import：

```java
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;
```

加测试和辅助方法：

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

Run: 同 Step 2。

Expected: `exit=1`，`should_leave_name_profile_and_nacos_to_launcher_and_starter() FAILED`，断言信息里列出 `spring.application.name`、`spring.profiles.active`、`spring.cloud.nacos.username` 等。

- [ ] **Step 7: 改 `application.yml` 的 `spring:` 段和启动类**

`application.yml` 的 `spring:` 段删掉 `application`、`profiles`、`cloud` 三块，改完 `spring:` 段是：

```yaml
spring:
  # Nacos 的 patra-common.yaml、patra-ingest.yaml 由启动器 PatraApplication 导入，这里不写
  config:
    import:
      # Local configuration files (error handling and message queue)
      - "classpath:ingest-error-config.yaml"
      - "classpath:ingest-rocketmq.yaml"
  # 地址随环境变，在 Nacos 的 patra-ingest.yaml；账号、密码在 patra-common.yaml（源文件在 patra-infra/nacos/dev/）
  datasource:
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  flyway:
    enabled: true
    locations: classpath:db/migration
```

`management.metrics.tags.application: ${spring.application.name}` 保留：服务名由启动器放进 default properties。

启动类改为（类 Javadoc 前面的功能列表和技术栈原样保留，只换最后的「启动配置」一句和 `main`）：

```java
package dev.linqibin.patra.ingest;

import dev.linqibin.patra.starter.nacos.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// （类 Javadoc 原样保留，把「启动配置: 未指定 profile 时默认使用 'dev' 环境配置。」一行改为：）
/// 配置从 Nacos 读（`patra-common.yaml`、`patra-ingest.yaml`），由启动器导入。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class PatraIngestApplication {

  /// 应用程序入口点：经启动器启动，服务名 `patra-ingest`。
  ///
  /// @param args 命令行参数
  static void main(String[] args) {
    PatraApplication.run("patra-ingest", PatraIngestApplication.class, args);
  }
}
```

`main` 保持原来的包级 `static void main`（Java 25 的启动协议），不改成 `public`。

- [ ] **Step 8: 看它通过**

Run: 同 Step 2。

Expected: `exit=0`，两条都通过。

- [ ] **Step 9: 加第三条测试（Nacos 接管的键不写在 jar 里）**

加 import `java.util.ArrayList;`，加测试：

```java
  @Test
  @DisplayName("Nacos 接管的键不在 jar 里重复写")
  void should_leave_nacos_owned_keys_to_nacos() throws IOException {
    List<String> names = new ArrayList<>(propertyNames("application.yml"));
    names.addAll(propertyNames("ingest-rocketmq.yaml"));

    assertThat(names)
        .doesNotContain(
            "xxl.job.admin.addresses",
            "rocketmq.name-server",
            "rocketmq.access-key",
            "rocketmq.secret-key");
  }
```

- [ ] **Step 10: 看它失败**

Run: 同 Step 2。

Expected: `exit=1`，`should_leave_nacos_owned_keys_to_nacos() FAILED`，断言信息列出这 4 个键。

- [ ] **Step 11: 删掉这 4 个键，搬入表达式编译的取值**

`application.yml`：

1. `patra:` 段开头加 `starter.expr`，改完 `patra:` 段开头是：

```yaml
patra:
  starter:
    # 表达式编译：两种运行方式都用严格模式和长度、参数数上限，其余参数用 starter 默认值
    expr:
      mode:
        strict: true
      compiler:
        max-query-length: 5000
        warn-param-count: 50
        max-param-count: 100
  ingest:
    exec:
```

2. `xxl:` 段改为（`admin` 整块删掉）：

```yaml
xxl:
  job:
    # 管理端地址随环境变，在 Nacos 公共配置 patra-common.yaml
    executor:
      # 回调地址：admin 按它回调本执行器。与 Nacos 注册地址同一个变量；容器里不设，自动探测 patra-net 上的 IP。
      ip: ${PATRA_ADVERTISE_IP:}
      # Log directory in centralized logs folder
      logpath: ${XXL_JOB_EXECUTOR_LOG_PATH:${PATRA_LOG_DIR:logs}/xxl-job/patra-ingest}
      logretentiondays: ${XXL_JOB_LOG_RETENTION_DAYS:30}
```

`ingest-rocketmq.yaml`：删掉第 6–7 行（`# RocketMQ NameServer 地址` 和 `name-server`）和第 23–25 行（`# 访问控制 (可选)` 及两个 key），在 `rocketmq:` 下第一行加注释，改完开头是：

```yaml
rocketmq:
  # NameServer 地址和 ACL 凭据随环境变，在 Nacos 的 patra-ingest.yaml

  producer:
    # 生产者组名称
    group: ingest-producer-group
```

`producer` 块和 `patra.ingest.outbox`、`mq` 段原样保留。

- [ ] **Step 12: 看它通过，跑模块检查**

Run: `$R/gradlew -p $R :patra-api:patra-ingest:patra-ingest-boot:check > $SCRATCH/t2-check.log 2>&1; echo "exit=$?"; tail -5 $SCRATCH/t2-check.log`

Expected: `exit=0`，`BUILD SUCCESSFUL`，`ApplicationConfigTest` 三条都通过，`IngestArchitectureTest` 通过。

- [ ] **Step 13: 变异检查**

两次，各自还原：

1. 在 `application.yml` 的 `spring:` 下加回 `profiles.active: dev`，跑 Step 2，Expected `exit=1`（第二条失败）。
2. 在 `ingest-rocketmq.yaml` 的 `rocketmq:` 下加回 `name-server: 127.0.0.1:9876`，跑 Step 2，Expected `exit=1`（第三条失败）。

还原后再跑 Step 2，Expected `exit=0`。结果记进 ledger。

- [ ] **Step 14: 提交**

```bash
$R/gradlew -p $R spotlessApply -q
git -C $R add patra-api/patra-ingest/patra-ingest-boot/
git -C $R diff --cached --stat
git -C $R commit -m "feat(config-center): ingest 改为从 Nacos 读配置 (PAP-77)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: catalog 迁移

**Files:**
- Create: `patra-api/patra-catalog/patra-catalog-boot/src/test/java/dev/linqibin/patra/catalog/ApplicationConfigTest.java`
- Delete: `patra-api/patra-catalog/patra-catalog-boot/src/main/resources/application-dev.yml`
- Delete: `patra-api/patra-catalog/patra-catalog-boot/src/main/resources/application-container.yml`
- Modify: `patra-api/patra-catalog/patra-catalog-boot/src/main/resources/application.yml`
- Modify: `patra-api/patra-catalog/patra-catalog-boot/src/main/java/dev/linqibin/patra/catalog/PatraCatalogApplication.java`
- Modify: `patra-api/patra-catalog/patra-catalog-boot/src/main/java/dev/linqibin/patra/catalog/config/LetPubConfiguration.java:91-92,98`（注释和告警文字）
- Modify: `patra-api/patra-catalog/patra-catalog-boot/src/testFixtures/resources/application-e2e-test.yml`

**Interfaces:**
- Consumes：`PatraApplication.run`；Task 1 的 `patra-catalog.yaml`；Task 2 的 `ApplicationConfigTest` 写法。
- Produces：无。

- [ ] **Step 1: 写第一条失败的测试**

```java
package dev.linqibin.patra.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// jar 里配置文件的约束（PAP-77）：随环境变的值在 Nacos，jar 里没有 profile 文件，
/// 服务名由启动器传入，Nacos 接入由 starter 注入，Nacos 接管的键不在 jar 里重复写。
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

Run: `$R/gradlew -p $R :patra-api:patra-catalog:patra-catalog-boot:test --tests '*ApplicationConfigTest' > $SCRATCH/t3.log 2>&1; echo "exit=$?"; grep -E "FAILED|Expecting" $SCRATCH/t3.log | head`

Expected: `exit=1`，`should_not_ship_profile_files() FAILED`。

- [ ] **Step 3: 删掉两个 profile 文件**

```bash
git -C $R rm -q patra-api/patra-catalog/patra-catalog-boot/src/main/resources/application-dev.yml \
  patra-api/patra-catalog/patra-catalog-boot/src/main/resources/application-container.yml
```

- [ ] **Step 4: 看它通过**

Run: 同 Step 2。Expected: `exit=0`（构建目录留旧文件时同 Task 2 Step 4 处理）。

- [ ] **Step 5: 加第二条测试**

import 与 Task 2 Step 5 相同：

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

- [ ] **Step 7: 改 `application.yml` 的 `spring:` 段和启动类**

`application.yml` 的 `spring:` 段：删掉 `application`、`profiles` 两块和整个「Nacos Service Discovery」小节（注释和 `cloud:` 块）；数据源注释改为指向 Nacos；从 container 文件搬来 `batch.job.enabled: false`。改完 `spring:` 段是：

```yaml
spring:
  # Configuration imports (order matters: later configs override earlier ones)
  # Nacos 的 patra-common.yaml、patra-catalog.yaml 由启动器 PatraApplication 导入，这里不写
  config:
    import:
      # Local error-handling configuration
      - "classpath:catalog-error-config.yaml"

  # Batch Job 不在启动时自动执行（catalog 有多个 Job，由 XXL-Job 手动触发）。
  # 缺此项时多 Job 场景启动期报「Job name must be specified in case of multiple jobs」。
  batch:
    job:
      enabled: false

  # Database Configuration
  datasource:
    driver-class-name: org.postgresql.Driver
    # 地址随环境变，在 Nacos 的 patra-catalog.yaml；账号、密码在 patra-common.yaml（源文件在 patra-infra/nacos/dev/）
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000    # 30 seconds
      idle-timeout: 600000          # 10 minutes
      max-lifetime: 1800000         # 30 minutes

  # Flyway Database Migration
  flyway:
    enabled: true
    locations: classpath:db/migration
```

启动类：`main` 改为经启动器启动，类 Javadoc 里「启动配置: 未指定 profile 时默认使用 'dev' 环境配置。」改为「配置从 Nacos 读（`patra-common.yaml`、`patra-catalog.yaml`），由启动器导入。」，其余 Javadoc 和 `@SpringBootApplication(scanBasePackages = "dev.linqibin", exclude = DataRedisRepositoriesAutoConfiguration.class)` 原样保留：

```java
import dev.linqibin.patra.starter.nacos.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
```

```java
  /// 应用程序入口点：经启动器启动，服务名 `patra-catalog`。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    PatraApplication.run("patra-catalog", PatraCatalogApplication.class, args);
  }
```

- [ ] **Step 8: 看它通过**

Run: 同 Step 2。Expected: `exit=0`，两条都通过。

- [ ] **Step 9: 加第三条测试**

```java
  @Test
  @DisplayName("Nacos 接管的键不在 jar 里重复写")
  void should_leave_nacos_owned_keys_to_nacos() throws IOException {
    assertThat(propertyNames("application.yml"))
        .doesNotContain(
            "xxl.job.admin.addresses",
            "patra.catalog.scopus.api-key",
            "linqibin.starter.rest-client.proxy.tunnel.enabled",
            "linqibin.starter.rest-client.proxy.tunnel.auth-key",
            "linqibin.starter.rest-client.proxy.tunnel.auth-pwd");
  }
```

- [ ] **Step 10: 看它失败**

Run: 同 Step 2。Expected: `exit=1`，`should_leave_nacos_owned_keys_to_nacos() FAILED`，断言信息列出这 5 个键。

- [ ] **Step 11: 删掉这 5 个键，搬入 5GB 上限，改 LetPub 的提示**

`application.yml`：

1. `patra.catalog` 下删掉整个 `scopus:` 块（含 4 行注释），在 `ror:` 块后面加一行注释：

```yaml
    # Scopus API key 随环境变，在 Nacos 的 patra-catalog.yaml（引用 ${SCOPUS_API_KEY:}，值放 ~/.patra/secrets/catalog.env）
```

2. `linqibin.starter` 改为（`tunnel` 只留主机和端口，新增 `object-storage.max-file-size`，`http-interface` 原样）：

```yaml
linqibin:
  starter:
    rest-client:
      proxy:
        tunnel:
          # 青果隧道代理：LetPub 等外部爬取经 Apache HttpClient 5 走它。
          # 开关和凭据随环境变，在 Nacos 的 patra-catalog.yaml（dev 关闭）；凭据引用 ${PROXY_AUTH_KEY:}、
          # ${PROXY_AUTH_PWD:}，值放 ~/.patra/secrets/catalog.env。打开后缺凭据，启动期 Assert 会报错。
          # 使用 IP 而非域名，避免 Clash fake-ip DNS 劫持
          # 域名 tun-afbngw.qg.net 对应 IP，可通过 nslookup tun-afbngw.qg.net 8.8.8.8 验证
          host: 112.49.20.234
          port: 15057
    object-storage:
      # MeSH 描述符文件约 400MB，starter 默认上限 100MB 不够
      max-file-size: 5368709120  # 5GB
    http-interface:
```

3. `xxl:` 段改为（`admin` 整块删掉）：

```yaml
xxl:
  job:
    # 管理端地址随环境变，在 Nacos 公共配置 patra-common.yaml
    executor:
      # 回调地址：admin 按它回调本执行器。与 Nacos 注册地址同一个变量；容器里不设，自动探测 patra-net 上的 IP。
      ip: ${PATRA_ADVERTISE_IP:}
      # Log directory in centralized logs folder
      logpath: ${XXL_JOB_EXECUTOR_LOG_PATH:${PATRA_LOG_DIR:logs}/xxl-job/patra-catalog}
      logretentiondays: ${XXL_JOB_LOG_RETENTION_DAYS:30}
```

`LetPubConfiguration.java`（只改文字，不改逻辑）：

- 第 91–92 行注释改为：

```java
      // 如需代理，在 Nacos 的 patra-catalog.yaml 打开 linqibin.starter.rest-client.proxy.tunnel.enabled，
      // 并在 ~/.patra/secrets/catalog.env 提供 PROXY_AUTH_KEY、PROXY_AUTH_PWD，
      // 使 TunnelProxyConfigurer Bean 生效（走 Apache HttpClient 5）。
```

- 第 98–99 行告警文字的「请改用 PROXY_TUNNEL_ENABLED=true 启用 」改为「请在 Nacos 的 patra-catalog.yaml 打开隧道代理，启用 」：

```java
                + "可能导致随机 'Tunnel failed, got: 503'。请在 Nacos 的 patra-catalog.yaml 打开隧道代理，启用 "
                + "TunnelProxyConfigurer（Apache HttpClient 5）。",
```

- [ ] **Step 12: 看它通过，跑模块检查和 e2e 测试**

Run: `$R/gradlew -p $R :patra-api:patra-catalog:patra-catalog-boot:check :patra-api:patra-catalog:patra-catalog-boot:e2eTest > $SCRATCH/t3-check.log 2>&1; echo "exit=$?"; tail -5 $SCRATCH/t3-check.log; grep -m3 -E "Could not resolve placeholder|FAILED" $SCRATCH/t3-check.log`

Expected: `ApplicationConfigTest` 三条通过；e2e 测试失败，报 `Could not resolve placeholder 'patra.catalog.scopus.api-key'`（Review Focus 2：e2e 测试启动完整上下文，不经过 Nacos，`ScopusConfiguration` 的 `@Value` 没有默认值）。

在 `src/testFixtures/resources/application-e2e-test.yml` 的 `xxl:` 段前面加：

```yaml
# 外部凭据（正式运行时在 Nacos 的 patra-catalog.yaml）：E2E 测试不连 Scopus，给空值让上下文能起来
patra:
  catalog:
    scopus:
      api-key: ""
```

重跑同一条命令，Expected: `exit=0`，`BUILD SUCCESSFUL`，`MeshDescriptorImportE2E`、`MeshScrImportE2E` 通过。如果失败原因不是这一项，按 superpowers:systematic-debugging 查，不改测试迁就；如果 e2e 测试第一次就通过了，说明上下文没加载 `ScopusConfiguration`，不加这段，在 ledger 记一条 Ruling。

- [ ] **Step 13: 变异检查**

两次，各自还原：

1. 在 `application.yml` 的 `spring:` 下加回 `application.name: patra-catalog`，跑 Step 2，Expected `exit=1`（第二条失败）。
2. 在 `linqibin.starter.rest-client.proxy.tunnel` 下加回 `enabled: false`，跑 Step 2，Expected `exit=1`（第三条失败）。

还原后再跑 Step 2，Expected `exit=0`。结果记进 ledger。

- [ ] **Step 14: 提交**

```bash
$R/gradlew -p $R spotlessApply -q
git -C $R add patra-api/patra-catalog/patra-catalog-boot/
git -C $R diff --cached --stat
git -C $R commit -m "feat(config-center): catalog 改为从 Nacos 读配置 (PAP-77)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 容器侧清理

**Files:**
- Modify: `patra-infra/docker/docker-compose.apps.yaml`（头部注释第 7–10 行；catalog、ingest 的 `env_file`）
- Delete: `patra-infra/docker/.env.ingest`、`patra-infra/docker/.env.catalog`、`patra-infra/docker/.env.dev`
- Modify: `patra-infra/docker/.env.common`（整个替换）
- Modify: `patra-infra/docker/.env`（第 15 行注释）
- Modify: `patra-infra/docker/.env.example`（第 12 行注释）
- Modify: `patra-infra/cd/detect-changes.sh`（第 57、61 行）
- Modify: `patra-infra/cd/detect-changes.test.sh`（第 68、76 行）

**Interfaces:**
- Consumes：Task 1 的 Nacos 文件；Task 2、3 后 jar 不再读 `SPRING_PROFILES_ACTIVE`、`*_DB_*`、`MINIO_*`、`REDIS_HOST`、`REDIS_PORT`、`PROXY_TUNNEL_ENABLED`。
- Produces：Task 6 部署用的 compose 和环境文件。

纯配置，不走 TDD；用 `docker compose config --quiet` 和路由测试检查。

- [ ] **Step 1: 改 compose**

catalog 的 `env_file` 删掉 `- .env.catalog`，ingest 的删掉 `- .env.ingest`，其余条目不动。

头部注释第 7–10 行改为：

```yaml
# env_file 按「本地变量、密钥」叠加（后者覆盖同名）：
#   .env.common   本地变量 PATRA_INFRA_HOST、PATRA_LOG_DIR。6 个后端服务的其余配置都在 Nacos（patra-infra/nacos/dev/）
```

第 11–17 行（密钥文件清单及其后两行）不动。

- [ ] **Step 2: 删环境文件，重写 `.env.common`**

```bash
git -C $R rm -q patra-infra/docker/.env.ingest patra-infra/docker/.env.catalog patra-infra/docker/.env.dev
```

`.env.common` 整个替换为：

```bash
# ============================================================
# Patra 应用容器 - 本地变量（随仓库提交）
# ============================================================
# 6 个后端服务的配置在 Nacos（patra-infra/nacos/dev/），容器和 IDE 读同一份，差异只在这几个本地变量
# （设计见 docs/patra/specs/2026-10-13-infra-access-unification-design.md）。
# 密钥（Redis 密码、网关私钥、Nacos 账号、外部数据源 API key 等）放在仓库外的 ~/.patra/secrets/，
# 由 compose 的 env_file 按绝对路径叠加加载，见 README「密钥」。

# 日志目录（镜像内 appuser 可写）
PATRA_LOG_DIR=/app/logs

# 基础设施所在主机：Nacos 地址，以及 Nacos 配置里的 PG、Redis、MinIO、RocketMQ namesrv、xxl-job admin 都跟随它。
# 容器经 compose 的 extra_hosts 把 linqibins-mac-mini 解析到 OrbStack 宿主机网关，不经过 tailscale；
# IDE 里设为 mini 的 tailscale IP。
PATRA_INFRA_HOST=linqibins-mac-mini
```

- [ ] **Step 3: 改两处注释**

`.env` 第 15 行「# .env.dev 中亦有同名变量副本，docker compose 默认只读 .env，所以以本文件为准。」改为「# docker compose 默认只读本目录的 .env。」。

`.env.example` 第 12 行「# 复制到 .env.dev 后改成本地实际值」改为「# 复制到 .env 后改成本地实际值」。

- [ ] **Step 4: 路由脚本和测试**

`detect-changes.sh`：

- 第 57 行注释「注意 apps 栈与 service.Dockerfile 已在上面判全量；应用容器的 .env.common / .env.<svc> 未列入，」改为「注意 apps 栈与 service.Dockerfile 已在上面判全量；应用容器的 .env.common 未列入，」（下一行「仍走下方……」不动）。
- 第 61 行 `patra-infra/docker/.env|patra-infra/docker/.env.dev|patra-infra/docker/*.example)` 改为 `patra-infra/docker/.env|patra-infra/docker/*.example)`。

`detect-changes.test.sh`：

- 第 68 行改为 `assert_field "patra-infra/docker/.env" '.backend_units' '[]' "基建栈 .env backend 空"`。
- 删掉第 76 行（`.env.catalog` 那条）。

Run: `bash $R/patra-infra/cd/detect-changes.test.sh 2>&1 | tail -1`

Expected: `通过 59 / 失败 0`。

- [ ] **Step 5: 校验 compose**

Run: `docker compose -f $R/patra-infra/docker/docker-compose.apps.yaml config --quiet; echo "exit=$?"`

Expected: `exit=0`（本机 `~/.patra/secrets/` 有 `nacos.env`、`redis.env`、`gateway.env`）。不要去掉 `--quiet`：完整输出会把密钥打到终端。本机没有 docker 命令时在 ledger 记一条 Ruling，留给 Task 6 的部署兜底。

- [ ] **Step 6: 没有遗留引用（Review Focus 4）**

```bash
grep -rnI --exclude-dir=node_modules --exclude-dir=build --exclude-dir=.git --exclude-dir=worktrees --exclude-dir=.superpowers \
  -e '\.env\.dev' -e '\.env\.ingest' -e '\.env\.catalog' -e 'SPRING_PROFILES_ACTIVE' -e 'XXL_JOB_ADMIN_ADDRESSES' \
  -e 'ROCKETMQ_NAMESRV' -e 'PROXY_TUNNEL_ENABLED' -e 'INGEST_DB_' -e 'CATALOG_DB_' -e 'BATCH_DB_' \
  -e 'MINIO_BUCKET' -e 'MINIO_ENDPOINT' -e 'REDIS_HOST' -e 'OBJECT_STORAGE_PROVIDER' -e 'MAX_FILE_SIZE' \
  $R | grep -vE 'docs/patra/(specs|plans|release-specs|runbooks)/|docs/patra/.*\.html'
```

Expected: 只剩 Task 5 要改的文档行（根 `CLAUDE.md`、`AGENTS.md`、`patra-infra/CLAUDE.md`、`patra-infra/docker/README.md`、`patra-api/README.md`），以及 `NacosConfigFilesTest.java` 里作为测试数据的 `${INGEST_DB_PASSWORD}`。没有代码、compose、脚本、workflow 的命中。

- [ ] **Step 7: 提交**

```bash
git -C $R add patra-infra/docker/ patra-infra/cd/detect-changes.sh patra-infra/cd/detect-changes.test.sh
git -C $R diff --cached --stat
git -C $R commit -m "chore(config-center): 删除已搬进 Nacos 的环境文件和不生效的 .env.dev (PAP-77)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 文档

**Files:**
- Modify: `CLAUDE.md`（第 38 行）、`AGENTS.md`（第 38、92 行）
- Modify: `patra-infra/CLAUDE.md`（第 45、51、61、71 行）
- Modify: `patra-infra/docker/README.md`（第 102、159、165、283、313 行）
- Modify: `patra-infra/docker/secrets/nacos.env.example`（第 3–5 行）、`redis.env.example`（第 3–4 行）
- Modify: `patra-infra/docs/mac-mini-connectivity.md`（第 88 行）
- Modify: `patra-api/README.md`（「环境配置文件」一节，第 146–150 行）
- Modify: `patra-api/patra-ingest/README.md`（第 216 行、「2. 运行服务」）
- Modify: `patra-api/patra-catalog/README.md`（「⚙️ 配置说明」开头）
- Modify: `.claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md`（「环境配置」「配置优先级」「Nacos 服务发现配置」三节）

**Interfaces:**
- Consumes：Task 1–4 的结果。
- Produces：无。

纯文档，不走 TDD。行号是写计划时（`e114454a2`）的位置，按内容定位。

- [ ] **Step 1: 根 `CLAUDE.md`、`AGENTS.md`**

两个文件第 38 行的「`patra-infra/docker/.env`、`.env.dev` 含 dev 默认凭据」改为「`patra-infra/docker/.env` 含 dev 默认凭据」，其余不动。`AGENTS.md` 第 92 行「`patra-infra/docker/.env`、`.env.dev` 中的 dev 默认凭据」改为「`patra-infra/docker/.env` 中的 dev 默认凭据」。

- [ ] **Step 2: `patra-infra/CLAUDE.md`**

第 45 行「registry、object-storage、identity、gateway 由 Nacos starter 注入（`PATRA_NACOS_ADDR` 可整体覆盖），ingest、catalog 迁移前仍写在 yml 里（`${NACOS_HOST:${PATRA_INFRA_HOST:127.0.0.1}}`）；」改为「6 个后端服务都由 Nacos starter 注入（`PATRA_NACOS_ADDR` 可整体覆盖）；」。

第 51 行开头「**`.env` / `.env.dev` 含 dev 默认凭据且随仓库提交。**」改为「**`.env` 含 dev 默认凭据且随仓库提交。**」；同一行「Mac mini 靠 `git pull` 同步这些配置（PG/MinIO/MySQL 密码等）」改为「Mac mini 靠 `git pull` 同步这些配置（mysql-ops 的密码等）」。

第 61 行「基建栈 `.env` / `.env.dev` / `*.example`」改为「基建栈 `.env` / `*.example`」；「应用容器的 `.env.common` / `.env.<svc>` 仍判全量」改为「应用容器的 `.env.common` 仍判全量」。

第 71 行整条改为：

```markdown
- **环境文件，密钥在仓库外**：`env_file` 顺序叠加 `.env.common`（本地变量 `PATRA_INFRA_HOST`、`PATRA_LOG_DIR`；6 个后端服务的其余配置都在 Nacos，源文件在 `nacos/dev/`）→ `${HOME}/.patra/secrets/` 下的密钥文件（`redis.env` 给 catalog/identity/gateway、`gateway.env` 给 gateway、`nacos.env` 给 6 个后端服务，三者必需；`<svc>.env` 可选；后者覆盖同名）。**Redis 密码、网关私钥、外部数据源 API key（Scopus / 青果 proxy / RocketMQ ACL 等）一律只进 `~/.patra/secrets/`**，committed 文件只放内网 dev 默认值和公钥，Nacos 配置里只写 `${变量}`。必需文件缺一个，compose 对整个 apps 项目的命令都会失败（portal 也部署不了）。
```

- [ ] **Step 3: `patra-infra/docker/README.md`**

第 102 行「Nacos 地址缺省跟随 `PATRA_INFRA_HOST`：registry、object-storage、identity、gateway 由 Nacos starter 注入，ingest、catalog 迁移前仍写在 yml 里。账号 `patra-app` 来自 `~/.patra/secrets/nacos.env`：前 4 个服务由 starter 读取，ingest、catalog 由 dev profile 导入；缺了启动失败。」改为：

```markdown
Nacos 地址缺省跟随 `PATRA_INFRA_HOST`，由 Nacos starter 注入。账号 `patra-app` 来自 `~/.patra/secrets/nacos.env`，由 starter 读取，缺了启动失败。
```

（同一段落后面的「Nacos 控制台同样直连……」不动。）

第 159 行密钥表 `<服务>.env` 一行的「对应服务的容器」改为「对应服务的容器和本机进程」，内容列改为「该服务的外部数据源密钥，例如 catalog 的 `SCOPUS_API_KEY`、`PROXY_AUTH_KEY`、`PROXY_AUTH_PWD`，ingest 的 `ROCKETMQ_AK`、`ROCKETMQ_SK`」。

第 165 行改为：

```markdown
- 本机进程：Nacos starter 把 `~/.patra/secrets/` 下的全部 `*.env` 读进来，IDEA 和 shell 都不用设这些变量。
```

第 283 行改为：

```markdown
Nacos 的地址、账号和服务发现设置由 `patra-spring-boot-starter-nacos` 注入，服务的配置里不写。
```

第 313 行「凭据见 `.env.dev` 的 `MYSQL_OPS_*`」改为「凭据见 `.env` 的 `MYSQL_OPS_*`」。

- [ ] **Step 4: 两份密钥模板**

`secrets/nacos.env.example` 第 3–5 行改为：

```
# 谁读：6 个后端服务的容器（compose env_file，必需）；本机起的进程（Nacos starter 读 ~/.patra/secrets/）；
#       scripts/nacos-init.sh 按它建账号，nacos-verify.sh 用它验权限。
```

`secrets/redis.env.example` 第 3–4 行改为：

```
# 谁读：core 栈的 redis（启动时设密码）；catalog、identity、gateway 的容器（compose env_file）
#       和这三个服务在本机起的进程（Nacos starter 读 ~/.patra/secrets/）。
```

- [ ] **Step 5: `patra-infra/docs/mac-mini-connectivity.md` 第 88 行**

改为：

```markdown
  - 应对（2026-10-14 起取消）：曾在 catalog / ingest 的 `application-dev.yml` 把 `linqibin.starter.http-interface.connect-timeout` 从全局 2s 放宽到 10s。PAP-77 起两种运行方式读同一份配置，都用 jar 里的 2s：本机首个请求撞上冷启动会超时失败，重试即可；容器走 Docker 内网，不受影响。
```

- [ ] **Step 6: `patra-api/README.md`「环境配置文件」一节**

第 146–150 行（5 条列表）整体替换为：

```markdown
- **6 个后端服务**：配置从 Nacos 读（`patra-common.yaml` 加 `{服务名}.yaml`，源文件在 `patra-infra/nacos/dev/`），启动类用 `PatraApplication.run(服务名, …)`。jar 里只有 `application.yml`，放端口、连接池、超时等随代码变的默认值；库地址、Redis、MinIO、xxl-job、公钥、密钥引用等随环境变的值在 Nacos。IDE 与容器读同一份配置，只差 `PATRA_INFRA_HOST`、`PATRA_ADVERTISE_IP`、`PATRA_LOG_DIR` 三个本地变量。设计见 `docs/patra/specs/2026-10-13-config-migration-batch1-design.md`、`docs/patra/specs/2026-10-14-config-migration-batch2-design.md`。
- 服务注册中心 (Nacos) 在端口 8848 运行，提供服务发现、配置和健康检查功能。
- 密钥（Redis 密码、网关私钥、外部数据源 API key）不进仓库：Nacos 配置里只写 `${变量}`，值放在 `~/.patra/secrets/` 下的 `.env` 文件里，本机由 Nacos starter 读，容器经 compose 的 `env_file` 注入。
```

- [ ] **Step 7: 两个服务的 README**

`patra-api/patra-ingest/README.md` 第 216 行「- ✅ **生产环境启用 STRICT 模式**(`expr.strict=true`),早期捕获配置错误」改为：

```markdown
- ✅ **启用 STRICT 模式**(`patra.starter.expr.mode.strict=true`，jar 默认，IDE 与容器一致),早期捕获配置错误
```

「### 2. 运行服务」的代码块后面（「**默认端口**」那一行之前）加一段：

```markdown
配置从 Nacos 读（`patra-common.yaml`、`patra-ingest.yaml`，源文件在 `patra-infra/nacos/dev/`）。本机运行只需要设 `PATRA_INFRA_HOST`、`PATRA_ADVERTISE_IP`，并在 `~/.patra/secrets/` 放好 `nacos.env`；RocketMQ 启用 ACL 时，在 `~/.patra/secrets/ingest.env` 放 `ROCKETMQ_AK`、`ROCKETMQ_SK`。
```

`patra-api/patra-catalog/README.md`「## ⚙️ 配置说明」标题下第一行加一段：

```markdown
配置从 Nacos 读（`patra-common.yaml`、`patra-catalog.yaml`，源文件在 `patra-infra/nacos/dev/`）：库地址、批处理库、Redis 密码引用、MinIO bucket、青果代理开关（dev 关闭）和外部凭据引用都在那里。外部凭据（`SCOPUS_API_KEY`、`PROXY_AUTH_KEY`、`PROXY_AUTH_PWD`）的值放在 `~/.patra/secrets/catalog.env`，没配时服务照常启动，用到对应数据源时才失败。下面各数据源的 URL 等随代码变的默认值在 jar 的 `application.yml`。
```

- [ ] **Step 8: 配置技能文档**

`.claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md` 第 3–12 行（「## 环境配置」到代码块结束）替换为：

````markdown
## 环境配置

6 个后端服务的配置分两处，不用 Spring profile：

```
patra-{service}-boot/src/main/resources/
└── application.yml            # 随代码变的默认值：端口、连接池、超时、路由等

patra-infra/nacos/dev/          # 随环境变的值，推送到 Nacos（git 为准）
├── patra-common.yaml           # 各服务共用：数据库账号、Redis、MinIO、xxl-job、身份断言公钥
└── {服务名}.yaml               # 本服务：库地址、bucket、密钥引用等
```

启动类用 `PatraApplication.run("patra-xxx", XxxApplication.class, args)`，由启动器导入两份 Nacos 配置并检查签到。密钥在 Nacos 里只写 `${变量}`，值放在 `~/.patra/secrets/*.env`。设计见 `docs/patra/specs/2026-10-13-config-migration-batch1-design.md`。
````

第 26–31 行「## 配置优先级（由高到低）」的列表改为：

```markdown
1. 命令行参数 `--spring.datasource.url=...`
2. 环境变量 `SPRING_DATASOURCE_URL`
3. Nacos 配置（`{服务名}.yaml` 高于 `patra-common.yaml`）
4. `application.yml`

同一个键不在 jar 和 Nacos 两处写：每个 boot 模块的 `ApplicationConfigTest` 检查。
```

第 33–49 行「## Nacos 服务发现配置」整节替换为：

```markdown
## Nacos 接入

Nacos 的地址、账号、服务发现设置由 `patra-spring-boot-starter-nacos` 注入，服务的配置里不写 `spring.cloud.nacos`。Nacos 同时用于服务发现和配置管理。
```

第 80 行「- 生产环境敏感信息通过环境变量注入（`${DB_PASSWORD}`）」改为「- 密钥在 Nacos 配置里写成 `${变量}` 引用，值放在 `~/.patra/secrets/`」。

- [ ] **Step 9: 核对**

```bash
grep -rnI -e 'application-dev' -e 'application-container' -e '\.env\.dev' -e '\.env\.ingest' -e '\.env\.catalog' \
  -e '迁移前' -e 'dev profile' -e 'SPRING_PROFILES_ACTIVE' \
  $R/CLAUDE.md $R/AGENTS.md $R/patra-infra/CLAUDE.md $R/patra-infra/docker/README.md $R/patra-infra/docker/secrets \
  $R/patra-infra/docs/mac-mini-connectivity.md $R/patra-api/README.md $R/patra-api/patra-ingest/README.md \
  $R/patra-api/patra-catalog/README.md $R/.claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md
```

Expected: 只剩 `mac-mini-connectivity.md` 第 88 行「曾在 catalog / ingest 的 `application-dev.yml`」这一处历史说明。

- [ ] **Step 10: 提交**

```bash
git -C $R add CLAUDE.md AGENTS.md patra-infra/CLAUDE.md patra-infra/docker/README.md patra-infra/docker/secrets/ \
  patra-infra/docs/mac-mini-connectivity.md patra-api/README.md patra-api/patra-ingest/README.md \
  patra-api/patra-catalog/README.md .claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md
git -C $R diff --cached --stat
git -C $R commit -m "docs(config-center): 同步 6 个服务都从 Nacos 读配置后的说明 (PAP-77)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: 全量验证与 mini 验收

**Files:** 正常情况下无代码改动。

**Interfaces:**
- Consumes：Task 1–5 的全部产物。
- Produces：验收记录，写进 ledger 和最终消息。

- [ ] **Step 1: 本机全量**

Run: `$R/gradlew -p $R check integrationTest e2eTest > $SCRATCH/t6-full.log 2>&1; echo "exit=$?"; tail -5 $SCRATCH/t6-full.log`

Expected: `exit=0`，`BUILD SUCCESSFUL`。失败时按 superpowers:systematic-debugging 查原因，不改测试迁就。负载高（`uptime` 的 load average 大于 40）导致的超时类失败，等负载降下来单独重跑失败的模块。

- [ ] **Step 2: 停下，征得用户同意后再动 mini 的 Nacos**

向用户说明 Step 3–5 会做什么：

- 把本分支 `patra-infra/nacos/dev/` 推到 mini 的 Nacos（预计 3 份更新：`patra-common.yaml`、`patra-ingest.yaml`、`patra-catalog.yaml`；4 份未变化）。mini 上旧的 ingest、catalog 镜像不读 Nacos 配置；另外 4 个服务读到公共配置新增的 MinIO、xxl-job 键，不绑定，不受影响。
- 本机逐个启动 ingest、catalog，它们会注册到 mini 的 Nacos 和 xxl-job 调度中心，期间 mini 的网关、调度中心可能把少量请求转到本机实例；每个服务验完就停。

只有用户明确同意后才执行 Step 3。

- [ ] **Step 3: 推送 Nacos 配置（Review Focus 3）**

```bash
NACOS_ADDR=http://100.103.73.27:8848 bash $R/patra-infra/scripts/nacos-push.sh --check; echo "exit=$?"
```

Expected: 3 行「更新」，4 份未变化，没有「删除」，`exit=2`。出现「删除」或别的文件就停下，把清单给用户看。

```bash
NACOS_ADDR=http://100.103.73.27:8848 bash $R/patra-infra/scripts/nacos-push.sh; echo "exit=$?"
```

Expected: `新增 0，更新 3，删除 0，未变化 4`，`exit=0`。人工核对输出里没有密码和令牌。

推送后看 mini 上 6 个容器：

```bash
ssh -o ControlMaster=no -o ControlPath=none mini 'export PATH=/opt/homebrew/bin:/usr/local/bin:$PATH; docker ps --filter name=patra- --format "{{.Names}} {{.Status}}"'
```

Expected: 6 个后端容器都是 `(healthy)`，`Up` 时间没有因为推送而重置。

- [ ] **Step 4: 准备本机启动环境**

本机 JVM 会继承 macOS 系统代理，每条启动命令前都带：

```bash
NP='localhost|127.*|100.*|192.168.*|172.*|10.*'
export JAVA_TOOL_OPTIONS="-Dhttp.nonProxyHosts=$NP -Dhttps.nonProxyHosts=$NP -Dsocks.nonProxyHosts=$NP"
export PATRA_INFRA_HOST=100.103.73.27 PATRA_ADVERTISE_IP=100.70.109.37
```

- [ ] **Step 5: 本机逐个启动 ingest、catalog（Review Focus 5）**

后台运行（Bash `run_in_background`），日志写到 scratchpad。ingest：

```bash
NP='localhost|127.*|100.*|192.168.*|172.*|10.*'
JAVA_TOOL_OPTIONS="-Dhttp.nonProxyHosts=$NP -Dhttps.nonProxyHosts=$NP -Dsocks.nonProxyHosts=$NP" \
PATRA_INFRA_HOST=100.103.73.27 PATRA_ADVERTISE_IP=100.70.109.37 \
$R/gradlew -p $R :patra-api:patra-ingest:patra-ingest-boot:bootRun \
  --args='--logging.level.org.springframework.boot.context.config=TRACE' > $SCRATCH/run-ingest.log 2>&1
```

catalog 把模块路径换成 `:patra-api:patra-catalog:patra-catalog-boot`，日志名换成 `run-catalog.log`。

每个服务等到日志出现 `Started Patra…Application` 或报错退出，然后检查：

```bash
grep -E "Adding imported property source|Started Patra|register finished|HikariPool-[0-9]+ - Start completed|xxl-job" $SCRATCH/run-ingest.log | head -20
curl -s http://127.0.0.1:6100/actuator/health
```

Expected（两个服务都要满足）：

- 导入日志里有 `patra-common.yaml` 和本服务的 `{服务名}.yaml`；ingest 另有 `ingest-error-config.yaml`、`ingest-rocketmq.yaml`，catalog 另有 `catalog-error-config.yaml`。
- 有 `Started Patra…Application`，说明签到检查和变量检查都通过。
- 有本服务注册到 Nacos 的日志（`register finished`，地址是 `100.70.109.37`）。
- 有 `HikariPool-1 - Start completed`；catalog 另有批处理库的连接池启动日志。
- `actuator/health` 返回 `{"status":"UP"}`（ingest 6100，catalog 6300）。catalog 的 health 含 Redis，UP 即 Redis 已连上。
- 执行器注册到 mini 的调度中心（只读查询）：

```bash
ssh -o ControlMaster=no -o ControlPath=none mini 'export PATH=/opt/homebrew/bin:/usr/local/bin:$PATH; docker exec patra-mysql-ops sh -c '"'"'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT registry_key,registry_value,update_time FROM xxl_job.xxl_job_registry" 2>/dev/null'"'"''
```

Expected：有一行本服务、地址 `http://100.70.109.37:6101/`（catalog 为 `6301`）、更新时间在本次启动之后。

ingest 启动失败、原因是连不上 RocketMQ（mini 上的旧 ingest 容器同样连不上，见 memory「10/13 方向反思」）时：停下来查 `rocketmq.name-server` 实际取到的值（TRACE 日志），值是 `100.103.73.27:9876` 就说明配置迁移正确，在 ledger 记一条 Ruling 写明「RocketMQ 不可达与本次无关」，把报错原样放进最终消息。

验完停掉这个服务（TaskStop 停后台任务），再用 `lsof -nP -iTCP:<端口> -sTCP:LISTEN` 确认端口已释放；没释放就 `kill` 那个 PID。再启动下一个。

- [ ] **Step 6: 停下，征得用户同意后再推送分支、部署到 mini**

向用户说明：

- 需要把 `feat/v0.9-config-center-api` 推到 GitHub（ship-gate 钩子拦截时请用户自己推送）；
- 然后在本分支手动触发 `cd.yml`（`service` 留空）：推送 Nacos 配置（应全部未变化）→ 构建并部署 6 个服务；
- 已知代价（spec 第 8 节）：部署后 ingest、catalog 回滚不到迁移前的旧镜像；mini 上会运行未合入 main 的代码，main 的下一次部署会覆盖回去。

只有用户明确同意后才执行 Step 7。

- [ ] **Step 7: 触发 CD**

分支推送后：

```bash
gh workflow run cd.yml --repo linqibin0826/patra --ref feat/v0.9-config-center-api
gh run list --repo linqibin0826/patra --workflow cd.yml --branch feat/v0.9-config-center-api --limit 1
```

用 `gh run watch <run-id> --repo linqibin0826/patra --exit-status`（后台运行）等它结束。

Expected：`detect-changes`、`nacos-push`、`build-deploy` 三个 job 都成功；`nacos-push` 日志是「未变化 7」；`build-deploy` 部署了 6 个服务，`deploy.sh` 没有触发回滚。

- [ ] **Step 8: 容器健康、快照日志、占位符**

```bash
ssh -o ControlMaster=no -o ControlPath=none mini 'export PATH=/opt/homebrew/bin:/usr/local/bin:$PATH; docker ps --filter name=patra- --format "{{.Names}} {{.Status}} {{.Image}}"; for c in patra-ingest patra-catalog; do echo "$c snapshot=$(docker logs $c 2>&1 | grep -ci snapshot) placeholder=$(docker logs $c 2>&1 | grep -c "Could not resolve placeholder") error=$(docker logs $c 2>&1 | grep -c " ERROR ")"; done'
```

Expected：6 个后端容器都是 `(healthy)`，镜像 tag 是本分支的提交 SHA；ingest、catalog 的 `placeholder=0`，`snapshot` 只有一两行，隔 2 分钟再数一次不增长；`error` 计数不为 0 时看具体行，判断是否与本次有关（RocketMQ 不可达按 Step 5 的规则处理）。

- [ ] **Step 9: 执行器注册（spec 第 7 节第 4 步 ingest 部分）**

同 Step 5 的只读查询。

Expected：`patra-ingest`、`patra-catalog` 各一行，地址是新容器在 `patra-net` 上的 IP（`192.168.97.x`），更新时间在部署之后。本机启动时留下的 `100.70.109.37` 那行已过期消失（调度中心 90 秒内清掉没有心跳的执行器）。

- [ ] **Step 10: 停下，征得用户同意后再触发 catalog 的导入 Job**

向用户说明：经 xxl-job 管理端 API 手动触发 `meshQualifierImportJob`（jobId 5，文件最小），会重新导入 MeSH 限定词到 mini 的 `patra_catalog`；下载 NLM 文件失败时改触发 `rorOrganizationImportJob`（jobId 9）。只有用户明确同意后才执行 Step 11。

- [ ] **Step 11: 触发并核对 Job**

```bash
BASE=http://linqibins-mac-mini:7070/xxl-job-admin; CK=$SCRATCH/xxl_cookie.txt
curl -s --noproxy '*' -c $CK "$BASE/auth/doLogin" --data-urlencode userName=admin --data-urlencode password=123456 -o /dev/null -w 'login %{http_code}\n'
curl -s --noproxy '*' -b $CK "$BASE/jobinfo/trigger" --data-urlencode id=5 --data-urlencode executorParam= --data-urlencode addressList=
```

Expected：`login 200`，触发返回 `"code":200`。

等 Job 结束（每 30 秒查一次，最多 15 分钟）：

```bash
ssh -o ControlMaster=no -o ControlPath=none mini 'export PATH=/opt/homebrew/bin:/usr/local/bin:$PATH; docker exec patra-mysql-ops sh -c '"'"'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT id,job_id,executor_address,trigger_code,handle_code,LEFT(REPLACE(handle_msg,CHAR(10),\" \"),300) FROM xxl_job.xxl_job_log ORDER BY id DESC LIMIT 3" 2>/dev/null'"'"''
```

Expected：最新一行 `job_id=5`，`executor_address` 是新 catalog 容器的地址，`trigger_code=200`，`handle_code=200`。

`handle_code` 是 500 时看 `handle_msg` 和 `docker logs patra-catalog`：

- 下载 NLM 失败（SSL 握手失败、连接重置、超时）：与配置无关，征得同意后改触发 jobId 9，同样核对。两个都因下载网络失败时，在 ledger 记一条 Ruling，把报错原样放进最终消息。
- 数据库、批处理库、MinIO、Redis 相关的报错：是本次的问题，按 superpowers:systematic-debugging 查。

- [ ] **Step 12: 网关路由**

```bash
for s in patra-registry patra-identity patra-catalog patra-ingest; do
  printf '%s %s\n' "$s" "$(curl -s -o /dev/null -w '%{http_code}' http://100.103.73.27:9528/$s/v3/api-docs)"
done
```

Expected：identity、catalog 是 `200`；registry、ingest 是 `404`，且来自服务本身（PAP-76 验收时已确认这两个服务没有 openapi 依赖，路由本身是通的）。

- [ ] **Step 13: 两种运行方式一致（只看变量名）**

```bash
ssh -o ControlMaster=no -o ControlPath=none mini 'export PATH=/opt/homebrew/bin:/usr/local/bin:$PATH; for c in patra-registry patra-object-storage patra-catalog patra-ingest patra-identity patra-gateway; do echo "== $c"; docker inspect -f "{{range .Config.Env}}{{println .}}{{end}}" $c | cut -d= -f1 | sort; done'
```

不要去掉 `cut`：完整输出会打印密码和私钥。

Expected：每个容器的变量名只有这几类：

- 本地变量：`PATRA_INFRA_HOST`、`PATRA_LOG_DIR`；
- Nacos 账号：`NACOS_USERNAME`、`NACOS_PASSWORD`；
- 密钥：catalog、identity、gateway 有 `REDIS_PASSWORD`，gateway 有 `PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY`；
- 镜像自带：`PATH`、`JAVA_HOME`、`JAVA_VERSION`、`LANG`、`LANGUAGE`、`LC_ALL` 等。

不应出现 `SPRING_PROFILES_ACTIVE`、`REDIS_HOST`、`REDIS_PORT`、`MINIO_*`、`*_DB_*`、`PROXY_TUNNEL_ENABLED`。

- [ ] **Step 14: D2 核对**

```bash
git -C $R ls-files | grep -E 'application-(dev|container)\.ya?ml$|\.env\.dev$'
grep -rnE '^\s*[A-Za-z0-9_.-]*(password|secret|token|credentials?|private-key|api-key|access-key|secret-key|auth-pwd|auth-key):' $R/patra-infra/nacos/dev/ | grep -v '\${'
```

Expected：两条命令都没有输出（第一条没有 profile 文件和 `.env.dev`；第二条 Nacos 里没有写死值的敏感键）。

- [ ] **Step 15: 收尾**

- 走 executing-plans / subagent-driven-development 的最终审查（换一个最强模型的 reviewer 审 `e114454a2..HEAD`），修完再进入下一步。
- 把验收结论（推送结果、本机启动、Job 结果、变量名清单、D2）写进 ledger 和最终消息。
- 问用户：是否把 PAP-77 置为 `Review`。
- 合入 main 之后 main 的 CD 会再部署一次，那次 `nacos-push` 应是「未变化 7」——作为待合入后核对的事项写进最终消息。
