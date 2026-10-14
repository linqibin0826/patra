# Nacos 配置接入 starter 实施计划（PAP-74）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `patra-spring-boot-starter-nacos`：统一注入 Nacos 连接和服务发现设置，经启动器 `PatraApplication` 从配置中心读取 `patra-common.yaml` 与 `{服务名}.yaml`，拿不到时启动失败；同时撤销 `dev-secrets` namespace 并同步文档与 Linear。

**Architecture:** starter 里只有两个 `EnvironmentPostProcessor` 和一个启动器，没有自动配置类。`NacosDefaultsPostProcessor` 在 Spring Boot 加载配置之前补默认值（并入 default properties）、读本机密钥文件、在没有 `nacos:` 导入时关掉配置中心；`PatraApplication` 把服务名、两条导入、namespace、`preference=remote`、签到开关放进 default properties；`ConfigSourceCheckPostProcessor` 在配置加载完之后检查两份 Nacos 配置的签到行。配置加载本身全部交给 SCA 的 `nacos:` 导入。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Cloud 2025.1.3、Spring Cloud Alibaba 2025.1.0.0（nacos-client 3.1.1）、Testcontainers 1.21.4、Nacos 服务端 3.2.4、Bash 3.2（mini 脚本）。

**Spec:** `docs/patra/specs/2026-10-13-nacos-config-starter-design.md`

## 与 spec 的差异（写计划时核对源码后改定，Task 3、Task 8 同步回 spec）

1. **签到检查改用 `EnvironmentPostProcessor`，不用带 `@Validated` 的配置类**。spec 5.5 写的是 `@ConfigurationProperties` record 加 `@NotBlank`。改的理由：
   - 检查发生在配置刚加载完、任何 Bean 创建之前。Nacos 没到时，不会先冒出数据源、Redis 等不相关的报错。
   - 报错里能带上实际的服务名。Bean Validation 的消息插值拿不到 `spring.application.name`。
   - 不再依赖 Bean Validation 的实现，spec 5.1 里的 `spring-boot-starter-validation` 依赖随之去掉。
   - 配置刷新时，spring-cloud-context 会重跑所有 `EnvironmentPostProcessor`。少了签到行的那次变更会被拒绝，应用继续用旧配置。
2. **starter 没有自动配置类**：签到检查不再是 Bean，starter 里只剩两个 `EnvironmentPostProcessor` 和启动器。
3. **集成测试拆成 4 个类，每个类一个 JVM**（`integrationTest` 设 `forkEvery = 1`）。SCA 的 `NacosConfigManager` 在进程里是静态单例：连哪个地址、用哪个账号，由进程里第一个启动的应用决定。nacos-client 的快照目录也在类加载时就固定了。地址或账号不同的用例因此不能共用一个 JVM。
4. **`nacos-verify.sh` 第 4 项改为「有 `dev`，没有 `dev-secrets`」**，切换后能直接看出旧 namespace 是否删干净。

## Global Constraints

- 模块 `patra-starters/patra-spring-boot-starter-nacos`，插件 `linqibin.module-patra` + `linqibin.spring-boot-starter`，包名 `dev.linqibin.patra.starter.nacos`。
- 依赖（`api`）：`spring-cloud-starter-alibaba-nacos-config`、`spring-cloud-starter-alibaba-nacos-discovery`；不引入 bootstrap。
- Nacos 地址 `${PATRA_NACOS_ADDR:${PATRA_INFRA_HOST:127.0.0.1}:8848}`；账号 `${NACOS_USERNAME}`、`${NACOS_PASSWORD}`，不给默认值。
- 服务发现：namespace 不设（public）；`service` 取 `${spring.application.name}`；`ip` 取 `${PATRA_ADVERTISE_IP:}`；`fail-fast: false`。
- 环境里没有 `nacos:` 导入时设 `spring.cloud.nacos.config.enabled=false`。
- 启动器的 default properties 共 5 项：
  - `spring.application.name=<服务名>`；
  - `spring.config.import=nacos:patra-common.yaml,nacos:<服务名>.yaml`（按此顺序，不标 `optional:`）；
  - `spring.cloud.nacos.config.namespace=dev`；
  - `spring.cloud.nacos.config.preference=remote`；
  - `patra.config-source.required=true`。
- 签到字段：`patra.config-source.common: patra-common.yaml`、`patra.config-source.service: <服务名>.yaml`，只写在 Nacos 的文件里。
- 报错文案（原文，不得改动）：
  - 「没拿到 Nacos 配置 patra-common.yaml：Nacos 不可达且本地没有快照，或该文件不存在」
  - 「没拿到本服务的 Nacos 配置 <服务名>.yaml：Nacos 不可达且本地没有快照，或该文件不存在」
- 本机密钥：读 `${user.home}/.patra/secrets/*.env`，按文件名排序，放在 `systemEnvironment` 之后；目录不存在时跳过。
- 代码规范：
  - Google Java Style；所有方法写 `///` Javadoc（Markdown）；不用全类名；每行不超过 100 列。
  - gjf 会把长 `///` 行折断，FQN 链接要独占一行。
  - 提交前跑 `spotlessApply`。
- 测试：
  - 单元测试叫 `{类}Test`，集成测试叫 `{场景}IT`，方法名用 snake_case，`@DisplayName` 写中文；技术栈是 JUnit 5 + AssertJ。
  - TDD 一次只写一条测试：写一条 → 看它失败 → 写最少的实现 → 看它通过 → 再写下一条。禁止一次写多条测试再实现。
- 提交：
  - subject 用中文动词开头，格式 `<type>(config-center): … (PAP-74)`，结尾带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
  - 只提交本计划涉及的路径，提交前看 `git diff --cached --stat`。
  - 不推送。
- 安全：仓库是公开的。不提交任何真实密钥，不在终端或日志打印密码、令牌、密钥值。测试里的密码一律运行时随机生成。
- 动 mini 的每一步先征得用户确认。改了 `*.gradle.kts` 或 `settings.gradle.kts` 后，提醒用户在 IDEA 里点「加载 Gradle 更改」。
- `check` 含 SpotBugs。报错先修；确属误报才加带理由的排除，并记录 Ruling。

## Review Focus

spec 没写、但最可能坑到人的 5 种输入。每条都已在所属任务里加了测试：

1. **配置刷新时，服务配置少了签到行**：这次变更被拒绝，应用继续运行并保留旧值。（Task 5 `should_keep_old_values_when_refreshed_config_lacks_signature`）
2. **两个 `.env` 文件里有同名变量**：按文件名排序后读，后读的文件为准，结果稳定。（Task 2 `should_let_later_file_win_when_key_duplicated`）
3. **`.env` 文件里有注释行和空行**：它们不会变成属性。（Task 2 `should_skip_comments_and_blank_lines`）
4. **签到值是空白字符串**：当作没拿到，启动失败。（Task 3 `should_treat_blank_signature_as_missing`）
5. **导入写成 `optional:nacos:…`，或来自 `SPRING_CONFIG_IMPORT` 环境变量**：仍算有 Nacos 导入，不关闭配置中心。（Task 1 `should_keep_config_center_when_import_is_optional_nacos`）

---

### Task 1: 模块骨架与连接、服务发现默认值

**Files:**
- Modify: `gradle/libs.versions.toml`（在 `spring-cloud-starter-alibaba-nacos-discovery` 那一行后面）
- Modify: `settings.gradle.kts`（patra-starters 段）
- Create: `patra-starters/patra-spring-boot-starter-nacos/build.gradle.kts`
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/main/java/dev/linqibin/patra/starter/nacos/NacosDefaultsPostProcessor.java`
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/main/resources/META-INF/spring.factories`
- Test: `patra-starters/patra-spring-boot-starter-nacos/src/test/java/dev/linqibin/patra/starter/nacos/NacosDefaultsPostProcessorTest.java`
- Modify（生成）: `patra-infra/cd/module-graph.json`

**Interfaces:**
- Consumes: 无
- Produces:
  - `public class NacosDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered`，无参构造；
  - `public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER - 1`；
  - `static final String CONFIG_ENABLED_PROPERTY = "spring.cloud.nacos.config.enabled"`；
  - 登记在 `META-INF/spring.factories` 的 `org.springframework.boot.EnvironmentPostProcessor` 键下。

- [ ] **Step 1: 加依赖别名、注册模块、写构建脚本**

`gradle/libs.versions.toml` 在 `spring-cloud-starter-alibaba-nacos-discovery = …` 一行后面加：

```toml
spring-cloud-starter-alibaba-nacos-config = { module = "com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config" }
```

`settings.gradle.kts` 在 `includeAt(":patra-starters:patra-spring-boot-starter-security", …)` 后面加：

```kotlin
includeAt(":patra-starters:patra-spring-boot-starter-nacos", "patra-starters/patra-spring-boot-starter-nacos")
```

`patra-starters/patra-spring-boot-starter-nacos/build.gradle.kts`：

```kotlin
/**
 * Patra Spring Boot Starter - Nacos
 *
 * Patra 服务接入 Nacos：
 * - 统一注入 Nacos 连接和服务发现设置（所有启动方式都生效，包括测试）
 * - 启动器 PatraApplication 加入 patra-common.yaml、{服务名}.yaml 两条导入
 * - 签到检查：拿不到 Nacos 配置时启动失败
 * - 本机读取 ~/.patra/secrets/*.env
 *
 * 设计：docs/patra/specs/2026-10-13-nacos-config-starter-design.md
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.spring-boot-starter")
}

dependencies {
    // 配置中心：只用 spring.config.import 的 nacos: 导入，不引入 bootstrap（它会让动态刷新失效）
    api(libs.spring.cloud.starter.alibaba.nacos.config)

    // 服务发现
    api(libs.spring.cloud.starter.alibaba.nacos.discovery)

    // 集成测试：Testcontainers 起 Nacos；Awaitility 等配置刷新
    "integrationTestImplementation"(libs.bundles.testcontainers)
    "integrationTestImplementation"(libs.awaitility)
}

// SCA 的 NacosConfigManager 在进程里是静态单例：连哪个地址、用哪个账号，由进程里第一个启动的
// 应用决定；nacos-client 的快照目录也在类加载时固定。地址或账号不同的集成测试类各用一个 JVM。
tasks.named<Test>("integrationTest") {
    forkEvery = 1
}
```

- [ ] **Step 2: 写测试类骨架和第一条测试**

`NacosDefaultsPostProcessorTest.java`。先写类骨架、辅助方法和下面第一条测试：

```java
package dev.linqibin.patra.starter.nacos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/// NacosDefaultsPostProcessor 单元测试：连接与服务发现的默认值、配置中心开关、本机密钥文件。
///
/// 每个用例都换掉真实的系统环境变量，并把 `user.home` 指到临时目录：开发机上的
/// `PATRA_INFRA_HOST`、`~/.patra/secrets` 不影响结果。
@DisplayName("NacosDefaultsPostProcessor 单元测试")
class NacosDefaultsPostProcessorTest {

  private final NacosDefaultsPostProcessor postProcessor = new NacosDefaultsPostProcessor();

  @TempDir private Path home;

  @Test
  @DisplayName("没设任何变量时，Nacos 地址是 127.0.0.1:8848")
  void should_default_server_addr_to_loopback_when_no_variable_set() {
    StandardEnvironment environment = process(environment(Map.of()));

    assertThat(environment.getProperty("spring.cloud.nacos.server-addr"))
        .isEqualTo("127.0.0.1:8848");
  }

  private StandardEnvironment environment(Map<String, Object> systemEnvironment) {
    StandardEnvironment environment = new StandardEnvironment();
    MutablePropertySources sources = environment.getPropertySources();
    sources.replace(
        SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
        new SystemEnvironmentPropertySource(
            SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, systemEnvironment));
    sources.addFirst(new MapPropertySource("test", Map.of("user.home", home.toString())));
    return environment;
  }

  private StandardEnvironment process(StandardEnvironment environment) {
    postProcessor.postProcessEnvironment(environment, new SpringApplication());
    return environment;
  }
}
```

- [ ] **Step 3: 跑测试，确认失败**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:test --tests "*NacosDefaultsPostProcessorTest*"`
Expected: 编译失败，报 `cannot find symbol … NacosDefaultsPostProcessor`。

- [ ] **Step 4: 按下表逐条 Red → Green，最终实现如 Step 5**

每条测试都要先看到它失败（类不存在、断言不符），再补最少的实现。下表的测试代码依次追加到测试类里（`import` 按需补上）：

```java
  @Test
  @DisplayName("没设 PATRA_NACOS_ADDR 时，地址取 PATRA_INFRA_HOST:8848")
  void should_use_infra_host_when_nacos_addr_not_set() {
    StandardEnvironment environment = process(environment(Map.of("PATRA_INFRA_HOST", "10.0.0.5")));

    assertThat(environment.getProperty("spring.cloud.nacos.server-addr"))
        .isEqualTo("10.0.0.5:8848");
  }

  @Test
  @DisplayName("设了 PATRA_NACOS_ADDR 时，它优先于 PATRA_INFRA_HOST")
  void should_prefer_nacos_addr_over_infra_host() {
    StandardEnvironment environment =
        process(
            environment(
                Map.of("PATRA_INFRA_HOST", "10.0.0.5", "PATRA_NACOS_ADDR", "nacos.example:8848")));

    assertThat(environment.getProperty("spring.cloud.nacos.server-addr"))
        .isEqualTo("nacos.example:8848");
  }

  @Test
  @DisplayName("账号密码从 NACOS_USERNAME、NACOS_PASSWORD 读取")
  void should_read_credentials_from_environment_variables() {
    StandardEnvironment environment =
        process(environment(Map.of("NACOS_USERNAME", "patra-app", "NACOS_PASSWORD", "pw")));

    assertThat(environment.getProperty("spring.cloud.nacos.username")).isEqualTo("patra-app");
    assertThat(environment.getProperty("spring.cloud.nacos.password")).isEqualTo("pw");
  }

  @Test
  @DisplayName("密码不给默认值：没设 NACOS_PASSWORD 时解析失败")
  void should_fail_to_resolve_password_when_variable_missing() {
    StandardEnvironment environment = process(environment(Map.of()));

    assertThatThrownBy(() -> environment.getProperty("spring.cloud.nacos.password"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NACOS_PASSWORD");
  }

  @Test
  @DisplayName("服务发现：service 取服务名，通告地址取 PATRA_ADVERTISE_IP，fail-fast 关闭")
  void should_configure_discovery_with_application_name_and_advertise_ip() {
    StandardEnvironment environment = environment(Map.of("PATRA_ADVERTISE_IP", "100.70.1.2"));
    environment
        .getPropertySources()
        .addLast(new MapPropertySource("app", Map.of("spring.application.name", "patra-registry")));

    process(environment);

    assertThat(environment.getProperty("spring.cloud.nacos.discovery.service"))
        .isEqualTo("patra-registry");
    assertThat(environment.getProperty("spring.cloud.nacos.discovery.ip")).isEqualTo("100.70.1.2");
    assertThat(environment.getProperty("spring.cloud.nacos.discovery.fail-fast"))
        .isEqualTo("false");
  }

  @Test
  @DisplayName("没设 PATRA_ADVERTISE_IP 时通告地址为空，由客户端自动探测")
  void should_leave_advertise_ip_empty_when_variable_missing() {
    StandardEnvironment environment = process(environment(Map.of()));

    assertThat(environment.getProperty("spring.cloud.nacos.discovery.ip")).isEmpty();
  }

  @Test
  @DisplayName("服务的配置能覆盖注入的默认值")
  void should_let_application_config_override_defaults() {
    StandardEnvironment environment = environment(Map.of());
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                "applicationConfig", Map.of("spring.cloud.nacos.server-addr", "other:8848")));

    process(environment);

    assertThat(environment.getProperty("spring.cloud.nacos.server-addr")).isEqualTo("other:8848");
  }

  @Test
  @DisplayName("启动器已放进 default properties 的同名键不被覆盖，其余键照常补上")
  void should_keep_launcher_default_properties() {
    StandardEnvironment environment = environment(Map.of());
    environment
        .getPropertySources()
        .addLast(
            new DefaultPropertiesPropertySource(
                Map.of(
                    "spring.application.name", "patra-registry",
                    "spring.cloud.nacos.server-addr", "launcher:8848")));

    process(environment);

    assertThat(environment.getProperty("spring.cloud.nacos.server-addr"))
        .isEqualTo("launcher:8848");
    assertThat(environment.getProperty("spring.application.name")).isEqualTo("patra-registry");
    assertThat(
            environment
                .getPropertySources()
                .get(DefaultPropertiesPropertySource.NAME)
                .getProperty("spring.cloud.nacos.username"))
        .isEqualTo("${NACOS_USERNAME}");
  }

  @Test
  @DisplayName("环境里没有 nacos: 导入时，关闭 Nacos 配置中心")
  void should_disable_config_center_when_no_nacos_import() {
    StandardEnvironment environment = process(environment(Map.of()));

    assertThat(environment.getProperty("spring.cloud.nacos.config.enabled")).isEqualTo("false");
  }

  @Test
  @DisplayName("启动器加了 nacos: 导入时，不关闭配置中心")
  void should_keep_config_center_when_launcher_adds_nacos_import() {
    StandardEnvironment environment = environment(Map.of());
    environment
        .getPropertySources()
        .addLast(
            new DefaultPropertiesPropertySource(
                Map.of(
                    "spring.config.import",
                    "nacos:patra-common.yaml,nacos:patra-registry.yaml")));

    process(environment);

    assertThat(environment.getProperty("spring.cloud.nacos.config.enabled")).isNull();
  }

  @Test
  @DisplayName("导入写成 optional:nacos:、或来自 SPRING_CONFIG_IMPORT 环境变量时，不关闭配置中心")
  void should_keep_config_center_when_import_is_optional_nacos() {
    StandardEnvironment environment =
        process(environment(Map.of("SPRING_CONFIG_IMPORT", "optional:nacos:patra-common.yaml")));

    assertThat(environment.getProperty("spring.cloud.nacos.config.enabled")).isNull();
  }

  @Test
  @DisplayName("只有非 nacos: 的导入时，照样关闭配置中心")
  void should_disable_config_center_when_imports_have_no_nacos() {
    StandardEnvironment environment = environment(Map.of());
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "args", Map.of("spring.config.import", "classpath:registry-error-config.yaml")));

    process(environment);

    assertThat(environment.getProperty("spring.cloud.nacos.config.enabled")).isEqualTo("false");
  }

  @Test
  @DisplayName("排在 Spring Boot 加载配置之前")
  void should_run_before_config_data_loading() {
    assertThat(postProcessor.getOrder()).isLessThan(ConfigDataEnvironmentPostProcessor.ORDER);
  }
```

每条的单测命令同 Step 3。

- [ ] **Step 5: 最终实现**

`NacosDefaultsPostProcessor.java`：

```java
package dev.linqibin.patra.starter.nacos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/// 在 Spring Boot 加载配置之前，给环境补上 Nacos 的连接和服务发现设置。
///
/// 所有启动方式都经过它，包括测试，所以这里只放测试也需要的设置；服务名和导入由启动器
/// `PatraApplication` 负责。
///
/// - 默认值并入 default properties，优先级最低：服务的 `application.yml`、Nacos 配置、环境变量
///   都能覆盖；启动器已放进 default properties 的同名键保持不变。
/// - 环境里没有 `nacos:` 导入时（测试、没用启动器的应用）关闭 Nacos 配置中心，否则 SCA 的导入
///   检查会因为 `spring.config.import` 里没有 `nacos:` 让应用启动失败。这里只看得到已在环境里的
///   导入（启动器、启动参数、系统属性、环境变量）；写在 `application.yml` 里的导入这时还没加载，
///   按约定也不应该写在那里。
public class NacosDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered {

  /// 排在 Spring Boot 加载配置之前：SCA 解析 `nacos:` 导入时要读到这里注入的连接设置。
  public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER - 1;

  /// Nacos 配置中心的总开关。
  static final String CONFIG_ENABLED_PROPERTY = "spring.cloud.nacos.config.enabled";

  private static final String IMPORT_PROPERTY = "spring.config.import";
  private static final String OPTIONAL_PREFIX = "optional:";
  private static final String NACOS_PREFIX = "nacos:";

  /// 连接与服务发现的默认值。配置中心经 SCA 绑定 `spring.cloud.nacos` 取地址和账号，服务发现在
  /// 自己的地址、账号为空时也回落到这里。
  private static final Map<String, Object> DEFAULTS =
      Map.of(
          "spring.cloud.nacos.server-addr",
          "${PATRA_NACOS_ADDR:${PATRA_INFRA_HOST:127.0.0.1}:8848}",
          "spring.cloud.nacos.username",
          "${NACOS_USERNAME}",
          "spring.cloud.nacos.password",
          "${NACOS_PASSWORD}",
          "spring.cloud.nacos.discovery.service",
          "${spring.application.name}",
          "spring.cloud.nacos.discovery.ip",
          "${PATRA_ADVERTISE_IP:}",
          // nacos 3.x gRPC 握手未完成时同步注册会失败，fail-fast 会让整个应用启动失败
          "spring.cloud.nacos.discovery.fail-fast",
          "false");

  /// 补上默认值，并在没有 `nacos:` 导入时关闭配置中心。
  ///
  /// @param environment 要补充的环境
  /// @param application 正在启动的应用
  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> defaults = new LinkedHashMap<>(DEFAULTS);
    if (!hasNacosImport(environment)) {
      defaults.put(CONFIG_ENABLED_PROPERTY, "false");
    }
    addDefaults(environment.getPropertySources(), defaults);
  }

  /// 排序值，见 [#ORDER]。
  ///
  /// @return 排序值
  @Override
  public int getOrder() {
    return ORDER;
  }

  /// 环境里是否已有 `nacos:` 导入（含 `optional:nacos:`）。
  ///
  /// @param environment 要检查的环境
  /// @return 有任意一条 `nacos:` 导入时为 `true`
  private static boolean hasNacosImport(ConfigurableEnvironment environment) {
    List<String> imports =
        Binder.get(environment)
            .bind(IMPORT_PROPERTY, Bindable.listOf(String.class))
            .orElse(List.of());
    return imports.stream()
        .map(String::strip)
        .map(
            location ->
                location.startsWith(OPTIONAL_PREFIX)
                    ? location.substring(OPTIONAL_PREFIX.length())
                    : location)
        .anyMatch(location -> location.startsWith(NACOS_PREFIX));
  }

  /// 把默认值并入 default properties；已有的同名键（启动器放的）不覆盖。
  ///
  /// @param sources 环境的属性源
  /// @param defaults 要并入的默认值，会被修改
  private static void addDefaults(MutablePropertySources sources, Map<String, Object> defaults) {
    PropertySource<?> existing = sources.get(DefaultPropertiesPropertySource.NAME);
    if (existing != null) {
      defaults.keySet().removeIf(existing::containsProperty);
    }
    DefaultPropertiesPropertySource.addOrMerge(defaults, sources);
  }
}
```

`META-INF/spring.factories`：

```properties
org.springframework.boot.EnvironmentPostProcessor=\
dev.linqibin.patra.starter.nacos.NacosDefaultsPostProcessor
```

- [ ] **Step 6: 跑本类全部测试**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:test --tests "*NacosDefaultsPostProcessorTest*"`
Expected: `BUILD SUCCESSFUL`，14 个测试通过。

- [ ] **Step 7: 格式化、导出模块依赖图、跑模块 check**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:spotlessApply dumpModuleGraph :patra-starters:patra-spring-boot-starter-nacos:check`
Expected: `BUILD SUCCESSFUL`。`patra-infra/cd/module-graph.json` 里出现 `:patra-starters:patra-spring-boot-starter-nacos`。

提醒用户：`settings.gradle.kts` 和构建脚本有变化，请在 IDEA 里点「加载 Gradle 更改」。

- [ ] **Step 8: 提交**

```bash
git add gradle/libs.versions.toml settings.gradle.kts patra-infra/cd/module-graph.json patra-starters/patra-spring-boot-starter-nacos
git diff --cached --stat
git commit -m "feat(config-center): 新增 Nacos starter，注入连接与服务发现默认值 (PAP-74)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 本机密钥文件

**Files:**
- Modify: `patra-starters/patra-spring-boot-starter-nacos/src/main/java/dev/linqibin/patra/starter/nacos/NacosDefaultsPostProcessor.java`
- Test: `patra-starters/patra-spring-boot-starter-nacos/src/test/java/dev/linqibin/patra/starter/nacos/NacosDefaultsPostProcessorTest.java`

**Interfaces:**
- Consumes: Task 1 的 `NacosDefaultsPostProcessor`、测试辅助方法 `environment(Map)`、`process(StandardEnvironment)`、`@TempDir home`
- Produces: `static final String LOCAL_SECRETS_SOURCE_NAME = "patraLocalSecrets"`（属性源名，Task 5 不直接用）

- [ ] **Step 1: 逐条 Red → Green**

测试依次追加到 `NacosDefaultsPostProcessorTest`（补 `import java.nio.file.Files;`、`import java.io.IOException;`、`import org.springframework.core.env.EnumerablePropertySource;`）。每条先跑看失败，再改实现：

```java
  @Test
  @DisplayName("密钥目录不存在时跳过，不报错")
  void should_skip_local_secrets_when_directory_missing() {
    StandardEnvironment environment = process(environment(Map.of()));

    assertThat(
            environment
                .getPropertySources()
                .contains(NacosDefaultsPostProcessor.LOCAL_SECRETS_SOURCE_NAME))
        .isFalse();
  }

  @Test
  @DisplayName("读取密钥目录下所有 .env 文件")
  void should_read_all_env_files_in_secrets_directory() throws IOException {
    writeSecret("redis.env", "PATRA_TEST_A=1\n");
    writeSecret("gateway.env", "PATRA_TEST_B=2\n");

    StandardEnvironment environment = process(environment(Map.of()));

    assertThat(environment.getProperty("PATRA_TEST_A")).isEqualTo("1");
    assertThat(environment.getProperty("PATRA_TEST_B")).isEqualTo("2");
  }

  @Test
  @DisplayName("不是 .env 结尾的文件被忽略")
  void should_ignore_files_not_ending_with_env() throws IOException {
    writeSecret("notes.txt", "PATRA_TEST_TXT=1\n");
    writeSecret("nacos-server.env.example", "PATRA_TEST_EXAMPLE=1\n");

    StandardEnvironment environment = process(environment(Map.of()));

    assertThat(environment.getProperty("PATRA_TEST_TXT")).isNull();
    assertThat(environment.getProperty("PATRA_TEST_EXAMPLE")).isNull();
  }

  @Test
  @DisplayName("环境变量覆盖密钥文件里的同名值")
  void should_let_environment_variables_override_secret_files() throws IOException {
    writeSecret("redis.env", "PATRA_TEST_A=from-file\n");

    StandardEnvironment environment = process(environment(Map.of("PATRA_TEST_A", "from-env")));

    assertThat(environment.getProperty("PATRA_TEST_A")).isEqualTo("from-env");
  }

  @Test
  @DisplayName("密钥文件紧跟在系统环境变量之后")
  void should_place_secrets_right_after_system_environment() throws IOException {
    writeSecret("redis.env", "PATRA_TEST_A=1\n");

    MutablePropertySources sources = process(environment(Map.of())).getPropertySources();

    assertThat(sources.precedenceOf(sources.get(NacosDefaultsPostProcessor.LOCAL_SECRETS_SOURCE_NAME)))
        .isEqualTo(sources.precedenceOf(sources.get(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) + 1);
  }

  @Test
  @DisplayName("两个文件有同名变量时，按文件名排序后读，后读的为准")
  void should_let_later_file_win_when_key_duplicated() throws IOException {
    writeSecret("b.env", "PATRA_TEST_DUP=from-b\n");
    writeSecret("a.env", "PATRA_TEST_DUP=from-a\n");

    StandardEnvironment environment = process(environment(Map.of()));

    assertThat(environment.getProperty("PATRA_TEST_DUP")).isEqualTo("from-b");
  }

  @Test
  @DisplayName("注释行和空行不会变成属性")
  void should_skip_comments_and_blank_lines() throws IOException {
    writeSecret("redis.env", "# Redis 密码\n\nPATRA_TEST_A=1\n");

    StandardEnvironment environment = process(environment(Map.of()));

    EnumerablePropertySource<?> secrets =
        (EnumerablePropertySource<?>)
            environment
                .getPropertySources()
                .get(NacosDefaultsPostProcessor.LOCAL_SECRETS_SOURCE_NAME);
    assertThat(secrets.getPropertyNames()).containsExactly("PATRA_TEST_A");
  }

  private void writeSecret(String fileName, String content) throws IOException {
    Path directory = Files.createDirectories(home.resolve(".patra/secrets"));
    Files.writeString(directory.resolve(fileName), content);
  }
```

`MutablePropertySources.precedenceOf(PropertySource)` 是 public 方法。如果当前 Spring 版本不可见，改用 `sources.stream().toList().indexOf(...)`，并记 Ruling。

单测命令：`./gradlew :patra-starters:patra-spring-boot-starter-nacos:test --tests "*NacosDefaultsPostProcessorTest*"`

- [ ] **Step 2: 最终实现**

在 `NacosDefaultsPostProcessor` 里：
- 补 import：`java.io.IOException`、`java.io.Reader`、`java.io.UncheckedIOException`、`java.nio.charset.StandardCharsets`、`java.nio.file.Files`、`java.nio.file.Path`、`java.util.Properties`、`java.util.stream.Stream`、`org.springframework.core.env.PropertiesPropertySource`、`org.springframework.core.env.StandardEnvironment`；
- 类 Javadoc 的列表里加一条：「`${user.home}/.patra/secrets/*.env` 读成一组属性，放在系统环境变量之后，见 [#addLocalSecrets]。」
- `postProcessEnvironment` 末尾加 `addLocalSecrets(environment);`，Javadoc 改为「补上默认值和本机密钥，并在没有 `nacos:` 导入时关闭配置中心。」

加入：

```java
  /// 本机密钥文件的属性源名。
  static final String LOCAL_SECRETS_SOURCE_NAME = "patraLocalSecrets";

  /// 把 `${user.home}/.patra/secrets/` 下的 `*.env` 读成一组属性，放在系统环境变量之后。
  ///
  /// 文件格式是 `KEY=value`（与 compose 的 env_file 共用同一批文件），按 properties 解析；
  /// 按文件名排序后读，同名变量以后读的为准。目录不存在时跳过：容器里没有这个目录，
  /// 值来自 env_file 注入的环境变量。
  ///
  /// @param environment 要补充的环境
  private static void addLocalSecrets(ConfigurableEnvironment environment) {
    Path directory = Path.of(environment.getRequiredProperty("user.home"), ".patra", "secrets");
    if (!Files.isDirectory(directory)) {
      return;
    }
    Properties secrets = new Properties();
    for (Path file : listEnvFiles(directory)) {
      try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
        secrets.load(reader);
      } catch (IOException e) {
        throw new UncheckedIOException("读取本机密钥文件失败：" + file, e);
      }
    }
    environment
        .getPropertySources()
        .addAfter(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new PropertiesPropertySource(LOCAL_SECRETS_SOURCE_NAME, secrets));
  }

  /// 列出目录下以 `.env` 结尾的普通文件，按文件名排序。
  ///
  /// @param directory 密钥目录
  /// @return 排好序的文件
  private static List<Path> listEnvFiles(Path directory) {
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> file.getFileName().toString().endsWith(".env"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("列出本机密钥目录失败：" + directory, e);
    }
  }
```

- [ ] **Step 3: 跑本类全部测试**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:test --tests "*NacosDefaultsPostProcessorTest*"`
Expected: `BUILD SUCCESSFUL`，21 个测试通过。

- [ ] **Step 4: 格式化、模块 check、提交**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:spotlessApply :patra-starters:patra-spring-boot-starter-nacos:check`
Expected: `BUILD SUCCESSFUL`

```bash
git add patra-starters/patra-spring-boot-starter-nacos
git diff --cached --stat
git commit -m "feat(config-center): 本机读取 ~/.patra/secrets 下的密钥文件 (PAP-74)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 签到检查

**Files:**
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/main/java/dev/linqibin/patra/starter/nacos/ConfigSourceCheckPostProcessor.java`
- Modify: `patra-starters/patra-spring-boot-starter-nacos/src/main/resources/META-INF/spring.factories`
- Test: `patra-starters/patra-spring-boot-starter-nacos/src/test/java/dev/linqibin/patra/starter/nacos/ConfigSourceCheckPostProcessorTest.java`
- Modify: `docs/patra/specs/2026-10-13-nacos-config-starter-design.md`（5.1、5.5）

**Interfaces:**
- Consumes: 无
- Produces:
  - `public class ConfigSourceCheckPostProcessor implements EnvironmentPostProcessor, Ordered`；
  - `public static final String REQUIRED_PROPERTY = "patra.config-source.required"`；
  - `public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER + 1`；
  - `static final String COMMON_PROPERTY = "patra.config-source.common"`；
  - `static final String SERVICE_PROPERTY = "patra.config-source.service"`；
  - 检查失败时抛 `IllegalStateException`，消息为 Global Constraints 里的原文，两条都缺时用换行连接。

- [ ] **Step 1: 写测试类骨架和第一条测试**

```java
package dev.linqibin.patra.starter.nacos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

/// ConfigSourceCheckPostProcessor 单元测试：两份 Nacos 配置的签到检查。
@DisplayName("ConfigSourceCheckPostProcessor 单元测试")
class ConfigSourceCheckPostProcessorTest {

  private static final String COMMON_MISSING =
      "没拿到 Nacos 配置 patra-common.yaml：Nacos 不可达且本地没有快照，或该文件不存在";
  private static final String SERVICE_MISSING =
      "没拿到本服务的 Nacos 配置 patra-ingest.yaml：Nacos 不可达且本地没有快照，或该文件不存在";

  private final ConfigSourceCheckPostProcessor postProcessor =
      new ConfigSourceCheckPostProcessor();

  @Test
  @DisplayName("没开启签到检查时（测试、没用启动器），缺签到也不报错")
  void should_skip_check_when_not_required() {
    MockEnvironment environment = new MockEnvironment();

    assertThatCode(() -> check(environment)).doesNotThrowAnyException();
  }

  private void check(MockEnvironment environment) {
    postProcessor.postProcessEnvironment(environment, new SpringApplication());
  }

  private static MockEnvironment requiredEnvironment() {
    return new MockEnvironment()
        .withProperty(ConfigSourceCheckPostProcessor.REQUIRED_PROPERTY, "true")
        .withProperty("spring.application.name", "patra-ingest");
  }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:test --tests "*ConfigSourceCheckPostProcessorTest*"`
Expected: 编译失败，报 `cannot find symbol … ConfigSourceCheckPostProcessor`。

- [ ] **Step 3: 逐条 Red → Green**

依次追加，每条先看失败：

```java
  @Test
  @DisplayName("两个签到都在时通过")
  void should_pass_when_both_signatures_present() {
    MockEnvironment environment =
        requiredEnvironment()
            .withProperty("patra.config-source.common", "patra-common.yaml")
            .withProperty("patra.config-source.service", "patra-ingest.yaml");

    assertThatCode(() -> check(environment)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("缺公共配置的签到：启动失败，说明没拿到 patra-common.yaml")
  void should_fail_with_common_message_when_common_signature_missing() {
    MockEnvironment environment =
        requiredEnvironment().withProperty("patra.config-source.service", "patra-ingest.yaml");

    assertThatThrownBy(() -> check(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(COMMON_MISSING);
  }

  @Test
  @DisplayName("缺本服务配置的签到：启动失败，报错里带上服务名")
  void should_fail_with_service_message_when_service_signature_missing() {
    MockEnvironment environment =
        requiredEnvironment().withProperty("patra.config-source.common", "patra-common.yaml");

    assertThatThrownBy(() -> check(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(SERVICE_MISSING);
  }

  @Test
  @DisplayName("两个都缺时两条都报")
  void should_report_both_when_both_signatures_missing() {
    assertThatThrownBy(() -> check(requiredEnvironment()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(COMMON_MISSING + "\n" + SERVICE_MISSING);
  }

  @Test
  @DisplayName("签到值是空白字符串时当作没拿到")
  void should_treat_blank_signature_as_missing() {
    MockEnvironment environment =
        requiredEnvironment()
            .withProperty("patra.config-source.common", "  ")
            .withProperty("patra.config-source.service", "patra-ingest.yaml");

    assertThatThrownBy(() -> check(environment)).hasMessage(COMMON_MISSING);
  }

  @Test
  @DisplayName("排在 Spring Boot 加载配置之后")
  void should_run_after_config_data_loading() {
    assertThat(postProcessor.getOrder()).isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER);
  }
```

- [ ] **Step 4: 最终实现**

`ConfigSourceCheckPostProcessor.java`：

```java
package dev.linqibin.patra.starter.nacos;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/// 签到检查：确认两份 Nacos 配置都到了，没到就让启动失败。
///
/// 每份 Nacos 配置里写一行签到，值为文件名：`patra-common.yaml` 写
/// `patra.config-source.common`，`{服务名}.yaml` 写 `patra.config-source.service`。jar 里永远
/// 不写，所以签到在，就说明对应的文件确实从 Nacos（或本地快照）读到了。SCA 拿不到配置时只打
/// warn、返回空配置，靠这一步兜底。
///
/// - 只在 `patra.config-source.required=true`（启动器设置）时检查；测试不经过启动器，不检查。
/// - 排在 Spring Boot 加载完配置之后，比任何 Bean 创建都早：Nacos 没到时，不会先冒出数据源、
///   Redis 之类不相关的报错。
/// - 配置刷新时 spring-cloud-context 会重跑所有 `EnvironmentPostProcessor`：少了签到行的那次
///   变更会被拒绝，应用继续用旧配置。
public class ConfigSourceCheckPostProcessor implements EnvironmentPostProcessor, Ordered {

  /// 签到检查开关，由启动器设为 `true`。
  public static final String REQUIRED_PROPERTY = "patra.config-source.required";

  /// 排在 Spring Boot 加载配置（含 Nacos 导入）之后。
  public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER + 1;

  /// `patra-common.yaml` 的签到字段。
  static final String COMMON_PROPERTY = "patra.config-source.common";

  /// `{服务名}.yaml` 的签到字段。
  static final String SERVICE_PROPERTY = "patra.config-source.service";

  private static final String CAUSE = "Nacos 不可达且本地没有快照，或该文件不存在";

  /// 开启了签到检查时，确认两个签到字段都有值；缺了就抛异常，启动中止。
  ///
  /// @param environment 已加载完配置的环境
  /// @param application 正在启动的应用
  /// @throws IllegalStateException 缺签到时，每缺一个写一行原因
  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (!environment.getProperty(REQUIRED_PROPERTY, Boolean.class, false)) {
      return;
    }
    List<String> missing = new ArrayList<>();
    if (!StringUtils.hasText(environment.getProperty(COMMON_PROPERTY))) {
      missing.add("没拿到 Nacos 配置 patra-common.yaml：" + CAUSE);
    }
    if (!StringUtils.hasText(environment.getProperty(SERVICE_PROPERTY))) {
      String dataId = environment.getProperty("spring.application.name") + ".yaml";
      missing.add("没拿到本服务的 Nacos 配置 " + dataId + "：" + CAUSE);
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(String.join("\n", missing));
    }
  }

  /// 排序值，见 [#ORDER]。
  ///
  /// @return 排序值
  @Override
  public int getOrder() {
    return ORDER;
  }
}
```

`META-INF/spring.factories` 改为：

```properties
org.springframework.boot.EnvironmentPostProcessor=\
dev.linqibin.patra.starter.nacos.NacosDefaultsPostProcessor,\
dev.linqibin.patra.starter.nacos.ConfigSourceCheckPostProcessor
```

- [ ] **Step 5: 跑本类全部测试**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:test --tests "*ConfigSourceCheckPostProcessorTest*"`
Expected: `BUILD SUCCESSFUL`，7 个测试通过。

- [ ] **Step 6: 把实现方式同步回 spec**

`docs/patra/specs/2026-10-13-nacos-config-starter-design.md`：

- 5.1 依赖列表删掉 `spring-boot-starter-validation` 这一条（含它后面的说明）。
- 5.5 把「starter 里有一个带 `@Validated` 的 `@ConfigurationProperties("patra.config-source")` record，两个签到字段都是 `@NotBlank`，报错信息写明原因：」改为：

  ```markdown
  starter 的第二个 `EnvironmentPostProcessor`（`ConfigSourceCheckPostProcessor`）在 Spring Boot 加载完配置（含 Nacos 导入）之后，立即检查这两个签到字段，缺了或为空白就抛异常、启动中止。报错写明原因，两个都缺时两条都报：
  ```

- 5.5 把「签到字段只写在 Nacos 的文件里……不依赖有人注入它。测试不经过启动器，不做这项检查。」改为：

  ```markdown
  签到字段只写在 Nacos 的文件里，jar 里永远不写，jar 默认值没法冒充。检查只在 `patra.config-source.required=true`（启动器设置）时进行，测试不经过启动器，不做这项检查。

  放在配置加载阶段而不用 Bean 校验（写实施计划时改定）：
  - 比任何 Bean 创建都早，Nacos 没到时不会先冒出数据源、Redis 等不相关的报错；
  - 报错里能带上实际的服务名；
  - 不依赖 Bean Validation 的实现。

  配置刷新时，spring-cloud-context 会重跑所有 `EnvironmentPostProcessor`。少了签到行的那次变更会被拒绝，应用继续用旧配置。
  ```

- 5.2 第一句「一个 `EnvironmentPostProcessor`」改为「一个 `EnvironmentPostProcessor`（`NacosDefaultsPostProcessor`）」。

- [ ] **Step 7: 格式化、模块 check、提交**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:spotlessApply :patra-starters:patra-spring-boot-starter-nacos:check`
Expected: `BUILD SUCCESSFUL`

```bash
git add patra-starters/patra-spring-boot-starter-nacos docs/patra/specs/2026-10-13-nacos-config-starter-design.md
git diff --cached --stat
git commit -m "feat(config-center): 拿不到 Nacos 配置时由签到检查让启动失败 (PAP-74)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 启动器 `PatraApplication`

**Files:**
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/main/java/dev/linqibin/patra/starter/nacos/PatraApplication.java`
- Test: `patra-starters/patra-spring-boot-starter-nacos/src/test/java/dev/linqibin/patra/starter/nacos/PatraApplicationTest.java`

**Interfaces:**
- Consumes: Task 3 的 `ConfigSourceCheckPostProcessor.REQUIRED_PROPERTY`
- Produces:
  - `public static ConfigurableApplicationContext run(String applicationName, Class<?> source, String... args)`；
  - `static Map<String, Object> defaultProperties(String applicationName)`：空白名字时抛 `IllegalArgumentException("服务名不能为空")`；
  - `static final String COMMON_DATA_ID = "patra-common.yaml"`；
  - `static final String NAMESPACE = "dev"`。

- [ ] **Step 1: 写测试类骨架和第一条测试**

```java
package dev.linqibin.patra.starter.nacos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// PatraApplication 单元测试：启动器放进 default properties 的内容。
@DisplayName("PatraApplication 单元测试")
class PatraApplicationTest {

  @Test
  @DisplayName("服务名放进 spring.application.name")
  void should_put_application_name() {
    assertThat(PatraApplication.defaultProperties("patra-registry"))
        .containsEntry("spring.application.name", "patra-registry");
  }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:test --tests "*PatraApplicationTest*"`
Expected: 编译失败，报 `cannot find symbol … PatraApplication`。

- [ ] **Step 3: 逐条 Red → Green**

```java
  @Test
  @DisplayName("先导入公共配置、再导入本服务配置，都不标 optional")
  void should_import_common_then_service_config_without_optional() {
    assertThat(PatraApplication.defaultProperties("patra-registry"))
        .containsEntry(
            "spring.config.import", "nacos:patra-common.yaml,nacos:patra-registry.yaml");
  }

  @Test
  @DisplayName("配置中心用 dev namespace，Nacos 的值优先于 jar")
  void should_use_dev_namespace_and_prefer_remote_config() {
    assertThat(PatraApplication.defaultProperties("patra-registry"))
        .containsEntry("spring.cloud.nacos.config.namespace", "dev")
        .containsEntry("spring.cloud.nacos.config.preference", "remote");
  }

  @Test
  @DisplayName("开启签到检查")
  void should_require_config_source_check() {
    assertThat(PatraApplication.defaultProperties("patra-registry"))
        .containsEntry(ConfigSourceCheckPostProcessor.REQUIRED_PROPERTY, "true");
  }

  @Test
  @DisplayName("只放这五项")
  void should_contain_only_launcher_properties() {
    assertThat(PatraApplication.defaultProperties("patra-registry")).hasSize(5);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  @DisplayName("服务名为空时报错")
  void should_reject_blank_application_name(String applicationName) {
    assertThatThrownBy(() -> PatraApplication.defaultProperties(applicationName))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("服务名不能为空");
  }
```

- [ ] **Step 4: 最终实现**

`PatraApplication.java`：

```java
package dev.linqibin.patra.starter.nacos;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/// Patra 服务的启动器，服务的启动类用它代替 `SpringApplication.run`：
///
/// ```java
/// PatraApplication.run("patra-registry", PatraRegistryApplication.class, args);
/// ```
///
/// 启动前把服务名、Nacos 配置的导入列表等放进 Spring 的 default properties（优先级最低，
/// 环境变量、配置文件、Nacos 都能覆盖），服务的配置文件里因此不写任何 Nacos 配置。
///
/// 导入不写在配置文件里，原因有两个：
/// - 初始导入里的服务名占位符解析不了；
/// - 写在 `application.yml` 里的导入，测试关不掉。
///
/// 测试不经过启动器：不导入 Nacos 配置，也不做签到检查。
public final class PatraApplication {

  /// 跨服务共享的 Nacos 配置。
  static final String COMMON_DATA_ID = "patra-common.yaml";

  /// 配置中心的 namespace：只有 dev 一个环境。
  static final String NAMESPACE = "dev";

  /// 只有静态方法，不实例化。
  private PatraApplication() {}

  /// 以 Patra 服务的方式启动应用。
  ///
  /// @param applicationName 服务名，也是 Nacos 里本服务配置的文件名（`{服务名}.yaml`）
  /// @param source 启动类
  /// @param args 启动参数
  /// @return 启动好的应用上下文
  public static ConfigurableApplicationContext run(
      String applicationName, Class<?> source, String... args) {
    SpringApplication application = new SpringApplication(source);
    application.setDefaultProperties(defaultProperties(applicationName));
    return application.run(args);
  }

  /// 启动器放进 default properties 的内容。
  ///
  /// @param applicationName 服务名
  /// @return 服务名、两条导入、namespace、Nacos 优先、签到开关
  /// @throws IllegalArgumentException 服务名为空时
  static Map<String, Object> defaultProperties(String applicationName) {
    if (applicationName == null || applicationName.isBlank()) {
      throw new IllegalArgumentException("服务名不能为空");
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("spring.application.name", applicationName);
    // 后导入的覆盖先导入的：本服务的配置覆盖公共配置
    properties.put(
        "spring.config.import",
        "nacos:" + COMMON_DATA_ID + ",nacos:" + applicationName + ".yaml");
    properties.put("spring.cloud.nacos.config.namespace", NAMESPACE);
    // Nacos 的值覆盖 jar 里的默认值（SCA 默认是本地优先）
    properties.put("spring.cloud.nacos.config.preference", "remote");
    properties.put(ConfigSourceCheckPostProcessor.REQUIRED_PROPERTY, "true");
    return properties;
  }
}
```

- [ ] **Step 5: 跑模块全部单元测试**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:test`
Expected: `BUILD SUCCESSFUL`，共 36 个测试通过（21 + 7 + 8，参数化用例算 3 个）。

- [ ] **Step 6: 格式化、模块 check、提交**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:spotlessApply :patra-starters:patra-spring-boot-starter-nacos:check`
Expected: `BUILD SUCCESSFUL`

```bash
git add patra-starters/patra-spring-boot-starter-nacos
git diff --cached --stat
git commit -m "feat(config-center): 新增启动器 PatraApplication，加入两份 Nacos 配置的导入 (PAP-74)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 集成测试（Testcontainers Nacos 3.2.4）

这些集成测试验证前 4 个任务组合起来的效果，以及 SCA、nacos-client 的实际行为，对应 spec 第 9 节要实测的第 1～4 点。

- 前 4 个任务都已实现，预期首跑即通过。
- 某条失败时，先用 superpowers:systematic-debugging 找原因，不要改断言迁就。
- 只有确认 spec 的推断与实际不符时，才按实际改断言，同时改 spec 相应段落，并记 Ruling。

**Files:**
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/resources/application.yml`
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosITBootstrap.java`
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosServerFixture.java`
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosConfigImportIT.java`
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosWrongPasswordIT.java`
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosUnreachableIT.java`
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/WithoutLauncherIT.java`
- Modify: `docs/patra/specs/2026-10-13-nacos-config-starter-design.md`（5.7 写回实测结果）

**Interfaces:**
- Consumes:
  - `PatraApplication.run(String, Class<?>, String...)`；
  - `NacosDefaultsPostProcessor.CONFIG_ENABLED_PROPERTY`；
  - Task 3 的报错原文；
  - Task 1 构建脚本里的 `forkEvery = 1` 和 Testcontainers、Awaitility 依赖。
- Produces：`NacosServerFixture` 只在本 source set 内使用。
  - 实例方法：`start()`、`useGrpcPortOffset()`、`publish(String dataId, String content)`、`launch(String applicationName)`、`launch(String applicationName, String password)`、`close()`；
  - 静态方法：`isolateSnapshots()`、`causeChain(Throwable)`。

前置条件：本机 Docker（OrbStack）在运行。首次运行会拉 `nacos/nacos-server:v3.2.4`。

- [ ] **Step 1: 测试应用与 jar 默认值**

`src/integrationTest/resources/application.yml`：

```yaml
# 集成测试应用的 jar 默认值。故意不写任何 Nacos 配置：连接由 starter 注入，导入由启动器加。
patra:
  it:
    jar-vs-nacos: from-jar
    jar-only: from-jar
```

`NacosITBootstrap.java`：

```java
package dev.linqibin.patra.starter.nacos;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/// 集成测试用的应用，只存在于测试里。
///
/// 它的 `application.yml` 只有 jar 默认值，没有任何 Nacos 配置：和接入 starter 后的服务一样。
@SpringBootApplication
public class NacosITBootstrap {}
```

- [ ] **Step 2: Nacos 服务端 fixture**

`NacosServerFixture.java`：

```java
package dev.linqibin.patra.starter.nacos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/// 集成测试用的 Nacos 服务端：Testcontainers 起 3.2.4，开启鉴权。
///
/// 管理员由本类初始化，密码运行时随机生成；测试应用也用管理员账号连接。nacos-client 连 gRPC
/// 用「HTTP 端口 + 1000」，Testcontainers 随机映射的端口不满足这个关系，所以启动应用前要调
/// [#useGrpcPortOffset()]，把偏移设成实际的差值（JVM 级系统属性）。
final class NacosServerFixture implements AutoCloseable {

  /// 管理员账号。
  static final String ADMIN = "nacos";

  private static final DockerImageName IMAGE = DockerImageName.parse("nacos/nacos-server:v3.2.4");
  private static final int HTTP_PORT = 8848;
  private static final int GRPC_PORT = 9848;
  private static final int CONSOLE_PORT = 8080;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern ACCESS_TOKEN =
      Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern CODE_OK = Pattern.compile("\"code\"\\s*:\\s*0\\s*[,}]");

  private final GenericContainer<?> container;
  private final HttpClient http =
      HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
  private final String adminPassword = HexFormat.of().formatHex(randomBytes(12));
  private String token = "";

  /// 包装已启动的容器。
  ///
  /// @param container 已启动的 Nacos 容器
  private NacosServerFixture(GenericContainer<?> container) {
    this.container = container;
  }

  /// 启动 Nacos 容器，初始化管理员，建好 `dev` namespace。
  ///
  /// @return 可用的 fixture
  @SuppressWarnings("resource")
  static NacosServerFixture start() {
    GenericContainer<?> container =
        new GenericContainer<>(IMAGE)
            .withExposedPorts(HTTP_PORT, GRPC_PORT, CONSOLE_PORT)
            .withEnv("MODE", "standalone")
            .withEnv("NACOS_AUTH_ENABLE", "true")
            .withEnv("NACOS_AUTH_TOKEN", Base64.getEncoder().encodeToString(randomBytes(32)))
            .withEnv("NACOS_AUTH_IDENTITY_KEY", HexFormat.of().formatHex(randomBytes(16)))
            .withEnv("NACOS_AUTH_IDENTITY_VALUE", HexFormat.of().formatHex(randomBytes(32)))
            .waitingFor(
                Wait.forHttp("/v3/console/health/readiness")
                    .forPort(CONSOLE_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));
    container.start();
    NacosServerFixture fixture = new NacosServerFixture(container);
    fixture.initialize();
    return fixture;
  }

  /// 把 nacos-client 的快照目录指到一个新的临时目录，避免受本机 `~/nacos/config` 里已有快照
  /// 的影响。必须在本 JVM 第一次加载 nacos-client 之前调用（快照目录在类加载时固定）。
  ///
  /// @return 临时目录
  static Path isolateSnapshots() {
    try {
      Path directory = Files.createTempDirectory("nacos-snapshot");
      System.setProperty("JM.SNAPSHOT.PATH", directory.toString());
      return directory;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 把异常链上每一层的类型和消息拼成一段文字，方便断言报错原因。
  ///
  /// @param failure 最外层异常
  /// @return 每层一行
  static String causeChain(Throwable failure) {
    StringBuilder chain = new StringBuilder();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      chain.append(cause.getClass().getName()).append(": ").append(cause.getMessage());
      chain.append('\n');
    }
    return chain.toString();
  }

  /// 让 nacos-client 用实际映射的 gRPC 端口：偏移设为两个映射端口的差值。
  void useGrpcPortOffset() {
    int offset = container.getMappedPort(GRPC_PORT) - container.getMappedPort(HTTP_PORT);
    System.setProperty("nacos.server.grpc.port.offset", String.valueOf(offset));
  }

  /// 应用连接用的地址。容器宿主是 localhost 时写成 127.0.0.1，避免先解析到 ::1。
  ///
  /// @return `host:port`
  String serverAddr() {
    String host = "localhost".equals(container.getHost()) ? "127.0.0.1" : container.getHost();
    return host + ":" + container.getMappedPort(HTTP_PORT);
  }

  /// 在 `dev` namespace 的 `DEFAULT_GROUP` 下发布（或覆盖）一份 yaml 配置。
  ///
  /// @param dataId 文件名
  /// @param content 配置正文
  void publish(String dataId, String content) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("namespaceId", PatraApplication.NAMESPACE);
    form.put("groupName", "DEFAULT_GROUP");
    form.put("dataId", dataId);
    form.put("content", content);
    form.put("type", "yaml");
    postExpectingOk("/v3/admin/cs/config", form);
  }

  /// 用管理员账号经启动器启动测试应用。
  ///
  /// @param applicationName 服务名
  /// @return 启动好的应用上下文，用完要关闭
  ConfigurableApplicationContext launch(String applicationName) {
    return launch(applicationName, adminPassword);
  }

  /// 用指定密码经启动器启动测试应用。
  ///
  /// @param applicationName 服务名
  /// @param password 连接 Nacos 用的密码
  /// @return 启动好的应用上下文，用完要关闭
  ConfigurableApplicationContext launch(String applicationName, String password) {
    return PatraApplication.run(
        applicationName,
        NacosITBootstrap.class,
        "--spring.main.web-application-type=none",
        "--PATRA_NACOS_ADDR=" + serverAddr(),
        "--NACOS_USERNAME=" + ADMIN,
        "--NACOS_PASSWORD=" + password);
  }

  /// 停掉容器。
  @Override
  public void close() {
    http.close();
    container.stop();
  }

  /// 初始化管理员、登录拿令牌、建 `dev` namespace。
  private void initialize() {
    // 成功时响应体里有明文密码，postExpectingOk 不打印响应体
    postExpectingOk("/v3/auth/user/admin", Map.of("password", adminPassword));
    HttpResponse<String> login =
        post("/v3/auth/user/login", Map.of("username", ADMIN, "password", adminPassword));
    Matcher matcher = ACCESS_TOKEN.matcher(login.body());
    if (login.statusCode() != 200 || !matcher.find()) {
      throw new IllegalStateException("Nacos 管理员登录失败：HTTP " + login.statusCode());
    }
    token = matcher.group(1);
    postExpectingOk(
        "/v3/admin/core/namespace",
        Map.of(
            "namespaceId", PatraApplication.NAMESPACE,
            "namespaceName", PatraApplication.NAMESPACE));
  }

  /// 发 POST 表单请求，要求 HTTP 200 且响应 `code` 为 0。失败时只报路径和状态码，不报响应体。
  ///
  /// @param path 不含 `/nacos` 前缀的路径
  /// @param form 表单字段
  private void postExpectingOk(String path, Map<String, String> form) {
    HttpResponse<String> response = post(path, form);
    if (response.statusCode() != 200 || !CODE_OK.matcher(response.body()).find()) {
      throw new IllegalStateException("Nacos 接口失败：" + path + " HTTP " + response.statusCode());
    }
  }

  /// 发 POST 表单请求，带上已有的令牌。
  ///
  /// @param path 不含 `/nacos` 前缀的路径
  /// @param form 表单字段
  /// @return 响应
  private HttpResponse<String> post(String path, Map<String, String> form) {
    String body =
        form.entrySet().stream()
            .map(
                entry ->
                    URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://" + serverAddr() + "/nacos" + path))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (!token.isEmpty()) {
      request.header("Authorization", "Bearer " + token);
    }
    try {
      return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /// 生成随机字节。
  ///
  /// @param length 字节数
  /// @return 随机字节
  private static byte[] randomBytes(int length) {
    byte[] bytes = new byte[length];
    RANDOM.nextBytes(bytes);
    return bytes;
  }
}
```

- [ ] **Step 3: 经启动器导入（spec 8.2 第 1、2、3、6、8 条与 Review Focus 第 1 条）**

`NacosConfigImportIT.java`：

```java
package dev.linqibin.patra.starter.nacos;

import static dev.linqibin.patra.starter.nacos.NacosServerFixture.causeChain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/// 经启动器导入 Nacos 配置的集成测试（spec 8.2 第 1、2、3、6、8 条）。
///
/// 这些用例连同一个 Nacos、用同一个账号，可以共用一个 JVM（SCA 在进程里只有一个 ConfigService）。
@DisplayName("经启动器导入 Nacos 配置 集成测试")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class NacosConfigImportIT {

  private static final String SERVICE = "patra-it-app";

  private static final String COMMON_YAML =
      """
      patra:
        config-source:
          common: patra-common.yaml
        it:
          jar-vs-nacos: from-common
          common-vs-service: from-common
          shared: from-common
      """;

  private static NacosServerFixture nacos;

  @BeforeAll
  static void startNacos() {
    NacosServerFixture.isolateSnapshots();
    nacos = NacosServerFixture.start();
    nacos.useGrpcPortOffset();
    nacos.publish("patra-common.yaml", COMMON_YAML);
    nacos.publish(SERVICE + ".yaml", serviceYaml("v1"));
  }

  @AfterAll
  static void stopNacos() {
    nacos.close();
  }

  @AfterEach
  void restore() {
    System.clearProperty("patra.it.shared");
    System.clearProperty("PATRA_IT_SECRET");
    nacos.publish(SERVICE + ".yaml", serviceYaml("v1"));
  }

  @Test
  @DisplayName("读到两份配置：Nacos 覆盖 jar，本服务配置覆盖公共配置")
  void should_read_both_data_ids_with_nacos_over_jar_and_service_over_common() {
    try (ConfigurableApplicationContext context = nacos.launch(SERVICE)) {
      ConfigurableEnvironment environment = context.getEnvironment();

      assertThat(environment.getProperty("patra.it.jar-vs-nacos")).isEqualTo("from-common");
      assertThat(environment.getProperty("patra.it.common-vs-service")).isEqualTo("from-service");
      assertThat(environment.getProperty("patra.it.jar-only")).isEqualTo("from-jar");
    }
  }

  @Test
  @DisplayName("系统属性覆盖 Nacos 的值")
  void should_let_system_property_override_nacos() {
    System.setProperty("patra.it.shared", "from-system");

    try (ConfigurableApplicationContext context = nacos.launch(SERVICE)) {
      assertThat(context.getEnvironment().getProperty("patra.it.shared")).isEqualTo("from-system");
    }
  }

  @Test
  @DisplayName("Nacos 配置里的 ${变量} 能解析到进程里的值")
  void should_resolve_placeholder_in_nacos_config() {
    System.setProperty("PATRA_IT_SECRET", "secret-for-test");

    try (ConfigurableApplicationContext context = nacos.launch(SERVICE)) {
      assertThat(context.getEnvironment().getProperty("patra.it.secret"))
          .isEqualTo("secret-for-test");
    }
  }

  @Test
  @DisplayName("本服务的配置在 Nacos 里不存在：启动失败，报签到错误")
  void should_fail_with_signature_message_when_service_data_id_missing() {
    assertThatThrownBy(() -> nacos.launch("patra-it-missing"))
        .satisfies(
            failure ->
                assertThat(causeChain(failure))
                    .contains("没拿到本服务的 Nacos 配置 patra-it-missing.yaml")
                    .doesNotContain("没拿到 Nacos 配置 patra-common.yaml"));
  }

  @Test
  @DisplayName("Nacos 里的值变了：运行中应用的配置环境读到新值")
  void should_see_new_value_after_nacos_change() {
    try (ConfigurableApplicationContext context = nacos.launch(SERVICE)) {
      assertThat(refreshable(context)).isEqualTo("v1");

      nacos.publish(SERVICE + ".yaml", serviceYaml("v2"));

      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(refreshable(context)).isEqualTo("v2"));
    }
  }

  @Test
  @DisplayName("刷新时本服务配置少了签到行：这次变更被拒绝，应用继续用旧值")
  void should_keep_old_values_when_refreshed_config_lacks_signature() {
    try (ConfigurableApplicationContext context = nacos.launch(SERVICE)) {
      nacos.publish(SERVICE + ".yaml", serviceYaml("v2"));
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(refreshable(context)).isEqualTo("v2"));

      nacos.publish(
          SERVICE + ".yaml",
          """
          patra:
            it:
              refreshable: unsigned
          """);

      await()
          .during(Duration.ofSeconds(5))
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(refreshable(context)).isEqualTo("v2"));
      assertThat(context.isActive()).isTrue();
    }
  }

  private static String refreshable(ConfigurableApplicationContext context) {
    return context.getEnvironment().getProperty("patra.it.refreshable");
  }

  private static String serviceYaml(String refreshable) {
    return """
        patra:
          config-source:
            service: patra-it-app.yaml
          it:
            common-vs-service: from-service
            secret: ${PATRA_IT_SECRET}
            refreshable: %s
        """
        .formatted(refreshable);
  }
}
```

- [ ] **Step 4: 跑这一个类**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:integrationTest --tests "*NacosConfigImportIT*"`
Expected: `BUILD SUCCESSFUL`，6 个测试通过。

失败时的排查方向（spec 第 9 节）：
- 第一条用例连不上：gRPC 偏移或代理。日志里看 nacos-client 连的端口。
- `jar-vs-nacos` 取到 `from-jar`：`preference=remote` 没生效。
- 签到相关用例失败：看 `causeChain` 的输出。

- [ ] **Step 5: 密码错误（spec 8.2 第 5 条）**

`NacosWrongPasswordIT.java`：

```java
package dev.linqibin.patra.starter.nacos;

import static dev.linqibin.patra.starter.nacos.NacosServerFixture.causeChain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// 密码错误的集成测试（spec 8.2 第 5 条）。账号与其他用例不同，单独一个 JVM。
@DisplayName("Nacos 密码错误 集成测试")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class NacosWrongPasswordIT {

  private static NacosServerFixture nacos;

  @BeforeAll
  static void startNacos() {
    NacosServerFixture.isolateSnapshots();
    nacos = NacosServerFixture.start();
    nacos.useGrpcPortOffset();
    nacos.publish(
        "patra-common.yaml",
        """
        patra:
          config-source:
            common: patra-common.yaml
        """);
    nacos.publish(
        "patra-it-app.yaml",
        """
        patra:
          config-source:
            service: patra-it-app.yaml
        """);
  }

  @AfterAll
  static void stopNacos() {
    nacos.close();
  }

  @Test
  @DisplayName("密码错误：启动失败，原因是 Nacos 拒绝访问，不是签到检查")
  void should_fail_when_password_wrong() {
    assertThatThrownBy(() -> nacos.launch("patra-it-app", "wrong-password"))
        .satisfies(failure -> assertThat(causeChain(failure)).doesNotContain("没拿到"));
  }
}
```

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:integrationTest --tests "*NacosWrongPasswordIT*"`
Expected: `BUILD SUCCESSFUL`，1 个测试通过。

- [ ] **Step 6: Nacos 不可达（spec 8.2 第 4 条）**

`NacosUnreachableIT.java`：

```java
package dev.linqibin.patra.starter.nacos;

import static dev.linqibin.patra.starter.nacos.NacosServerFixture.causeChain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Nacos 不可达且没有快照的集成测试（spec 8.2 第 4 条）。地址与其他用例不同，单独一个 JVM。
@DisplayName("Nacos 不可达 集成测试")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class NacosUnreachableIT {

  @BeforeAll
  static void isolateSnapshots() {
    NacosServerFixture.isolateSnapshots();
  }

  @Test
  @DisplayName("Nacos 不可达且没有快照：启动失败，两份配置都报没拿到")
  void should_fail_with_signature_messages_when_nacos_unreachable_and_no_snapshot() {
    assertThatThrownBy(
            () ->
                PatraApplication.run(
                    "patra-it-app",
                    NacosITBootstrap.class,
                    "--spring.main.web-application-type=none",
                    "--PATRA_NACOS_ADDR=127.0.0.1:1",
                    "--NACOS_USERNAME=nacos",
                    "--NACOS_PASSWORD=unused"))
        .satisfies(
            failure ->
                assertThat(causeChain(failure))
                    .contains(
                        "没拿到 Nacos 配置 patra-common.yaml",
                        "没拿到本服务的 Nacos 配置 patra-it-app.yaml"));
  }
}
```

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:integrationTest --tests "*NacosUnreachableIT*"`
Expected: `BUILD SUCCESSFUL`，1 个测试通过。

- [ ] **Step 7: 不经启动器（spec 8.2 第 7 条）**

`WithoutLauncherIT.java`：

```java
package dev.linqibin.patra.starter.nacos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/// 不经启动器、用 `@SpringBootTest` 启动的集成测试（spec 8.2 第 7 条）。
///
/// 对应接入 starter 后各服务的测试：Nacos 不可达也照常启动，不导入 Nacos 配置，不做签到检查。
/// 服务发现照各服务测试 profile 的做法关掉。
@SpringBootTest(
    classes = NacosITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "PATRA_NACOS_ADDR=127.0.0.1:1",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.service-registry.auto-registration.enabled=false"
    })
@DisplayName("不经启动器启动 集成测试")
class WithoutLauncherIT {

  @Autowired private ConfigurableEnvironment environment;

  @Test
  @DisplayName("Nacos 不可达也正常启动：配置中心关闭，只有 jar 里的配置")
  void should_start_from_jar_config_without_nacos_import() {
    assertThat(environment.getProperty(NacosDefaultsPostProcessor.CONFIG_ENABLED_PROPERTY))
        .isEqualTo("false");
    assertThat(environment.getProperty("patra.it.jar-vs-nacos")).isEqualTo("from-jar");
    assertThat(environment.getPropertySources().stream().map(PropertySource::getName))
        .noneMatch(name -> name.contains("nacos:"));
  }
}
```

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:integrationTest --tests "*WithoutLauncherIT*"`
Expected: `BUILD SUCCESSFUL`，1 个测试通过。

- [ ] **Step 8: 跑模块全部集成测试**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:integrationTest`
Expected: `BUILD SUCCESSFUL`，4 个类共 9 个测试通过。

- [ ] **Step 9: 把实测结果写回 spec 5.7**

`docs/patra/specs/2026-10-13-nacos-config-starter-design.md` 5.7：

- 「按 Spring Boot 的规则推断，从高到低：」改为「从高到低：」。
- 最后一行「集成测试实测 1 > 3 > 4，结果写回本节（…）。」改为：

  ```markdown
  <执行当天日期> 集成测试（`NacosConfigImportIT`）实测：
  - 系统属性覆盖 Nacos，Nacos 覆盖 jar 的 `application.yml`；
  - 本服务配置覆盖公共配置；
  - Nacos 配置里的 `${变量}` 能解析到进程里的值；
  - 刷新时少了签到行的变更被拒绝，应用保留旧值。
  ```

  如果实测与推断有出入，按实际写，并在第 9 节对应条目后加「实测：…」。

- 第 9 节第 1～4 条各补一句「实测：…」，写明结论。
  - 第 1 条写「用系统属性 `nacos.server.grpc.port.offset` 解决」；
  - 第 2a 条写「`NacosDefaultsPostProcessor` 在 `ConfigDataEnvironmentPostProcessor` 之前运行，能看到启动器放进 default properties 的导入」。

- [ ] **Step 10: 格式化、提交**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:spotlessApply :patra-starters:patra-spring-boot-starter-nacos:check`
Expected: `BUILD SUCCESSFUL`

```bash
git add patra-starters/patra-spring-boot-starter-nacos docs/patra/specs/2026-10-13-nacos-config-starter-design.md
git diff --cached --stat
git commit -m "test(config-center): 用 Nacos 容器验证导入、覆盖、失败和刷新 (PAP-74)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: 撤销 `dev-secrets`：改 Nacos 脚本并本地验证

**Files:**
- Modify: `patra-infra/scripts/nacos-init.sh`
- Modify: `patra-infra/scripts/nacos-verify.sh`

**Interfaces:**
- Consumes: 无
- Produces：
  - `nacos-init.sh` 只确保 `dev` namespace 存在，`APP_PERMS` 两行；
  - `nacos-verify.sh` 共 9 项检查，加 `--expect-services` 时为 10 项。

纯脚本改动，不走 TDD；用一个本地临时 Nacos 容器验证。

- [ ] **Step 1: 改 `nacos-init.sh`**

- 第 2 行改为：`# Nacos 初始化：管理员、dev namespace、两个账号、角色和权限。幂等，可以反复执行。`
- 删掉 `# 密钥放 dev-secrets：patra-push 在那里不能有任何权限。……（spec 5.1）` 这一行注释，换成：

  ```bash
  # 只有 dev 一个 namespace：PAP-74 撤销了 dev-secrets，密钥不进 Nacos。脚本只确保 namespace 存在，不删除
  ```

- `APP_PERMS` 改为：

  ```bash
  APP_PERMS='public:*:naming/*|rw
  dev:*:config/*|r'
  ```

- 删掉 `ensure_namespace dev-secrets "Patra dev 环境的密钥，只有管理员能改"` 这一行。

- [ ] **Step 2: 改 `nacos-verify.sh`**

- 用法注释改成：
  - 不带参数：`# 第 1～9 项`
  - 带 `--expect-services`：`# 另查第 10 项：6 个服务都已注册`
- 删掉 `PROBE_LEAK=nacos-verify-probe-leak.yaml` 和 `TMP=""`。
- 删掉 `push_import`、`push_clone`、`content` 三个函数及其注释。
- `cleanup` 改为：

  ```bash
  cleanup() {
    [ -n "$AT" ] || return 0
    local ns
    cfg DELETE "$AT" dev DEFAULT_GROUP "$PROBE"
    cfg DELETE "$AT" dev DEFAULT_GROUP "$PROBE_PUSH"
    cfg DELETE "$AT" public DEFAULT_GROUP "$PROBE_PUSH"
    for ns in public dev; do
      inst DELETE "$AT" "$ns"
      nacos_api DELETE /v3/admin/ns/service "$AT" "namespaceId=$ns" groupName=DEFAULT_GROUP "serviceName=$PROBE_SVC"
    done
  }
  ```

- 第 4 项改为：

  ```bash
  D="namespace 列表有 dev，没有已撤销的 dev-secrets"
  if [ -z "$AT" ]; then
    fail 4 "$D" "没有管理员令牌"
  else
    nacos_api GET /v3/admin/core/namespace/list "$AT"
    if nacos_ok && jq -e '[.data[].namespace] | (index("dev") != null) and (index("dev-secrets") == null)' <<<"$NACOS_BODY" >/dev/null; then
      pass 4 "$D"
    else
      fail 4 "$D" "$(detail)，现有：$(jq -r '[.data[]?.namespace] | join(",")' <<<"$NACOS_BODY" 2>/dev/null)"
    fi
  fi
  ```

- 第 5 项：`D="patra-app 能读 dev 的配置"`，循环改为 `for ns in dev; do`。
- 第 6 项改为：

  ```bash
  D="patra-app 发布 dev、public 配置都被拒"
  if [ -z "$APT" ]; then
    fail 6 "$D" "patra-app 登录失败"
  else
    cfg POST "$APT" dev DEFAULT_GROUP "$PROBE_PUSH"; r1="$NACOS_HTTP"
    cfg POST "$APT" public DEFAULT_GROUP "$PROBE_PUSH"; r2="$NACOS_HTTP"
    if [ "$r1" = 403 ] && [ "$r2" = 403 ]; then
      pass 6 "$D"
    else
      fail 6 "$D" "dev HTTP $r1，public HTTP $r2"
    fi
  fi
  ```

- 删掉第 10 项的整段：从 `# 导入、批量删除的鉴权取请求参数里的 groupName……` 注释，到 `fail 10 …` 所在 `if` 块的 `fi`。
- `--expect-services` 那一项的编号 `11` 全部改为 `10`（`fail 11` → `fail 10`，`pass 11` → `pass 10`）。

- [ ] **Step 3: 语法检查**

Run: `bash -n patra-infra/scripts/nacos-init.sh && bash -n patra-infra/scripts/nacos-verify.sh && (command -v shellcheck >/dev/null && shellcheck -x patra-infra/scripts/nacos-init.sh patra-infra/scripts/nacos-verify.sh || echo "未装 shellcheck，跳过")`
Expected: 没有输出，或只输出「未装 shellcheck，跳过」。shellcheck 报的新问题要修掉。

- [ ] **Step 4: 本地临时容器验证（旧脚本 → 新脚本 → 删 namespace → 验收）**

一次 Bash 调用跑完。所有密码都是一次性的随机值，不打印。旧版脚本从 `HEAD` 取，放进 scratchpad：

```bash
set -eo pipefail
R=/Users/linqibin/Projects/Products/patra
T=$(mktemp -d /private/tmp/claude-501/-Users-linqibin-Projects-Products-patra/86b59638-8d2a-4194-bfc7-ee01f9010782/scratchpad/pap74-nacos.XXXX)
git -C "$R" show HEAD:patra-infra/scripts/nacos-init.sh > "$T/nacos-init.sh"
git -C "$R" show HEAD:patra-infra/scripts/nacos-lib.sh > "$T/nacos-lib.sh"
docker run -d --name pap74-nacos-script -p 18848:8848 -p 19848:9848 -p 18080:8080 \
  -e MODE=standalone -e NACOS_AUTH_ENABLE=true \
  -e NACOS_AUTH_TOKEN="$(openssl rand -base64 32)" \
  -e NACOS_AUTH_IDENTITY_KEY="$(openssl rand -hex 16)" \
  -e NACOS_AUTH_IDENTITY_VALUE="$(openssl rand -hex 32)" \
  nacos/nacos-server:v3.2.4 >/dev/null
export NACOS_ADDR=http://127.0.0.1:18848 NACOS_CONSOLE_ADDR=http://127.0.0.1:18080 NACOS_SECRETS_DIR=/nonexistent
export NACOS_ADMIN_PASSWORD="$(openssl rand -hex 12)" NACOS_USERNAME=patra-app NACOS_PASSWORD="$(openssl rand -hex 12)"
export NACOS_PUSH_USERNAME=patra-push NACOS_PUSH_PASSWORD="$(openssl rand -hex 12)"
echo "== 旧脚本"; bash "$T/nacos-init.sh"
echo "== 新脚本"; bash "$R/patra-infra/scripts/nacos-init.sh"
echo "== 新脚本重跑"; bash "$R/patra-infra/scripts/nacos-init.sh"
echo "== 删 dev-secrets"
. "$R/patra-infra/scripts/nacos-lib.sh"
nacos_login nacos "$NACOS_ADMIN_PASSWORD"
nacos_api GET /v3/admin/core/namespace/list "$NACOS_TOKEN"
jq -r '.data[] | "\(.namespace) 配置数 \(.configCount)"' <<<"$NACOS_BODY"
nacos_api DELETE /v3/admin/core/namespace "$NACOS_TOKEN" namespaceId=dev-secrets
nacos_ok && echo "已删除 dev-secrets" || echo "删除失败：HTTP $NACOS_HTTP code $(nacos_code)"
echo "== 验收"; bash "$R/patra-infra/scripts/nacos-verify.sh" || true
docker rm -f pap74-nacos-script >/dev/null
rm -rf "$T"
```

Expected:
- 旧脚本创建了 `dev`、`dev-secrets`。
- 新脚本第一次输出 `角色 PATRA_APP 的权限：新增 0，删除 1`；重跑输出 `新增 0，删除 0`。
- namespace 列表里有 `dev-secrets 配置数 0`，随后输出「已删除 dev-secrets」。
- 验收：
  - 第 2～9 项全部 PASS。
  - 第 1 项 FAIL 是预期的：本地容器用内嵌存储，不是 PG。
  - 最后一行 `通过 8 / 失败 1`。
- 脚本跑完后，容器和临时目录都已删除。

若 `nacos-init.sh` 因端口或代理连不上，确认 `NACOS_ADDR` 指向 18848；`nacos_api` 已带 `--noproxy '*'`。

- [ ] **Step 5: 提交**

```bash
git add patra-infra/scripts/nacos-init.sh patra-infra/scripts/nacos-verify.sh
git diff --cached --stat
git commit -m "chore(config-center): 撤销 dev-secrets namespace，Nacos 脚本只管 dev (PAP-74)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: mini 切换（每一步先征得用户确认）

**Files:** 无（只操作 mini）

**Interfaces:**
- Consumes: Task 6 的三个脚本（mini 的仓库还停在 main，所以把脚本复制到 mini 的临时目录执行）
- Produces: mini 上的 Nacos 没有 `dev-secrets`；`nacos-verify.sh --expect-services` 共 10 项，全部 PASS

- [ ] **Step 1: 向用户说明并征得确认**

用纯文本列出下面四步，等用户明确同意后再动 mini：
1. 复制脚本到 mini 的 `/tmp/pap74-nacos`；
2. 查 `dev-secrets` 里的配置数，为 0 才删；
3. 跑新的 `nacos-init.sh`；
4. 跑 `nacos-verify.sh --expect-services`。

- [ ] **Step 2: 复制脚本，查 namespace 里的配置数（只读）**

```bash
ssh mini 'mkdir -p /tmp/pap74-nacos'
scp patra-infra/scripts/nacos-lib.sh patra-infra/scripts/nacos-init.sh patra-infra/scripts/nacos-verify.sh mini:/tmp/pap74-nacos/
ssh mini 'bash -s' <<'EOF'
set -eo pipefail
export PATH=/opt/homebrew/bin:$PATH
. /tmp/pap74-nacos/nacos-lib.sh
nacos_load NACOS_ADMIN_PASSWORD nacos-admin.env
nacos_login nacos "$NACOS_ADMIN_PASSWORD"
nacos_api GET /v3/admin/core/namespace/list "$NACOS_TOKEN"
jq -r '.data[] | "\(.namespace) 配置数 \(.configCount)"' <<<"$NACOS_BODY"
EOF
```

Expected: 列出 `public`、`dev`、`dev-secrets`，其中 `dev-secrets 配置数 0`。不是 0 就停下，把里面的 dataId 列给用户，等用户决定。

- [ ] **Step 3: 删除 `dev-secrets`，跑初始化和验收**

```bash
ssh mini 'bash -s' <<'EOF'
set -eo pipefail
export PATH=/opt/homebrew/bin:$PATH
. /tmp/pap74-nacos/nacos-lib.sh
nacos_load NACOS_ADMIN_PASSWORD nacos-admin.env
nacos_login nacos "$NACOS_ADMIN_PASSWORD"
nacos_api DELETE /v3/admin/core/namespace "$NACOS_TOKEN" namespaceId=dev-secrets
nacos_ok && echo "已删除 dev-secrets" || { echo "删除失败：HTTP $NACOS_HTTP code $(nacos_code)"; exit 1; }
bash /tmp/pap74-nacos/nacos-init.sh
bash /tmp/pap74-nacos/nacos-verify.sh --expect-services
EOF
ssh mini 'rm -rf /tmp/pap74-nacos'
```

Expected:
- 输出「已删除 dev-secrets」。
- `nacos-init.sh` 输出 `角色 PATRA_APP 的权限：新增 0，删除 1`。如果删 namespace 时服务端已连带删掉权限，这里是 `删除 0`，也算正常，照实记录。
- `nacos-verify.sh` 最后一行 `通过 10 / 失败 0`。

- [ ] **Step 4: 记录**

把验收输出的 10 行 PASS 留作 Linear 证据（Task 8 用）。不提交任何文件。

---

### Task 8: 文档与 Linear 同步，全量门控

**Files:**
- Create: `patra-starters/patra-spring-boot-starter-nacos/README.md`
- Modify: `docs/patra/release-specs/v0.9-config-center.md`
- Modify: `docs/patra/specs/2026-10-11-nacos-hardening-design.md`（开头加说明）
- Modify: `docs/patra/specs/2026-10-13-nacos-config-starter-design.md`（状态行）
- Modify: `patra-infra/CLAUDE.md`（第 81 行）
- Modify: `patra-infra/docker/README.md`（第 95 行、第 236～238 行）
- Modify: `.claude/rules/project-info.md`（Patra 专属 Starter 列表）

**Interfaces:**
- Consumes: Task 1～7 的结果、Task 7 的验收输出
- Produces: 文档与代码一致；Linear 上 PAP-71/74/75/76/77 与本设计一致

- [ ] **Step 1: starter README**

`patra-starters/patra-spring-boot-starter-nacos/README.md`：

````markdown
# patra-spring-boot-starter-nacos

Patra 服务接入 Nacos 的 starter。它统一注入 Nacos 的连接和服务发现设置，经启动器从配置中心读取 `patra-common.yaml` 和 `{服务名}.yaml`，拿不到配置时启动失败。

工程设计：`docs/patra/specs/2026-10-13-nacos-config-starter-design.md`。

## 1. 怎么引入

boot 模块依赖本 starter，它带上 SCA 的 nacos-config 和 nacos-discovery：

```kotlin
implementation(project(":patra-starters:patra-spring-boot-starter-nacos"))
```

启动类改用启动器，服务名只写在这里：

```java
public static void main(String[] args) {
  PatraApplication.run("patra-registry", PatraRegistryApplication.class, args);
}
```

服务的 `application.yml` 里不写 `spring.application.name`，也不写任何 Nacos 配置。

## 2. 启动器做了什么

`PatraApplication.run` 在启动 Spring Boot 之前，把下面 5 项放进 default properties。它们优先级最低，环境变量、配置文件、Nacos 都能覆盖：

| 键 | 值 |
|---|---|
| `spring.application.name` | 传入的服务名 |
| `spring.config.import` | `nacos:patra-common.yaml,nacos:{服务名}.yaml`：后者覆盖前者，都不标 `optional:` |
| `spring.cloud.nacos.config.namespace` | `dev` |
| `spring.cloud.nacos.config.preference` | `remote`：Nacos 的值覆盖 jar 里的默认值 |
| `patra.config-source.required` | `true`：开启签到检查 |

导入不写在配置文件里，原因有两个：
- 初始导入里的服务名占位符解析不了；
- 写在 `application.yml` 里的导入，测试关不掉。

## 3. 所有启动方式都会注入的设置

`NacosDefaultsPostProcessor` 在 Spring Boot 加载配置之前运行，测试也会经过它。默认值并入 default properties，服务的配置能覆盖：

| 键 | 值 |
|---|---|
| `spring.cloud.nacos.server-addr` | `${PATRA_NACOS_ADDR:${PATRA_INFRA_HOST:127.0.0.1}:8848}` |
| `spring.cloud.nacos.username` / `password` | `${NACOS_USERNAME}` / `${NACOS_PASSWORD}`，不给默认值 |
| `spring.cloud.nacos.discovery.service` | `${spring.application.name}` |
| `spring.cloud.nacos.discovery.ip` | `${PATRA_ADVERTISE_IP:}`，留空时自动探测 |
| `spring.cloud.nacos.discovery.fail-fast` | `false`：nacos 3.x gRPC 握手未完成时，同步注册会让应用启动失败 |

它还做两件事：
- 环境里没有 `nacos:` 导入时（测试、没用启动器的应用），设 `spring.cloud.nacos.config.enabled=false`，不连配置中心。
- 把 `~/.patra/secrets/*.env` 读成一组属性，放在系统环境变量之后。
  - 文件按文件名排序后读，同名变量以后读的为准。
  - 目录不存在时跳过。容器里就是这种情况，值来自 compose env_file 注入的环境变量。

## 4. 签到检查

每份 Nacos 配置写一行签到，值为文件名，只写在 Nacos 里：

```yaml
# patra-common.yaml
patra:
  config-source:
    common: patra-common.yaml
```

```yaml
# patra-ingest.yaml
patra:
  config-source:
    service: patra-ingest.yaml
```

SCA 拿不到配置时只打 warn、返回空配置，不会让启动失败。所以 `ConfigSourceCheckPostProcessor` 在配置加载完之后检查这两个字段，缺了就让启动失败，例如：

```text
没拿到本服务的 Nacos 配置 patra-ingest.yaml：Nacos 不可达且本地没有快照，或该文件不存在
```

配置刷新时也会检查：少了签到行的那次变更被拒绝，应用继续用旧配置。

## 5. 优先级（从高到低）

1. 启动参数、系统属性、环境变量
2. 本机密钥文件 `~/.patra/secrets/*.env`
3. Nacos 配置（本服务的覆盖公共的）
4. jar 里的 `application.yml`
5. 启动器的 default properties、starter 注入的默认值

## 6. 密钥怎么写

- 公开的 dev 默认值（PG、MinIO、xxl-job 的账号密码）：Nacos 配置里直接写，或写成 `${变量:默认值}`。
- 真正的密钥（Redis 密码、网关私钥、外部数据源 API key）：Nacos 配置里只写 `${变量}`，不给默认值。值留在两台机器的 `~/.patra/secrets/`：本机由本 starter 读取，容器经 compose 的 env_file 注入。

## 7. 测试

- 测试不经过启动器，不导入 Nacos 配置，也不做签到检查。
- 需要服务名的测试，在测试 profile 里设 `spring.application.name`。
- 服务发现照旧在测试 profile 里关掉。
````

- [ ] **Step 2: release spec `docs/patra/release-specs/v0.9-config-center.md`**

逐处替换：

1. Milestone ① 表格：
   - PAP-71 行的「创建 `dev`、`dev-secrets` 两个 namespace」改为「创建 `dev`、`dev-secrets` 两个 namespace（`dev-secrets` 已在 PAP-74 撤销）」。
   - PAP-74 行的标题列改为：「[BE] Nacos 配置接入 starter：注入 Nacos 连接和服务发现设置；启动器 `PatraApplication` 加入 `patra-common.yaml`、`{服务名}.yaml` 两条导入（不标 `optional:`）；签到检查，拿不到配置时启动失败；本机读取 `~/.patra/secrets`；撤销 `dev-secrets`」。
2. 第 3 节表格：
   - A 行：「只建 dev 环境（`dev` 和放密钥的 `dev-secrets`）」改为「只建 dev 环境（一个 `dev` namespace）」。
   - F 行理由：改为「继续单机部署。Nacos 不可用时，本机 IDE 可靠 nacos-client 的本地快照启动，容器不处理快照（PAP-74 设计第 4 节决定 4）」。
3. 决策 A 的「决策」一行改为：

   ```markdown
   - **决策**：本版只有 dev 一个环境，只有一个 `dev` namespace，每个服务在 Nacos 里只存一份配置。密钥不进 Nacos（决策 E）。PAP-71 建的 `dev-secrets` 已在 PAP-74 撤销：SCA 让一个应用只连一个 namespace，要从两个 namespace 导入就得自己实现配置加载。IDE 和容器是同一个 dev 环境的两种运行方式，差异收敛为放在 Nacos 之外的本地变量，做法由 PAP-72 实测选定。`dev`、`container` 两个 profile 去掉。
   ```

   「否决」一行末尾追加：「密钥单独放一个 namespace（PAP-71 原做法）——SCA 不支持一个应用从两个 namespace 导入。」
4. 决策 C：
   - 删掉 `- \`patra-secrets.yaml\`：密钥（决策 E）。` 这一行。
   - 「环境变量、启动参数等进程级覆盖的优先级在 PAP-74 实测确定。」改为「进程级覆盖（启动参数、系统属性、环境变量）高于 Nacos，Nacos 高于 jar，PAP-74 集成测试已实测。」
5. 决策 E 整节（从 `### E.` 到决策 F 标题之前）替换为：

   ```markdown
   ### E. 密钥不进 Nacos：配置里用 `${变量}` 引用，值留在本地（PAP-74 改定，取代 PAP-71 的密钥 namespace）

   - **影响 Issue**：PAP-71、PAP-74、PAP-75、PAP-76、PAP-77
   - **决策**：Nacos 配置分两类写法。
     - 公开的 dev 默认值（PG、MinIO、xxl-job 的账号密码，已在仓库公开）：直接写，或写成 `${变量:默认值}`。
     - 真正的密钥（Redis 密码、网关签身份断言的私钥、外部数据源 API key）：只写 `${变量}`，不给默认值。值留在两台机器的 `~/.patra/secrets/`：本机由 starter 读取，容器经 compose 的 env_file 注入。

     Nacos 自身的加固（PAP-71）照旧：token 签名密钥和 identity 不在仓库、默认管理员密码已改、应用账号只读、推送账号只能写 `dev`。
   - **为什么改**：
     - PAP-71 把密钥放在单独的 namespace `dev-secrets`，但 SCA 让一个应用只连一个 namespace，同时读 `dev` 和 `dev-secrets` 就得自己实现配置加载。
     - git 推送是整份文件覆盖。「git 推送」「密钥写在普通配置文件里」「仓库公开」三者不能同时成立。
     - 所以密钥留在本地，配置文件里只放引用。v0.8 决策 G 的理由仍然成立：Redis 密码一公开，任何能连上的人都能写入假会话。
   - **已知代价**：
     - 改一个真正的密钥，仍要在两台机器的 `~/.patra/secrets/` 各改一次。只涉及 Redis 密码、网关私钥、几个外部 API key。
     - 本机进程能读到 `~/.patra/secrets/` 下的所有密钥。
   - **否决**：
     - 密钥放单独的 namespace（PAP-71 原做法）——要自己实现跨 namespace 的配置加载。
     - SOPS 加密后进 git——要多保管一把私钥，密文永久留在公开仓库历史里。
   ```

6. 决策 F 整节（从 `### F.` 到决策 G 标题之前）替换为：

   ```markdown
   ### F. 连接由 starter 注入，导入由启动器加（PAP-74 改定）

   - **影响 Issue**：PAP-74、PAP-76、PAP-77
   - **决策**：
     - 连接和服务发现设置由 starter 的 `EnvironmentPostProcessor` 注入，并入 default properties（优先级最低），地址和账号从环境变量读取。
     - 服务名和导入由启动器 `PatraApplication.run("服务名", X.class, args)` 放进 default properties。两条导入都不标 `optional:`，拿不到配置时由签到检查让启动失败。
     - 测试不经过启动器，不连 Nacos 配置中心。
   - **与 SpringBlade 的区别**：SpringBlade 的启动器写 System properties，环境变量覆盖不了；这里写 default properties，环境变量、配置文件、Nacos 都能覆盖。
   - **否决**：
     - 导入写在各服务的 `application.yml`——初始导入里的服务名占位符解析不了，测试也关不掉这条导入。
     - 构建时注入账号（pig、JeecgBoot）——换环境要重新打包。
   ```

7. Done 判定：
   - D2 第一条改为「6 个服务都从 Nacos 的 `dev` namespace 读取 `patra-common.yaml`、`{服务名}.yaml`」。
   - D2 第三条改为「compose 的 env 文件只剩 PAP-72 选定的本地变量、Nacos 连接凭据和密钥」。
   - D6：删掉「`~/.patra/secrets` 里除 Nacos 连接凭据外，其他文件不再被读取」这一条。
   - D7：删掉「停掉 Nacos 后重启某个服务，它靠本地快照正常启动」这一条，保留「Nacos 恢复后，配置变更照常推送和生效」。
   - D8：已写明「Nacos 配置接入 starter 都有单元测试和集成测试」，不用改。

- [ ] **Step 3: 其余文档**

- `docs/patra/specs/2026-10-11-nacos-hardening-design.md`：在文件开头的元信息块之后插入一段（先读文件开头确认位置）：

  ```markdown
  > **2026-10-13 更新**：`dev-secrets` namespace 已在 PAP-74 撤销，密钥不再进 Nacos，见 [Nacos 配置接入 starter 设计](2026-10-13-nacos-config-starter-design.md) 第 4 节。下文关于 `dev-secrets`、`patra-secrets.yaml` 的内容只作历史记录。
  ```

- `docs/patra/specs/2026-10-13-nacos-config-starter-design.md`：状态行改为「已实现（<执行当天日期>），mini 已撤销 `dev-secrets`」。
- `patra-infra/CLAUDE.md` 第 81 行：「管理员、`dev` 与 `dev-secrets` 两个 namespace、」改为「管理员、`dev` namespace、」。
- `patra-infra/docker/README.md`：
  - 第 95 行：「管理员、dev 与 dev-secrets 两个 namespace、账号、角色和权限」改为「管理员、dev namespace、账号、角色和权限」。
  - 第 236～238 行三条替换为：

    ```markdown
    - namespace：配置只有 `dev` 一个 namespace，服务发现留在 `public`。密钥不进 Nacos：配置里写 `${变量}`，值在 `~/.patra/secrets/`（PAP-74 撤销了 `dev-secrets`，见 `docs/patra/specs/2026-10-13-nacos-config-starter-design.md` 第 4 节）。
    - 账号：管理员 `nacos`；应用账号 `patra-app`（public 服务注册读写、`dev` 的配置只读）；推送账号 `patra-push`（只能写 `dev` 的 `DEFAULT_GROUP`）。
    ```

- `.claude/rules/project-info.md`「Patra 专属 Starter 与内核」列表加一行：`- \`patra-starters/patra-spring-boot-starter-nacos\` - Nacos 接入：连接与服务发现注入、启动器 \`PatraApplication\`、签到检查`。

- [ ] **Step 4: 检查遗漏**

Run: `grep -rn 'dev-secrets\|patra-secrets' docs/patra/release-specs/v0.9-config-center.md patra-infra/CLAUDE.md patra-infra/docker/README.md patra-infra/scripts`
Expected:
- 只剩两类内容：release spec 里 PAP-71 行的「（`dev-secrets` 已在 PAP-74 撤销）」、决策 A 和 E 里说明撤销原因的文字；决策 G「为什么不是原定的 3.1.x」里提到 `patra-secrets.yaml` 的历史描述。
- 脚本目录里没有命中。

- [ ] **Step 5: 全量门控**

Run: `./gradlew check > /private/tmp/claude-501/-Users-linqibin-Projects-Products-patra/86b59638-8d2a-4194-bfc7-ee01f9010782/scratchpad/pap74-check.log 2>&1; tail -30 /private/tmp/claude-501/-Users-linqibin-Projects-Products-patra/86b59638-8d2a-4194-bfc7-ee01f9010782/scratchpad/pap74-check.log`
Expected: `BUILD SUCCESSFUL`。

再跑一次本模块的集成测试，确认 `check` 之外的部分：

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:integrationTest`
Expected: `BUILD SUCCESSFUL`，9 个测试通过。

如果出现 `bad class file` 或 `NoSuchFileException`，那是 IDEA 在并发写 `build/classes`，不是代码问题：等 IDEA 空闲后重跑。

- [ ] **Step 6: 提交文档**

```bash
git add patra-starters/patra-spring-boot-starter-nacos/README.md docs/patra/release-specs/v0.9-config-center.md docs/patra/specs/2026-10-11-nacos-hardening-design.md docs/patra/specs/2026-10-13-nacos-config-starter-design.md patra-infra/CLAUDE.md patra-infra/docker/README.md .claude/rules/project-info.md
git diff --cached --stat
git commit -m "docs(config-center): 同步 Nacos starter 与撤销 dev-secrets 后的文档 (PAP-74)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 7: Linear**

先用 ToolSearch 加载 Linear 工具（`get_issue`、`save_issue`、`save_comment`），读各 Issue 的现有描述后再改。

- **PAP-71**：发一条评论，状态不变：

  ```markdown
  `dev-secrets` namespace 已在 PAP-74 撤销。SCA 让一个应用只连一个 namespace，同时读 `dev` 和 `dev-secrets` 就得自己实现配置加载；改为密钥不进 Nacos，配置里写 `${变量}`，值留在 `~/.patra/secrets/`。mini 上已删除 `dev-secrets`，`nacos-init.sh` 已撤掉 `patra-app` 在它上面的读权限，`nacos-verify.sh --expect-services` 10 项全部通过。设计见 `docs/patra/specs/2026-10-13-nacos-config-starter-design.md` 第 4 节。
  ```

- **PAP-74**：描述里的 To-Do 和 Acceptance Criteria 两节替换为下面的内容，Overview 去掉 `patra-secrets.yaml`、`dev-secrets` 相关的话，并发一条评论贴证据。证据包括：
  - 集成测试 9 个通过的命令与结果；
  - mini 验收输出的 10 行 PASS（不含任何凭据）；
  - 各提交 SHA。

  To-Do 全部勾上：

  ```markdown
  ## To-Do
  - [x] 新建 `patra-spring-boot-starter-nacos`：注入连接与服务发现默认值、读本机密钥文件、没有 `nacos:` 导入时关闭配置中心
  - [x] 启动器 `PatraApplication.run(服务名, X.class, args)`：服务名、两条导入、namespace、`preference=remote`、签到开关放进 default properties
  - [x] 签到检查：Nacos 配置缺签到行时启动失败并说明原因
  - [x] 集成测试（Testcontainers Nacos 3.2.4）：读取顺序、覆盖关系、失败场景、刷新、`${变量}` 解析；实测并记录进程级覆盖与 Nacos 的优先级
  - [x] 撤销 `dev-secrets`：改 `nacos-init.sh`、`nacos-verify.sh`，mini 上删 namespace 并跑检查
  - [x] 同步 release spec、PAP-71 设计、infra 文档

  ## Acceptance Criteria
  - [x] 集成测试：服务按顺序读到两个 dataId，Nacos 的值覆盖 jar 默认值
  - [x] Nacos 不可达且无快照、或配置不存在时，启动失败并给出明确错误
  - [x] 引入 starter 的服务只改启动类，配置文件里不写 Nacos 配置；测试不连 Nacos 配置中心
  - [x] `dev-secrets` 已撤销，mini 上 Nacos 检查全部通过
  ```

  PAP-74 状态：代码还要随 v0.9 后端 PR 合入，置为 In Review 还是 Done，问用户。

- **PAP-75**：描述里去掉 `patra-secrets.yaml`，To-Do 加两条：

  ```markdown
  - [ ] 每份推送的文件必须带签到行：`patra-common.yaml` 写 `patra.config-source.common`，服务文件写 `patra.config-source.service`，值为文件名；推送前逐个检查，缺了就拒绝推送
  - [ ] 仓库里的配置文件不能有真正的密钥，只能写 `${变量}`（公开的 dev 默认值可以直接写或 `${变量:默认值}`）
  ```

- **PAP-76、PAP-77**：描述里去掉 `patra-secrets.yaml`、`dev-secrets`，To-Do 加：

  ```markdown
  - [ ] 启动类改用 `PatraApplication.run("服务名", X.class, args)`；`application.yml` 删掉 `spring.application.name`、原有的 Nacos 配置段和 `~/.patra/secrets` 导入
  - [ ] 测试里需要服务名的，在测试 profile 里设 `spring.application.name`
  - [ ] convention plugin `linqibin.hexagonal-boot` 把直接依赖的 nacos-discovery 换成 `patra-spring-boot-starter-nacos`（PAP-76 做，PAP-77 沿用）
  - [ ] 真正的密钥在 Nacos 配置里写成 `${变量}`，compose 的 env_file 继续加载 `~/.patra/secrets/` 下对应的文件
  - [ ] 第一个服务上 mini 时观察：容器里写不了快照时，nacos-client 会不会刷错误日志（PAP-74 设计第 9 节第 5 条）
  ```
