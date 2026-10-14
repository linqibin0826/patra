# Nacos 配置文件目录与推送实施计划（PAP-75）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 `patra-infra/nacos/dev/` 配置目录（7 个骨架文件），用 starter 里的 Java 测试检查文件内容；编写幂等的全量同步脚本 `nacos-push.sh`（发布、跳过未变化、删除多余、`--check`）；接入路由与 CD，合入 main 后在部署前自动推送。

**Architecture:** 配置检查是 starter `src/test` 里的 `NacosConfigFileChecker` + `NacosConfigFilesTest`，读仓库真实目录，随 `foundation` 单元在 CI 跑。推送脚本是纯 bash（复用 `nacos-lib.sh`），由 starter `src/integrationTest` 里的 `NacosPushScriptIT` 对 Testcontainers 起的 Nacos 3.2.4 驱动真实脚本来测。`detect-changes.sh` 新增 `nacos_changed` 输出，`cd.yml` 新增 mini 上的 `nacos-push` job，排在 `build-deploy` 之前。

**Tech Stack:** Java 25、JUnit 5 + AssertJ、SnakeYAML 2.5（testCompileClasspath 已有）、json-path 2.10（integrationTestCompileClasspath 已有）、Testcontainers、Nacos 服务端 3.2.4、bash 3.2、curl、jq（`--rawfile` 需要 1.6+）、GitHub Actions。

**Spec:** `docs/patra/specs/2026-10-13-nacos-config-push-design.md`

## 写计划时实测的 Nacos 3.2.4 接口行为（2026-10-13，临时容器）

| 调用 | 结果 |
|------|------|
| `GET /v3/admin/cs/config`，配置不存在 | HTTP 404，`code` 20004 |
| `GET /v3/admin/cs/config`，存在 | HTTP 200，`code` 0，正文在 `.data.content` |
| `POST /v3/admin/cs/config`，表单 `content@文件`（curl `--data-urlencode name@file`） | 读回与文件逐字节一致（中文、`${X:1}`、末尾换行都保留） |
| `GET /v3/admin/cs/config/list`，`namespaceId=dev`、`groupName=DEFAULT_GROUP`、`dataId=`、`pageNo`、`pageSize`、`search=accurate` | `.data.pageItems[].dataId`、`.data.pagesAvailable`；不同分组不会混进来 |
| `GET /v3/admin/cs/history/list`，`namespaceId`、`groupName`、`dataId`、`pageNo`、`pageSize` | `.data.totalCount` 是历史条数 |
| `DELETE /v3/admin/cs/config`，存在或不存在 | 都是 HTTP 200，`code` 0 |
| 推送同款权限（`dev:DEFAULT_GROUP:config/*` rw + `dev:*:config/*` r） | 读、列、写、删、历史都通过；写 `OTHER` 分组 HTTP 403 |
| 登录密码错误 | HTTP 403，无 `code`；地址不通时 `nacos_api` 给 HTTP 000 |

## 与 spec 的差异（写计划时改定，Task 6 同步回 spec）

1. **敏感键判定改成「以…结尾」**，不是「包含」。按「包含」，`token-ttl: 30m`、`max-token-length` 这类普通键会被误判为敏感键。判定方法：键路径最后一段去掉 `[n]`、`-`、`_` 并转小写后，以 `password`、`passwd`、`secret`、`token`、`credential`、`credentials`、`privatekey`、`apikey`、`accesskey`、`secretkey` 之一结尾。
2. **敏感键允许空值**：值为 `null` 或空串也通过（没有泄露任何东西，比如 dev 的 Redis 不设密码）。数字、布尔等字面量照样不通过。
3. **YAML 解析拒绝重复键**（SnakeYAML `setAllowDuplicateKeys(false)`）：Spring 读到重复键时会静默取后一个，写错了很难发现。
4. **拒绝 CRLF 换行**：脚本用 `grep -x` 匹配签到行，行尾的 `\r` 会让匹配失败，而 Java 的 `String.lines()` 不会。两边的判定标准要一致，所以 Java 检查直接拒绝含 `\r` 的文件。
5. **系统属性改成 `patra.repo-root`，值是相对路径 `../..`**（相对于模块目录，也就是 Gradle 测试的工作目录），不传绝对路径。绝对路径会进构建缓存键，换台机器缓存就失效；相对路径在哪台机器上都一样。`test` 和 `integrationTest` 共用这个属性。
6. **集成测试多一个用例：地址连不上**。预期报「连不上 Nacos」并以退出码 1 结束，见 Review Focus 第 1 条。

## Global Constraints

- 目录 `patra-infra/nacos/dev/`；文件名即 dataId；全部推到 `dev` / `DEFAULT_GROUP`；只认 `*.yaml`，不建子目录。
- 7 个文件：`patra-common.yaml`、`patra-registry.yaml`、`patra-ingest.yaml`、`patra-catalog.yaml`、`patra-object-storage.yaml`、`patra-identity.yaml`、`patra-gateway.yaml`。
- 签到行写成一行扁平键：`patra.config-source.common: patra-common.yaml`；服务文件写 `patra.config-source.service: <文件名>`。
- 推送账号 `NACOS_PUSH_USERNAME` / `NACOS_PUSH_PASSWORD`，环境变量优先，否则读 `$NACOS_SECRETS_DIR/nacos-push.env`；地址 `NACOS_ADDR`，默认 `http://127.0.0.1:8848`。
- 写请求一律带 `groupName=DEFAULT_GROUP`；删除按 dataId 逐个删，不用 import / batch 接口；成败看 `code` 为 0（登录看 HTTP 200 加 `accessToken`）；令牌只放请求头，正文只走表单请求体。
- 脚本输出：
  - 每个 dataId 一行：`<操作> <dataId>`，操作是 `新增`、`更新`、`删除`、`未变化` 之一；
  - 最后一行：`新增 a，更新 b，删除 c，未变化 d`；
  - `--check` 模式在开头多打一行 `只对比，不写入（--check）`；
  - 退出码：0 成功（`--check` 表示一致），2 表示 `--check` 发现差异，1 表示出错。
- 不打印密码、令牌、响应体。
- bash 3.2：中文紧跟变量时一律写 `${VAR}`（`$VAR，` 会把全角字符并进变量名，触发 unbound variable）；空数组用 `${ARR[@]+"${ARR[@]}"}`。
- 代码规范：
  - Google Java Style；所有方法写 `///` Javadoc（Markdown 语法）；不用全类名；每行不超过 100 列；gjf 会折长 `///` 行。
  - 提交前跑 `./gradlew :patra-starters:patra-spring-boot-starter-nacos:spotlessApply`。
  - 不要对 `Path` 调用 `getFileName()`（SpotBugs 报 NP），文件名用 `directory.relativize(file).toString()`。
- 测试：
  - 单元测试叫 `{类}Test`，集成测试叫 `{场景}IT`；方法名用 snake_case，`@DisplayName` 写中文；技术栈是 JUnit 5 + AssertJ。
  - TDD 一次只写一条测试：先看它失败，再写最少的实现。
  - 某条测试在现有实现下直接通过时，做一次变异确认：临时改坏实现，看它变红，再撤回。把这次变异记进 ledger。
- 提交：
  - subject 用中文动词开头，格式 `<type>(config-center): … (PAP-75)`，结尾带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
  - 只 `git add` 本计划涉及的路径，提交前看 `git diff --cached --stat`。不推送。
  - 命令文本里不出现字面量 `git push`。
- 安全：仓库是公开的；测试密码运行时随机生成；不在终端打印密钥值。
- 动 mini 的每一步（包括推送到 mini 的 Nacos）先征得用户确认。改了 `*.gradle.kts` 后，提醒用户在 IDEA 里点「加载 Gradle 更改」。
- 跑 Java 命令前，`zsh` 里不 `source nacos-lib.sh`（`local path` 会改坏 zsh 的 PATH）；手动跑脚本时一律用 `bash`。

## Review Focus

spec 没写、但最可能坑到人的 5 种输入。每条都已在所属任务里加了测试：

1. **Nacos 地址写错或连不上**。预期报「连不上 Nacos：<地址>」并以退出码 1 结束，不能挂住，也不能报「登录被拒」误导排查方向。测试：Task 2「地址连不上」。
2. **同一文件里有重复键**。预期检查不通过，并指出是哪个键。测试：Task 1「重复键」。
3. **以 token 开头、却不以 token 结尾的普通键**（如 `token-ttl: 30m`）。预期不算敏感键。测试：Task 1「token-ttl 不算敏感键」。
4. **用 Windows 编辑器保存、带 CRLF 换行的文件**。预期 Java 检查不通过，并提示换行符问题。测试：Task 1「CRLF」。
5. **`--check` 有差异时的状态**。预期 Nacos 里一个字节都没变：没有新增、更新、删除。测试：Task 4「`--check` 有差异」（三类差异都断言 Nacos 未变）。

---

### Task 1: 配置目录与配置检查

**Files:**
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/test/java/dev/linqibin/patra/starter/nacos/NacosConfigFileChecker.java`
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/test/java/dev/linqibin/patra/starter/nacos/NacosConfigFilesTest.java`
- Create: `patra-infra/nacos/dev/patra-common.yaml` 及 6 个服务文件
- Modify: `patra-starters/patra-spring-boot-starter-nacos/build.gradle.kts`

**Interfaces:**
- Consumes: `ConfigSourceCheckPostProcessor.COMMON_PROPERTY`、`SERVICE_PROPERTY`（包内可见）。
- Produces:
  - `NacosConfigFileChecker.check(Path directory)`，返回 `List<String>`，每条违规一行，以文件名开头；
  - 常量 `NacosConfigFileChecker.COMMON_DATA_ID = "patra-common.yaml"`；
  - 系统属性 `patra.repo-root`（`test` 任务），Task 2 给 `integrationTest` 也加上。

- [ ] **Step 0: Linear 置 In Progress**

用 Linear MCP 把 PAP-75 置为 `In Progress`。

- [ ] **Step 1: 写第一条失败测试（合规目录通过）**

`NacosConfigFilesTest.java`：

```java
package dev.linqibin.patra.starter.nacos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// NacosConfigFileChecker 单元测试：要推送到 Nacos 的配置文件的格式与安全检查（spec 第 4 节）。
@DisplayName("NacosConfigFileChecker 单元测试")
class NacosConfigFilesTest {

  @TempDir Path directory;

  @Test
  @DisplayName("签到正确、敏感键只写引用的目录通过")
  void should_pass_when_files_follow_rules() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\n");
    write(
        "patra-ingest.yaml",
        """
        patra.config-source.service: patra-ingest.yaml
        spring:
          datasource:
            password: ${INGEST_DB_PASSWORD}
        """);

    assertThat(NacosConfigFileChecker.check(directory)).isEmpty();
  }

  /// 在临时目录里写一份 UTF-8 文件。
  ///
  /// @param name 文件名
  /// @param content 文件内容
  /// @throws IOException 写入失败时
  private void write(String name, String content) throws IOException {
    Files.writeString(directory.resolve(name), content, StandardCharsets.UTF_8);
  }
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:test --tests "*NacosConfigFilesTest*"`
Expected: 编译失败，报 `cannot find symbol: NacosConfigFileChecker`。

- [ ] **Step 3: 写最小实现**

`NacosConfigFileChecker.java`：

```java
package dev.linqibin.patra.starter.nacos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/// 检查 `patra-infra/nacos/<namespace>/` 下要推送到 Nacos 的配置文件（spec 第 4 节）。
///
/// 只在测试里用：CI 经 [NacosConfigFilesTest] 检查仓库里的真实目录，坏配置合不进 main。
final class NacosConfigFileChecker {

  /// 公共配置的 dataId。
  static final String COMMON_DATA_ID = "patra-common.yaml";

  /// 不实例化。
  private NacosConfigFileChecker() {}

  /// 检查目录下所有 `*.yaml`。
  ///
  /// @param directory 配置目录，目录名即 namespace
  /// @return 违反规则的说明，每条以文件名开头；全部通过时为空
  static List<String> check(Path directory) {
    List<String> violations = new ArrayList<>();
    for (Path file : listYamlFiles(directory)) {
      violations.addAll(checkFile(directory.relativize(file).toString(), file));
    }
    return violations;
  }

  /// 检查一份文件。
  ///
  /// @param name 文件名（即 dataId）
  /// @param file 文件路径
  /// @return 这份文件的违规说明
  private static List<String> checkFile(String name, Path file) {
    return List.of();
  }

  /// 列出目录下以 `.yaml` 结尾的普通文件，按文件名排序。
  ///
  /// @param directory 配置目录
  /// @return 排好序的文件
  private static List<Path> listYamlFiles(Path directory) {
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> file.toString().endsWith(".yaml"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("列出配置目录失败：" + directory, e);
    }
  }
}
```

- [ ] **Step 4: 运行，确认通过**

Run: 同 Step 2。
Expected: PASS 1/1。

- [ ] **Step 5: 加「解析失败」测试，看它失败**

```java
  @Test
  @DisplayName("YAML 解析失败时不通过，报出行号")
  void should_report_parse_error() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\nbad: [\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .singleElement()
        .asString()
        .startsWith("patra-common.yaml：YAML 解析失败");
  }
```

Run: 同 Step 2。Expected: FAIL，结果是空列表。

- [ ] **Step 6: 实现解析**

`checkFile` 改为：

```java
  private static List<String> checkFile(String name, Path file) {
    String text = read(file);
    try {
      newYaml().load(text);
    } catch (MarkedYAMLException e) {
      return List.of(name + "：YAML 解析失败，第 " + line(e) + " 行：" + e.getProblem());
    }
    return List.of();
  }

  /// 出错位置的行号（从 1 开始）；没有位置信息时为 `?`。
  ///
  /// @param e 解析异常
  /// @return 行号
  private static String line(MarkedYAMLException e) {
    return e.getProblemMark() == null ? "?" : String.valueOf(e.getProblemMark().getLine() + 1);
  }

  /// 只构造基础类型、拒绝重复键的解析器。
  ///
  /// @return 新的解析器（`Yaml` 不是线程安全的，每次新建）
  private static Yaml newYaml() {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    return new Yaml(new SafeConstructor(options));
  }

  /// 按 UTF-8 读出文件内容。
  ///
  /// @param file 文件
  /// @return 内容
  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("读取配置文件失败：" + file, e);
    }
  }
```

新增 import：`java.nio.charset.StandardCharsets`、`org.yaml.snakeyaml.LoaderOptions`、`org.yaml.snakeyaml.Yaml`、`org.yaml.snakeyaml.constructor.SafeConstructor`、`org.yaml.snakeyaml.error.MarkedYAMLException`。报错只写 `getProblem()`（例如 `found duplicate key password`），不写 `toString()`：后者会带出原文片段，可能把密钥值打出来。

Run: 同 Step 2。Expected: PASS 2/2。

- [ ] **Step 7: 加「重复键」测试（Review Focus 2），看它的状态**

```java
  @Test
  @DisplayName("重复键不通过：Spring 会静默取后一个")
  void should_reject_duplicate_keys() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\na: 1\na: 2\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .singleElement()
        .asString()
        .contains("YAML 解析失败", "duplicate key");
  }
```

Run: 同 Step 2。Expected：PASS。Step 6 已经设置了 `setAllowDuplicateKeys(false)`，所以这条直接通过。做一次变异确认：临时把这行改成 `true`，看测试变红，再撤回。把这次变异记进 ledger。

- [ ] **Step 8: 加「顶层不是 map」测试，看它失败**

```java
  @Test
  @DisplayName("顶层不是 map 时不通过")
  void should_reject_non_map_root() throws IOException {
    write("patra-common.yaml", "- a\n- b\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly("patra-common.yaml：顶层必须是键值对（map）");
  }
```

Run: 同 Step 2。Expected: FAIL，结果是空列表。

- [ ] **Step 9: 实现**

```java
    Object root;
    try {
      root = newYaml().load(text);
    } catch (MarkedYAMLException e) {
      return List.of(name + "：YAML 解析失败，第 " + line(e) + " 行：" + e.getProblem());
    }
    if (!(root instanceof Map<?, ?> map)) {
      return List.of(name + "：顶层必须是键值对（map）");
    }
    return List.of();
```

新增 import `java.util.Map`。空文件解析结果是 `null`，同样走这条报错。

Run: 同 Step 2。Expected: PASS 4/4。

- [ ] **Step 10: 加「签到值与文件名不一致」测试，看它失败**

```java
  @Test
  @DisplayName("签到值与文件名不一致时不通过")
  void should_reject_signature_value_mismatch() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\n");
    write("patra-ingest.yaml", "patra.config-source.service: patra-catalog.yaml\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .contains("patra-ingest.yaml：签到 patra.config-source.service 的值必须是 patra-ingest.yaml");
  }
```

Run: 同 Step 2。Expected: FAIL。

- [ ] **Step 11: 实现签到值检查**

在 `checkFile` 的 map 检查之后：

```java
    Map<String, Object> flat = new LinkedHashMap<>();
    flatten("", map, flat);
    List<String> violations = new ArrayList<>();
    String property = signatureProperty(name);
    if (!name.equals(flat.get(property))) {
      violations.add(name + "：签到 " + property + " 的值必须是 " + name);
    }
    return violations;
```

新增方法：

```java
  /// 这份文件应写的签到属性：公共配置写 common，其余写 service。
  ///
  /// @param name 文件名
  /// @return 签到属性名
  private static String signatureProperty(String name) {
    return COMMON_DATA_ID.equals(name)
        ? ConfigSourceCheckPostProcessor.COMMON_PROPERTY
        : ConfigSourceCheckPostProcessor.SERVICE_PROPERTY;
  }

  /// 把嵌套的 map 和 list 压平成点分路径到叶子值的映射，列表元素写成 `key[i]`。
  ///
  /// @param prefix 当前路径，顶层为空串
  /// @param node 当前节点
  /// @param out 收集结果
  private static void flatten(String prefix, Object node, Map<String, Object> out) {
    if (node instanceof Map<?, ?> map) {
      map.forEach(
          (key, value) ->
              flatten(prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key, value, out));
    } else if (node instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        flatten(prefix + "[" + i + "]", list.get(i), out);
      }
    } else {
      out.put(prefix, node);
    }
  }
```

新增 import `java.util.LinkedHashMap`。

Run: 同 Step 2。Expected: PASS 5/5。

- [ ] **Step 12: 加「`patra-common.yaml` 用了 service 属性」测试，看它的状态**

```java
  @Test
  @DisplayName("公共配置用了 service 签到属性时不通过")
  void should_reject_common_file_with_service_signature() throws IOException {
    write("patra-common.yaml", "patra.config-source.service: patra-common.yaml\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .contains("patra-common.yaml：签到 patra.config-source.common 的值必须是 patra-common.yaml");
  }
```

Run: 同 Step 2。Expected：PASS（`signatureProperty` 已经区分了两种文件）。做一次变异确认：临时让 `signatureProperty` 恒返回 `SERVICE_PROPERTY`，看测试变红，再撤回。把这次变异记进 ledger。

- [ ] **Step 13: 加「签到写成嵌套形式」测试，看它失败**

```java
  @Test
  @DisplayName("签到写成嵌套形式时不通过：推送脚本只认一行扁平键")
  void should_reject_nested_signature() throws IOException {
    write(
        "patra-common.yaml",
        """
        patra:
          config-source:
            common: patra-common.yaml
        """);

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly(
            "patra-common.yaml：签到必须写成一行扁平键「patra.config-source.common: patra-common.yaml」");
  }
```

Run: 同 Step 2。Expected: FAIL，结果是空列表（压平后的值是对的）。

- [ ] **Step 14: 实现原文行检查**

在签到值检查之后：

```java
    String line = property + ": " + name;
    if (text.lines().noneMatch(line::equals)) {
      violations.add(name + "：签到必须写成一行扁平键「" + line + "」");
    }
```

Run: 同 Step 2。Expected: PASS 7/7（Step 10、12 的用例只断言 `contains`，多出这一条违规不影响它们）。

- [ ] **Step 15: 加「CRLF」测试（Review Focus 4），看它失败**

```java
  @Test
  @DisplayName("CRLF 换行不通过：推送脚本的签到匹配会失败")
  void should_reject_crlf_line_endings() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\r\na: 1\r\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly("patra-common.yaml：换行符必须是 LF，不能有 CR（\\r）");
  }
```

Run: 同 Step 2。Expected: FAIL，结果是空列表。

- [ ] **Step 16: 实现**

在 `checkFile` 读出 `text` 之后、解析之前：

```java
    if (text.indexOf('\r') >= 0) {
      return List.of(name + "：换行符必须是 LF，不能有 CR（\\r）");
    }
```

Run: 同 Step 2。Expected: PASS 8/8。

- [ ] **Step 17: 加「敏感键写字面量」测试，看它失败**

```java
  @Test
  @DisplayName("敏感键写字面量时不通过，不打印值")
  void should_reject_literal_sensitive_value() throws IOException {
    write(
        "patra-common.yaml",
        """
        patra.config-source.common: patra-common.yaml
        spring:
          datasource:
            password: hunter2
        """);

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly(
            "patra-common.yaml：敏感键 spring.datasource.password 只能写成 ${变量} 或 ${变量:默认值}");
  }
```

Run: 同 Step 2。Expected: FAIL，结果是空列表。

- [ ] **Step 18: 实现敏感键检查**

在原文行检查之后：

```java
    flat.forEach(
        (key, value) -> {
          if (isSensitive(key) && !isReference(value)) {
            violations.add(name + "：敏感键 " + key + " 只能写成 ${变量} 或 ${变量:默认值}");
          }
        });
```

新增：

```java
  /// 敏感键名的结尾（去掉 `-`、`_` 并转小写后比较）。
  private static final List<String> SENSITIVE_SUFFIXES =
      List.of(
          "password",
          "passwd",
          "secret",
          "token",
          "credential",
          "credentials",
          "privatekey",
          "apikey",
          "accesskey",
          "secretkey");

  /// 键路径最后一段（去掉 `[n]`、`-`、`_` 并转小写后）是否以敏感词结尾。
  ///
  /// @param key 点分路径
  /// @return 是敏感键时为 `true`
  private static boolean isSensitive(String key) {
    String last = key.substring(key.lastIndexOf('.') + 1);
    String normalized =
        last.replaceAll("\\[\\d+]", "").replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    return SENSITIVE_SUFFIXES.stream().anyMatch(normalized::endsWith);
  }

  /// 值是否没有泄露内容：`null`、空串，或 `${...}` 形式的引用。
  ///
  /// @param value 叶子值
  /// @return 可以提交时为 `true`
  private static boolean isReference(Object value) {
    if (value == null) {
      return true;
    }
    return value instanceof String text
        && (text.isEmpty() || (text.startsWith("${") && text.endsWith("}")));
  }
```

新增 import `java.util.Locale`。常量放在 `COMMON_DATA_ID` 下面。

Run: 同 Step 2。Expected: PASS 9/9。

- [ ] **Step 19: 再加四条敏感键用例，逐条运行**

每加一条就运行一次。这四条在 Step 18 的实现下都应直接通过：`${...}` 的放行、嵌套路径、结尾匹配已经实现。每条都做一次变异确认，把变异记进 ledger：

```java
  @Test
  @DisplayName("敏感键写 ${变量:默认值} 时通过")
  void should_accept_reference_with_default() throws IOException {
    write(
        "patra-common.yaml",
        "patra.config-source.common: patra-common.yaml\nredis:\n  password: ${REDIS_PASSWORD:dev}\n");

    assertThat(NacosConfigFileChecker.check(directory)).isEmpty();
  }

  @Test
  @DisplayName("数字字面量的敏感键也不通过")
  void should_reject_numeric_sensitive_value() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\napi-key: 123456\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly("patra-common.yaml：敏感键 api-key 只能写成 ${变量} 或 ${变量:默认值}");
  }

  @Test
  @DisplayName("token-ttl 这类不以敏感词结尾的键不算敏感键")
  void should_not_treat_token_ttl_as_sensitive() throws IOException {
    write(
        "patra-common.yaml",
        "patra.config-source.common: patra-common.yaml\nsession:\n  token-ttl: 30m\n");

    assertThat(NacosConfigFileChecker.check(directory)).isEmpty();
  }

  @Test
  @DisplayName("敏感键为空值时通过：没有泄露内容")
  void should_accept_empty_sensitive_value() throws IOException {
    write(
        "patra-common.yaml",
        "patra.config-source.common: patra-common.yaml\nredis:\n  password:\n  secret: ''\n");

    assertThat(NacosConfigFileChecker.check(directory)).isEmpty();
  }
```

变异确认的做法：
- 「`${变量:默认值}` 通过」：临时把 `isReference` 改成只接受不带冒号的 `${X}`，看测试变红。
- 「数字字面量」：临时把 `isReference` 改成对非字符串返回 `true`，看测试变红。
- 「token-ttl」：临时把 `endsWith` 改成 `contains`，看测试变红。
- 「空值」：临时去掉 `value == null` 分支，看测试变红。

每次都撤回。

Run: 同 Step 2。Expected: PASS 13/13。

- [ ] **Step 20: 加「缺 `patra-common.yaml`」测试，看它失败**

```java
  @Test
  @DisplayName("缺公共配置时不通过")
  void should_require_common_file() throws IOException {
    write("patra-ingest.yaml", "patra.config-source.service: patra-ingest.yaml\n");

    assertThat(NacosConfigFileChecker.check(directory)).containsExactly("缺少 patra-common.yaml");
  }
```

Run: 同 Step 2。Expected: FAIL。

- [ ] **Step 21: 实现**

`check` 开头：

```java
    List<String> violations = new ArrayList<>();
    if (!Files.isRegularFile(directory.resolve(COMMON_DATA_ID))) {
      violations.add("缺少 " + COMMON_DATA_ID);
    }
```

（删除原来那行 `List<String> violations = new ArrayList<>();`。）

Run: 同 Step 2。Expected: PASS 14/14。

- [ ] **Step 22: 加「仓库真实目录」测试，看它失败**

```java
  @Test
  @DisplayName("仓库里的 patra-infra/nacos/dev 全部通过")
  void should_pass_for_repository_directory() {
    String repoRoot =
        Objects.requireNonNull(
            System.getProperty("patra.repo-root"), "缺系统属性 patra.repo-root（由 Gradle 传入）");
    Path repositoryDirectory = Path.of(repoRoot, "patra-infra", "nacos", "dev");

    assertThat(repositoryDirectory).isDirectory();
    assertThat(NacosConfigFileChecker.check(repositoryDirectory)).isEmpty();
  }
```

新增 import `java.util.Objects`。

Run: 同 Step 2。Expected: FAIL，报 `缺系统属性 patra.repo-root`。

- [ ] **Step 23: 配置 Gradle 的 `test` 任务**

`build.gradle.kts` 末尾追加：

```kotlin
// NacosConfigFilesTest 检查仓库里要推送到 Nacos 的配置文件（PAP-75）。目录声明为输入：只改 yaml 时
// 测试不会命中构建缓存。仓库根用相对路径传入（测试的工作目录是本模块），不进缓存键的机器差异。
val nacosConfigDir = rootProject.layout.projectDirectory.dir("patra-infra/nacos/dev")
val repoRootRelative = projectDir.toPath().relativize(rootDir.toPath()).toString()

tasks.named<Test>("test") {
    inputs.dir(nacosConfigDir)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("nacosConfigFiles")
    systemProperty("patra.repo-root", repoRootRelative)
}
```

Run: 同 Step 2。Expected：Gradle 报 `nacosConfigFiles` 输入目录不存在（目录还没有建），或者测试 FAIL 在 `isDirectory`。两种都可以接受，看到其中之一即可。

- [ ] **Step 24: 建 7 个骨架文件**

```bash
D=/Users/linqibin/Projects/Products/patra/patra-infra/nacos/dev
mkdir -p "$D"
printf 'patra.config-source.common: patra-common.yaml\n' > "$D/patra-common.yaml"
for s in registry ingest catalog object-storage identity gateway; do
  printf 'patra.config-source.service: patra-%s.yaml\n' "$s" > "$D/patra-$s.yaml"
done
ls "$D"
```

Run: 同 Step 2。Expected: PASS 15/15。

- [ ] **Step 25: 确认改 yaml 后缓存失效**

先跑一次 `./gradlew :patra-starters:patra-spring-boot-starter-nacos:test --build-cache`。然后在 `patra-ingest.yaml` 末尾临时加一行 `password: x`，再跑同一条命令。

Expected：第二次 `test` 任务没有标 `FROM-CACHE` 或 `UP-TO-DATE`，并且 FAIL 在仓库目录那条用例。撤回这次改动后再跑一次，结果是 PASS。

- [ ] **Step 26: 格式化、检查、提交**

```bash
./gradlew :patra-starters:patra-spring-boot-starter-nacos:spotlessApply :patra-starters:patra-spring-boot-starter-nacos:check
git add patra-infra/nacos/dev/*.yaml patra-starters/patra-spring-boot-starter-nacos/build.gradle.kts patra-starters/patra-spring-boot-starter-nacos/src/test/java/dev/linqibin/patra/starter/nacos/NacosConfigFileChecker.java patra-starters/patra-spring-boot-starter-nacos/src/test/java/dev/linqibin/patra/starter/nacos/NacosConfigFilesTest.java
git diff --cached --stat
git commit -m "feat(config-center): 新增 Nacos 配置目录与文件检查 (PAP-75)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected：check 通过（含 SpotBugs），提交成功。提醒用户：改了 `build.gradle.kts`，需要在 IDEA 里点「加载 Gradle 更改」。

---

### Task 2: 推送脚本——预检、登录、发布、跳过未变化

**Files:**
- Create: `patra-infra/scripts/nacos-push.sh`
- Create: `patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosPushScriptIT.java`
- Modify: `patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosServerFixture.java`
- Modify: `patra-starters/patra-spring-boot-starter-nacos/build.gradle.kts`

**Interfaces:**
- Consumes: `patra.repo-root`（Task 1）；`nacos-lib.sh` 的 `nacos_load`、`nacos_api`、`nacos_ok`、`nacos_code`、`nacos_login`、`NACOS_HTTP`、`NACOS_BODY`、`NACOS_TOKEN`。
- Produces（Task 3、4 在此基础上扩展）：
  - 脚本里的全局变量：`CHECK`、`DIR`、`NAMESPACE`、`FILES`（数组）、`GROUP`、`COMMON`、计数器 `ADDED`、`UPDATED`、`DELETED`、`SAME`。
  - 脚本里的函数：`die MSG`、`report 操作 DATAID`、`preflight`、`login`、`publish_files`。
  - fixture 的方法：
    - `static final String PUSH_USER`；
    - `String createPushAccount()`；
    - `String content(String dataId)`、`String content(String group, String dataId)`：配置不存在时返回 `null`；
    - `int historyCount(String dataId)`；
    - `List<String> dataIds()`；
    - `void clear()`；
    - `void publish(String group, String dataId, String content)`。
  - IT 里的辅助方法：
    - `writeConfig(String dataId, String body)`：自动加签到行；
    - `push(String... args)`；
    - `run(String addr, String password, String... args)`；
    - 返回值类型 `record ScriptResult(int exitCode, String output)`。

- [ ] **Step 1: 给 fixture 加读写方法**

在 `NacosServerFixture` 里：

1. 把 `post` 里拼表单的代码抽成 `private static String encode(Map<String, String> form)`。
2. 把发送请求、带令牌的代码抽成 `private HttpResponse<String> send(HttpRequest.Builder request, String bearer)`：`bearer` 非空时加 `Authorization` 头；`IOException` 包成 `UncheckedIOException`；被中断时恢复中断标志，并抛 `IllegalStateException`。
3. 把 `postExpectingOk` 里的判定抽成 `private static void requireOk(HttpResponse<String> response, String path)`。

然后新增下面这些方法（`NAMESPACE` 用 `PatraApplication.NAMESPACE`）：

```java
  /// 推送账号名，权限与 mini 上的 `patra-push` 相同。
  static final String PUSH_USER = "patra-push-it";

  private static final String PUSH_ROLE = "ROLE_PATRA_PUSH_IT";
  private static final String NAMESPACE = PatraApplication.NAMESPACE;
  private static final String GROUP = "DEFAULT_GROUP";

  /// 建一个与 mini 上 `patra-push` 同款权限的账号（`dev:DEFAULT_GROUP:config/*` 读写，
  /// `dev:*:config/*` 只读），等权限生效后返回密码。
  ///
  /// @return 随机生成的密码
  String createPushAccount() {
    String password = HexFormat.of().formatHex(randomBytes(12));
    postExpectingOk("/v3/auth/user", Map.of("username", PUSH_USER, "password", password));
    postExpectingOk("/v3/auth/role", Map.of("role", PUSH_ROLE, "username", PUSH_USER));
    postExpectingOk(
        "/v3/auth/permission",
        Map.of("role", PUSH_ROLE, "resource", "dev:DEFAULT_GROUP:config/*", "action", "rw"));
    postExpectingOk(
        "/v3/auth/permission",
        Map.of("role", PUSH_ROLE, "resource", "dev:*:config/*", "action", "r"));
    // 权限有缓存，新建的不会立刻生效：等推送账号能列出配置为止
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .until(() -> pushAccountCanList(password));
    return password;
  }

  /// 读 `dev` / `DEFAULT_GROUP` 下一份配置的正文。
  ///
  /// @param dataId 文件名
  /// @return 正文；不存在时为 `null`
  String content(String dataId) {
    return content(GROUP, dataId);
  }

  /// 读 `dev` 下指定分组里一份配置的正文。
  ///
  /// @param group 分组
  /// @param dataId 文件名
  /// @return 正文；不存在时为 `null`
  String content(String group, String dataId) {
    String path = "/v3/admin/cs/config";
    HttpResponse<String> response =
        get(path, Map.of("namespaceId", NAMESPACE, "groupName", group, "dataId", dataId), token);
    if (response.statusCode() == 404) {
      return null;
    }
    requireOk(response, path);
    return JsonPath.read(response.body(), "$.data.content");
  }

  /// `dev` / `DEFAULT_GROUP` 下一份配置的历史条数。
  ///
  /// @param dataId 文件名
  /// @return 历史条数
  int historyCount(String dataId) {
    String path = "/v3/admin/cs/history/list";
    HttpResponse<String> response =
        get(
            path,
            Map.of(
                "namespaceId", NAMESPACE, "groupName", GROUP, "dataId", dataId,
                "pageNo", "1", "pageSize", "100"),
            token);
    requireOk(response, path);
    return JsonPath.read(response.body(), "$.data.totalCount");
  }

  /// `dev` / `DEFAULT_GROUP` 下全部 dataId，按名字排序。
  ///
  /// @return dataId 列表
  List<String> dataIds() {
    String path = "/v3/admin/cs/config/list";
    HttpResponse<String> response =
        get(
            path,
            Map.of(
                "namespaceId", NAMESPACE, "groupName", GROUP, "dataId", "",
                "pageNo", "1", "pageSize", "500", "search", "accurate"),
            token);
    requireOk(response, path);
    List<String> ids = JsonPath.read(response.body(), "$.data.pageItems[*].dataId");
    return ids.stream().sorted().toList();
  }

  /// 删掉 `dev` / `DEFAULT_GROUP` 下全部配置，其他分组不动。
  void clear() {
    String path = "/v3/admin/cs/config";
    for (String dataId : dataIds()) {
      requireOk(
          delete(path, Map.of("namespaceId", NAMESPACE, "groupName", GROUP, "dataId", dataId)),
          path);
    }
  }

  /// 在 `dev` 的指定分组下发布（或覆盖）一份 yaml 配置。
  ///
  /// @param group 分组
  /// @param dataId 文件名
  /// @param content 配置正文
  void publish(String group, String dataId, String content) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("namespaceId", NAMESPACE);
    form.put("groupName", group);
    form.put("dataId", dataId);
    form.put("content", content);
    form.put("type", "yaml");
    postExpectingOk("/v3/admin/cs/config", form);
  }

  /// 推送账号能否登录并列出 `dev` / `DEFAULT_GROUP` 的配置。
  ///
  /// @param password 推送账号密码
  /// @return 能时为 `true`
  private boolean pushAccountCanList(String password) {
    HttpResponse<String> login =
        post("/v3/auth/user/login", Map.of("username", PUSH_USER, "password", password));
    Matcher matcher = ACCESS_TOKEN.matcher(login.body());
    if (login.statusCode() != 200 || !matcher.find()) {
      return false;
    }
    HttpResponse<String> list =
        get(
            "/v3/admin/cs/config/list",
            Map.of(
                "namespaceId", NAMESPACE, "groupName", GROUP, "dataId", "",
                "pageNo", "1", "pageSize", "1", "search", "accurate"),
            matcher.group(1));
    return list.statusCode() == 200 && CODE_OK.matcher(list.body()).find();
  }

  /// 发 GET 请求，查询条件放 URL 上。
  ///
  /// @param path 不含 `/nacos` 前缀的路径
  /// @param query 查询条件
  /// @param bearer 令牌，空串表示不带
  /// @return 响应
  private HttpResponse<String> get(String path, Map<String, String> query, String bearer) {
    return send(HttpRequest.newBuilder(uri(path, query)).GET(), bearer);
  }

  /// 用管理员令牌发 DELETE 请求，条件放 URL 上。
  ///
  /// @param path 不含 `/nacos` 前缀的路径
  /// @param query 条件
  /// @return 响应
  private HttpResponse<String> delete(String path, Map<String, String> query) {
    return send(HttpRequest.newBuilder(uri(path, query)).DELETE(), token);
  }

  /// 拼出带查询串的地址。
  ///
  /// @param path 不含 `/nacos` 前缀的路径
  /// @param query 查询条件
  /// @return 地址
  private URI uri(String path, Map<String, String> query) {
    return URI.create("http://" + serverAddr() + "/nacos" + path + "?" + encode(query));
  }
```

原有的 `publish(String dataId, String content)` 改为 `publish(GROUP, dataId, content)`。

新增 import：`static org.awaitility.Awaitility.await`、`com.jayway.jsonpath.JsonPath`、`java.util.List`。

`historyCount` 的 `Map.of` 有 5 对键值、`dataIds` 的有 7 对，都没有超过 `Map.of` 的 10 对上限。gjf 会把 `Map.of` 的参数改成每行一个，以格式化后的结果为准。

`send` 的超时沿用 `Duration.ofSeconds(15)`，在每个 builder 上设置。

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:compileIntegrationTestJava`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 写第一条失败测试（首次推送）**

`NacosPushScriptIT.java`：

```java
package dev.linqibin.patra.starter.nacos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// `patra-infra/scripts/nacos-push.sh` 集成测试：对真实 Nacos 运行真实脚本（spec 7.2）。
///
/// 脚本用与 mini 上 `patra-push` 同款权限的账号运行；每个用例开始前清空 `dev` /
/// `DEFAULT_GROUP`。
@DisplayName("nacos-push.sh 集成测试")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class NacosPushScriptIT {

  private static final Path SCRIPT =
      Path.of(
          Objects.requireNonNull(
              System.getProperty("patra.repo-root"), "缺系统属性 patra.repo-root（由 Gradle 传入）"),
          "patra-infra",
          "scripts",
          "nacos-push.sh");

  private static NacosServerFixture nacos;
  private static String pushPassword;

  @TempDir Path temp;
  private Path dev;

  @BeforeAll
  static void startNacos() {
    nacos = NacosServerFixture.start();
    pushPassword = nacos.createPushAccount();
  }

  @AfterAll
  static void stopNacos() {
    nacos.close();
  }

  @BeforeEach
  void setUp() throws IOException {
    nacos.clear();
    dev = Files.createDirectories(temp.resolve("dev"));
  }

  @Test
  @DisplayName("首次推送：全部新增，Nacos 里的正文与文件逐字节一致，输出里没有密码")
  void should_publish_all_files_byte_for_byte_on_first_push() throws Exception {
    writeConfig("patra-common.yaml", "shared: 中文\n");
    writeConfig("patra-ingest.yaml", "k: ${X:1}\n\n");

    ScriptResult result = push();

    assertThat(result.exitCode()).as(result.output()).isZero();
    assertThat(result.output())
        .contains("新增 patra-common.yaml", "新增 patra-ingest.yaml", "新增 2，更新 0，删除 0，未变化 0")
        .doesNotContain(pushPassword, "accessToken", "Bearer");
    assertThat(nacos.content("patra-common.yaml")).isEqualTo(read("patra-common.yaml"));
    assertThat(nacos.content("patra-ingest.yaml")).isEqualTo(read("patra-ingest.yaml"));
  }

  /// 在 `dev` 目录写一份配置：第一行是签到，后面接正文。
  ///
  /// @param dataId 文件名
  /// @param body 签到之后的正文
  /// @throws IOException 写入失败时
  private void writeConfig(String dataId, String body) throws IOException {
    String kind = NacosConfigFileChecker.COMMON_DATA_ID.equals(dataId) ? "common" : "service";
    Files.writeString(
        dev.resolve(dataId),
        "patra.config-source." + kind + ": " + dataId + "\n" + body,
        StandardCharsets.UTF_8);
  }

  /// 读 `dev` 目录里一份文件。
  ///
  /// @param dataId 文件名
  /// @return 内容
  /// @throws IOException 读取失败时
  private String read(String dataId) throws IOException {
    return Files.readString(dev.resolve(dataId), StandardCharsets.UTF_8);
  }

  /// 用推送账号对测试 Nacos 运行脚本。
  ///
  /// @param args 脚本参数（目录参数自动加在最后）
  /// @return 退出码和合并后的输出
  /// @throws IOException 启动进程失败时
  /// @throws InterruptedException 等待时被中断
  private ScriptResult push(String... args) throws IOException, InterruptedException {
    return run("http://" + nacos.serverAddr(), pushPassword, args);
  }

  /// 运行脚本。
  ///
  /// @param addr `NACOS_ADDR`
  /// @param password 推送账号密码
  /// @param args 脚本参数（目录参数自动加在最后）
  /// @return 退出码和合并后的输出
  /// @throws IOException 启动进程失败时
  /// @throws InterruptedException 等待时被中断
  private ScriptResult run(String addr, String password, String... args)
      throws IOException, InterruptedException {
    List<String> command = new ArrayList<>(List.of("bash", SCRIPT.toString()));
    command.addAll(List.of(args));
    command.add(dev.toString());
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    Map<String, String> environment = builder.environment();
    environment.put("NACOS_ADDR", addr);
    environment.put("NACOS_PUSH_USERNAME", NacosServerFixture.PUSH_USER);
    environment.put("NACOS_PUSH_PASSWORD", password);
    // 不读本机真实密钥
    environment.put("NACOS_SECRETS_DIR", temp.resolve("no-secrets").toString());
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return ScriptResult.of(process.waitFor(), output);
  }

  /// 脚本的运行结果。
  ///
  /// @param exitCode 退出码
  /// @param output stdout 与 stderr 合并后的输出
  private record ScriptResult(int exitCode, String output) {

    /// 创建结果。
    ///
    /// @param exitCode 退出码
    /// @param output 输出
    /// @return 结果
    static ScriptResult of(int exitCode, String output) {
      return new ScriptResult(exitCode, output);
    }
  }
}
```

`build.gradle.kts` 里给 `integrationTest` 加上系统属性和脚本输入，写在已有的 `tasks.named<Test>("integrationTest")` 块里：

```kotlin
tasks.named<Test>("integrationTest") {
    forkEvery = 1
    // NacosPushScriptIT 运行仓库里的推送脚本：脚本声明为输入，只改脚本时不命中构建缓存
    inputs.files(
        rootProject.layout.projectDirectory.file("patra-infra/scripts/nacos-push.sh"),
        rootProject.layout.projectDirectory.file("patra-infra/scripts/nacos-lib.sh"),
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("nacosPushScripts")
    systemProperty("patra.repo-root", repoRootRelative)
}
```

（`repoRootRelative` 已在 Task 1 定义，放在这个块之前；必要时把 Task 1 追加的两行 `val` 移到文件靠前的位置。）

- [ ] **Step 3: 运行，确认失败**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:integrationTest --tests "*NacosPushScriptIT*"`
Expected: FAIL：Gradle 报输入文件 `nacos-push.sh` 不存在，或者 bash 报找不到脚本（退出码 127）。

- [ ] **Step 4: 写脚本的第一版（预检占位、登录、全部发布）**

`patra-infra/scripts/nacos-push.sh`：

```bash
#!/bin/bash
# 把 patra-infra/nacos/<namespace>/ 下的配置文件同步到 Nacos（PAP-75）。
#
# 用法：nacos-push.sh [--check] [目录]
#   目录      默认是本脚本旁边的 ../nacos/dev；目录名就是 namespace
#   --check   只对比，不写入。退出码 0 表示一致，2 表示有差异，1 表示出错
#
# 目录里的 *.yaml 就是 <namespace> / DEFAULT_GROUP 的全部配置：
#   内容不同或不存在的发布，相同的跳过，Nacos 里有、目录里没有的删除。
#   推送前先做本地预检：必须有 patra-common.yaml，每个文件都有完全匹配的签到行；
#   预检不过时不登录、不写入。
#
# 输入（环境变量）：
#   NACOS_ADDR                              默认 http://127.0.0.1:8848（mini 上的 runner 用默认值）
#   NACOS_PUSH_USERNAME、NACOS_PUSH_PASSWORD 推送账号；没设时读 $NACOS_SECRETS_DIR/nacos-push.env
#
# 设计：docs/patra/specs/2026-10-13-nacos-config-push-design.md
# 兼容 mini 的 bash 3.2；依赖 curl、jq（--rawfile 需要 1.6+）。
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=nacos-lib.sh
source "$SCRIPT_DIR/nacos-lib.sh"

GROUP=DEFAULT_GROUP
COMMON=patra-common.yaml

CHECK=false
if [ "${1:-}" = "--check" ]; then
  CHECK=true
  shift
fi
DIR="${1:-$SCRIPT_DIR/../nacos/dev}"

# nacos_load 用间接展开读变量，set -u 下变量必须先存在
NACOS_PUSH_USERNAME="${NACOS_PUSH_USERNAME:-}"
NACOS_PUSH_PASSWORD="${NACOS_PUSH_PASSWORD:-}"

ADDED=0
UPDATED=0
DELETED=0
SAME=0

# die MSG：报错并以退出码 1 结束。
die() {
  echo "$*" >&2
  exit 1
}

# report 操作 DATAID：打印一行并计数。操作是 新增 / 更新 / 删除 / 未变化。
report() {
  echo "$1 $2"
  case "$1" in
    新增) ADDED=$((ADDED + 1)) ;;
    更新) UPDATED=$((UPDATED + 1)) ;;
    删除) DELETED=$((DELETED + 1)) ;;
    未变化) SAME=$((SAME + 1)) ;;
  esac
}

# preflight：本地预检，不联网。
preflight() {
  [ -d "$DIR" ] || die "配置目录不存在：${DIR}"
}

# login：用推送账号登录，令牌放进 NACOS_TOKEN。
login() {
  nacos_load NACOS_PUSH_USERNAME nacos-push.env || exit 1
  nacos_load NACOS_PUSH_PASSWORD nacos-push.env || exit 1
  nacos_login "$NACOS_PUSH_USERNAME" "$NACOS_PUSH_PASSWORD" || die "登录 Nacos 失败"
}

# publish_files：逐个发布目录里的文件。
publish_files() {
  local file name
  for file in ${FILES[@]+"${FILES[@]}"}; do
    name="$(basename "$file")"
    nacos_api POST /v3/admin/cs/config "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
      "groupName=${GROUP}" "dataId=${name}" type=yaml "content@${file}"
    nacos_ok || die "发布 ${name} 失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    report 新增 "$name"
  done
}

preflight
DIR="$(cd "$DIR" && pwd)"
NAMESPACE="$(basename "$DIR")"
shopt -s nullglob
FILES=("$DIR"/*.yaml)
shopt -u nullglob

login
publish_files
echo "新增 ${ADDED}，更新 ${UPDATED}，删除 ${DELETED}，未变化 ${SAME}"
exit 0
```

（`COMMON`、`CHECK` 在后续步骤使用；在那之前 shellcheck 会报 SC2034，可以先忽略。）

`chmod +x patra-infra/scripts/nacos-push.sh`

- [ ] **Step 5: 运行，确认通过**

Run: 同 Step 3。
Expected: PASS 1/1。

- [ ] **Step 6: 加「改了的文件记为更新」测试，看它失败**

```java
  @Test
  @DisplayName("改一个文件：只更新这一个，其余未变化")
  void should_update_only_changed_file() throws Exception {
    writeConfig("patra-common.yaml", "a: 1\n");
    writeConfig("patra-ingest.yaml", "b: 1\n");
    assertThat(push().exitCode()).isZero();
    writeConfig("patra-ingest.yaml", "b: 2\n");

    ScriptResult result = push();

    assertThat(result.exitCode()).as(result.output()).isZero();
    assertThat(result.output())
        .contains("未变化 patra-common.yaml", "更新 patra-ingest.yaml", "新增 0，更新 1，删除 0，未变化 1");
    assertThat(nacos.content("patra-ingest.yaml")).isEqualTo(read("patra-ingest.yaml"));
  }
```

Run: 同 Step 3。Expected：新用例 FAIL，输出里是「新增 patra-ingest.yaml」。

- [ ] **Step 7: 实现读回比较**

`publish_files` 的循环体改为：

```bash
    name="$(basename "$file")"
    nacos_api GET /v3/admin/cs/config "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
      "groupName=${GROUP}" "dataId=${name}"
    if nacos_ok; then
      # 逐字节比较：不经 shell 变量，命令替换会吞掉末尾换行
      if jq -e --rawfile want "$file" '.data.content == $want' <<<"$NACOS_BODY" >/dev/null; then
        report 未变化 "$name"
        continue
      fi
      action=更新
    elif [ "$NACOS_HTTP" = 404 ] && [ "$(nacos_code)" = 20004 ]; then
      action=新增
    else
      die "读取 ${name} 失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    fi
    nacos_api POST /v3/admin/cs/config "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
      "groupName=${GROUP}" "dataId=${name}" type=yaml "content@${file}"
    nacos_ok || die "发布 ${name} 失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    report "$action" "$name"
```

`local file name` 改为 `local file name action`。

Run: 同 Step 3。Expected: PASS 2/2。

- [ ] **Step 8: 加「再跑一次全部未变化」测试，看它的状态**

```java
  @Test
  @DisplayName("连跑两次：第二次全部未变化，历史条数不变")
  void should_be_idempotent() throws Exception {
    writeConfig("patra-common.yaml", "a: 1\n");
    writeConfig("patra-ingest.yaml", "b: 1\n");
    assertThat(push().exitCode()).isZero();
    int commonHistory = nacos.historyCount("patra-common.yaml");
    int ingestHistory = nacos.historyCount("patra-ingest.yaml");

    ScriptResult result = push();

    assertThat(result.exitCode()).as(result.output()).isZero();
    assertThat(result.output()).contains("新增 0，更新 0，删除 0，未变化 2");
    assertThat(nacos.historyCount("patra-common.yaml")).isEqualTo(commonHistory);
    assertThat(nacos.historyCount("patra-ingest.yaml")).isEqualTo(ingestHistory);
  }
```

Run: 同 Step 3。Expected：PASS（Step 7 已经实现了跳过）。做一次变异确认：临时删掉 `continue` 之前的 `report 未变化` 分支，让相同的文件也走发布，看测试变红（历史条数加 1），再撤回。把这次变异记进 ledger。

- [ ] **Step 9: 加「缺签到行」测试，看它失败**

```java
  @Test
  @DisplayName("有文件缺签到行：拒绝推送，Nacos 里没有任何写入")
  void should_refuse_when_signature_missing() throws Exception {
    writeConfig("patra-common.yaml", "a: 1\n");
    Files.writeString(dev.resolve("patra-ingest.yaml"), "b: 1\n", StandardCharsets.UTF_8);

    ScriptResult result = push();

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output())
        .contains("patra-ingest.yaml 缺少签到行「patra.config-source.service: patra-ingest.yaml」");
    assertThat(nacos.dataIds()).isEmpty();
  }
```

Run: 同 Step 3。Expected：新用例 FAIL，退出码是 0。

- [ ] **Step 10: 实现签到预检**

`preflight` 改为：

```bash
# preflight：本地预检，不联网。不通过时列出所有问题再退出。
preflight() {
  local file name kind bad=0
  [ -d "$DIR" ] || die "配置目录不存在：${DIR}"
  for file in ${FILES[@]+"${FILES[@]}"}; do
    name="$(basename "$file")"
    if [ "$name" = "$COMMON" ]; then kind=common; else kind=service; fi
    if ! grep -qxF "patra.config-source.${kind}: ${name}" "$file"; then
      echo "预检失败：${name} 缺少签到行「patra.config-source.${kind}: ${name}」" >&2
      bad=1
    fi
  done
  [ "$bad" = 0 ] || die "预检没通过，没有登录，也没有写入任何配置"
}
```

主流程改为：先确认目录存在，再规范化路径、收集文件，最后预检：

```bash
[ -d "$DIR" ] || die "配置目录不存在：${DIR}"
DIR="$(cd "$DIR" && pwd)"
NAMESPACE="$(basename "$DIR")"
shopt -s nullglob
FILES=("$DIR"/*.yaml)
shopt -u nullglob

preflight
login
publish_files
```

（`preflight` 里的目录检查保留，作为防御。）

Run: 同 Step 3。Expected: PASS 4/4。

- [ ] **Step 11: 加「缺 `patra-common.yaml`」测试，看它失败**

```java
  @Test
  @DisplayName("缺公共配置：拒绝推送，Nacos 里没有任何写入")
  void should_refuse_without_common_file() throws Exception {
    writeConfig("patra-ingest.yaml", "b: 1\n");

    ScriptResult result = push();

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output()).contains("缺少 patra-common.yaml");
    assertThat(nacos.dataIds()).isEmpty();
  }
```

Run: 同 Step 3。Expected：新用例 FAIL，退出码是 0。

- [ ] **Step 12: 实现**

在 `preflight` 的 `for` 之前加：

```bash
  if [ ! -f "$DIR/$COMMON" ]; then
    echo "预检失败：缺少 ${COMMON}" >&2
    bad=1
  fi
```

Run: 同 Step 3。Expected: PASS 5/5。

- [ ] **Step 13: 加「密码错误」测试，看它失败**

```java
  @Test
  @DisplayName("密码错误：报出账号名，输出里没有密码")
  void should_report_rejected_login_without_leaking_password() throws Exception {
    writeConfig("patra-common.yaml", "a: 1\n");
    String wrong = "wrong-" + System.nanoTime();

    ScriptResult result = run("http://" + nacos.serverAddr(), wrong);

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output())
        .contains("登录被拒：账号 " + NacosServerFixture.PUSH_USER)
        .doesNotContain(wrong);
  }
```

Run: 同 Step 3。Expected：新用例 FAIL，输出是「登录 Nacos 失败」。

- [ ] **Step 14: 实现登录失败的区分**

`login` 的最后一行改为：

```bash
  if ! nacos_login "$NACOS_PUSH_USERNAME" "$NACOS_PUSH_PASSWORD"; then
    [ "$NACOS_HTTP" = 000 ] && die "连不上 Nacos：${NACOS_ADDR}"
    die "登录被拒：账号 ${NACOS_PUSH_USERNAME}（HTTP ${NACOS_HTTP}）"
  fi
```

Run: 同 Step 3。Expected: PASS 6/6。

- [ ] **Step 15: 加「地址连不上」测试（Review Focus 1），看它的状态**

```java
  @Test
  @DisplayName("地址连不上：报出地址，退出码 1")
  void should_report_unreachable_nacos() throws Exception {
    writeConfig("patra-common.yaml", "a: 1\n");

    ScriptResult result = run("http://127.0.0.1:1", pushPassword);

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output()).contains("连不上 Nacos：http://127.0.0.1:1");
  }
```

Run: 同 Step 3。Expected：PASS（Step 14 已经区分了 HTTP 000）。做一次变异确认：临时删掉 `NACOS_HTTP = 000` 那一行，看测试变红，再撤回。把这次变异记进 ledger。

- [ ] **Step 16: shellcheck 与提交**

```bash
shellcheck /Users/linqibin/Projects/Products/patra/patra-infra/scripts/nacos-push.sh
./gradlew :patra-starters:patra-spring-boot-starter-nacos:spotlessApply :patra-starters:patra-spring-boot-starter-nacos:integrationTest --tests "*NacosPushScriptIT*"
git add patra-infra/scripts/nacos-push.sh patra-starters/patra-spring-boot-starter-nacos/build.gradle.kts patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosServerFixture.java patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosPushScriptIT.java
git diff --cached --stat
git commit -m "feat(config-center): 新增 Nacos 配置推送脚本，发布并跳过未变化文件 (PAP-75)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected：
- shellcheck 只有 `CHECK` 未使用的 SC2034 告警（Task 4 用上它），没有其他告警。
- IT 7/7 通过。
- 提醒用户：改了 `build.gradle.kts`，需要在 IDEA 里点「加载 Gradle 更改」。

---

### Task 3: 推送脚本——删除仓库里没有的配置

**Files:**
- Modify: `patra-infra/scripts/nacos-push.sh`
- Modify: `patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosPushScriptIT.java`

**Interfaces:**
- Consumes: Task 2 的 `FILES`、`report`、`die`、fixture 的 `publish(group, dataId, content)`、`content(group, dataId)`、`dataIds()`。
- Produces：脚本里的函数 `remote_data_ids`、`prune`，常量 `PAGE_SIZE=100`，临时文件 `LISTING`（退出时删除）。

- [ ] **Step 1: 写失败测试（删除本地已删的文件，其他分组不动）**

```java
  @Test
  @DisplayName("删一个本地文件：Nacos 里对应的配置被删，其他分组不受影响")
  void should_delete_configs_missing_from_directory() throws Exception {
    writeConfig("patra-common.yaml", "a: 1\n");
    writeConfig("patra-ingest.yaml", "b: 1\n");
    assertThat(push().exitCode()).isZero();
    nacos.publish("OTHER", "keep.yaml", "k: 1\n");
    Files.delete(dev.resolve("patra-ingest.yaml"));

    ScriptResult result = push();

    assertThat(result.exitCode()).as(result.output()).isZero();
    assertThat(result.output()).contains("删除 patra-ingest.yaml", "新增 0，更新 0，删除 1，未变化 1");
    assertThat(nacos.dataIds()).containsExactly("patra-common.yaml");
    assertThat(nacos.content("OTHER", "keep.yaml")).isEqualTo("k: 1\n");
  }
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:integrationTest --tests "*NacosPushScriptIT*"`
Expected：新用例 FAIL，输出里没有「删除 patra-ingest.yaml」。

- [ ] **Step 3: 实现删除**

在常量区加 `PAGE_SIZE=100`，在计数器之后加：

```bash
LISTING="$(mktemp)"
trap 'rm -f "$LISTING"' EXIT
```

新增两个函数，放在 `publish_files` 之后：

```bash
# remote_data_ids：把 NAMESPACE / GROUP 下的全部 dataId 逐行写到 stdout，分页取完。
# 调用方用重定向（不用命令替换），出错时 die 才能结束整个脚本。
remote_data_ids() {
  local page=1 pages
  while :; do
    nacos_api GET /v3/admin/cs/config/list "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
      "groupName=${GROUP}" dataId= "pageNo=${page}" "pageSize=${PAGE_SIZE}" search=accurate
    nacos_ok || die "列出 ${NAMESPACE} / ${GROUP} 的配置失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    jq -r '.data.pageItems[].dataId' <<<"$NACOS_BODY"
    pages="$(jq -r '.data.pagesAvailable' <<<"$NACOS_BODY")"
    [ "$page" -ge "$pages" ] && return 0
    page=$((page + 1))
  done
}

# prune：删除 Nacos 里有、目录里没有的配置。先列完再删，避免边删边翻页漏掉。
prune() {
  local id file names=""
  for file in ${FILES[@]+"${FILES[@]}"}; do
    names="${names}$(basename "$file")"$'\n'
  done
  remote_data_ids >"$LISTING"
  while IFS= read -r id; do
    [ -z "$id" ] && continue
    # 用 here-string 不用管道：pipefail 下 grep -q 提前退出可能让 printf 收到 SIGPIPE，
    # 整条管道判为失败，本地有的配置会被当成没有而误删
    grep -qxF -- "$id" <<<"$names" && continue
    nacos_api DELETE /v3/admin/cs/config "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
      "groupName=${GROUP}" "dataId=${id}"
    nacos_ok || die "删除 ${id} 失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    report 删除 "$id"
  done <"$LISTING"
}
```

主流程在 `publish_files` 之后加 `prune`。

Run: 同 Step 2。Expected: PASS 8/8。

- [ ] **Step 4: 加「超过一页」测试，看它的状态**

```java
  @Test
  @DisplayName("远端超过一页：多余的配置全部删干净")
  void should_delete_across_pages() throws Exception {
    for (int i = 0; i < 105; i++) {
      nacos.publish("DEFAULT_GROUP", "extra-%03d.yaml".formatted(i), "k: " + i + "\n");
    }
    writeConfig("patra-common.yaml", "a: 1\n");

    ScriptResult result = push();

    assertThat(result.exitCode()).as(result.output()).isZero();
    assertThat(result.output()).contains("新增 1，更新 0，删除 105，未变化 0");
    assertThat(nacos.dataIds()).containsExactly("patra-common.yaml");
  }
```

Run: 同 Step 2。Expected：PASS（Step 3 已经实现了分页）。做一次变异确认：临时把 `[ "$page" -ge "$pages" ] && return 0` 改成 `return 0`，也就是只取第一页，看测试变红（只删了 100 个），再撤回。把这次变异记进 ledger。

- [ ] **Step 5: shellcheck 与提交**

```bash
shellcheck /Users/linqibin/Projects/Products/patra/patra-infra/scripts/nacos-push.sh
./gradlew :patra-starters:patra-spring-boot-starter-nacos:spotlessApply
git add patra-infra/scripts/nacos-push.sh patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosPushScriptIT.java
git diff --cached --stat
git commit -m "feat(config-center): 推送时删除仓库里没有的 Nacos 配置 (PAP-75)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected: shellcheck 只剩 `CHECK` 的 SC2034；提交成功。

---

### Task 4: 推送脚本——`--check` 模式

**Files:**
- Modify: `patra-infra/scripts/nacos-push.sh`
- Modify: `patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosPushScriptIT.java`

**Interfaces:**
- Consumes: Task 2、3 的 `CHECK`、`publish_files`、`prune`、计数器。
- Produces：最终版脚本（Task 5 的 CD 和 Task 7 的验收都用它）。

- [ ] **Step 1: 写失败测试（一致时退出码 0）**

```java
  @Test
  @DisplayName("--check：与仓库一致时退出码 0")
  void check_should_exit_zero_when_in_sync() throws Exception {
    writeConfig("patra-common.yaml", "a: 1\n");
    assertThat(push().exitCode()).isZero();

    ScriptResult result = push("--check");

    assertThat(result.exitCode()).as(result.output()).isZero();
    assertThat(result.output())
        .contains("只对比，不写入（--check）", "未变化 patra-common.yaml", "新增 0，更新 0，删除 0，未变化 1");
  }
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :patra-starters:patra-spring-boot-starter-nacos:integrationTest --tests "*NacosPushScriptIT*"`
Expected：新用例 FAIL。此时 `--check` 被当成目录参数前的普通参数被 `shift` 掉了，但输出里没有「只对比，不写入」。

- [ ] **Step 3: 实现提示行**

主流程在 `login` 之后加：

```bash
if [ "$CHECK" = true ]; then
  echo "只对比，不写入（--check）"
fi
```

Run: 同 Step 2。Expected: PASS 10/10。

- [ ] **Step 4: 加「有差异时退出码 2 且不写入」测试（Review Focus 5），看它失败**

```java
  @Test
  @DisplayName("--check：有差异时退出码 2，Nacos 一个字节都不变")
  void check_should_report_differences_without_writing() throws Exception {
    writeConfig("patra-common.yaml", "a: 1\n");
    writeConfig("patra-ingest.yaml", "b: 1\n");
    assertThat(push().exitCode()).isZero();
    nacos.publish("DEFAULT_GROUP", "stale.yaml", "k: 1\n");
    String ingestBefore = nacos.content("patra-ingest.yaml");
    writeConfig("patra-ingest.yaml", "b: 2\n");
    writeConfig("patra-identity.yaml", "c: 1\n");

    ScriptResult result = push("--check");

    assertThat(result.exitCode()).as(result.output()).isEqualTo(2);
    assertThat(result.output())
        .contains(
            "更新 patra-ingest.yaml",
            "新增 patra-identity.yaml",
            "删除 stale.yaml",
            "新增 1，更新 1，删除 1，未变化 1");
    assertThat(nacos.content("patra-ingest.yaml")).isEqualTo(ingestBefore);
    assertThat(nacos.content("patra-identity.yaml")).isNull();
    assertThat(nacos.content("stale.yaml")).isEqualTo("k: 1\n");
  }
```

Run: 同 Step 2。Expected：新用例 FAIL，退出码是 0，Nacos 已被改写。

- [ ] **Step 5: 实现只读与退出码**

`publish_files` 里的发布两行用 `if` 包起来：

```bash
    if [ "$CHECK" = false ]; then
      nacos_api POST /v3/admin/cs/config "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
        "groupName=${GROUP}" "dataId=${name}" type=yaml "content@${file}"
      nacos_ok || die "发布 ${name} 失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    fi
```

`prune` 里的删除两行同样处理：

```bash
    if [ "$CHECK" = false ]; then
      nacos_api DELETE /v3/admin/cs/config "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
        "groupName=${GROUP}" "dataId=${id}"
      nacos_ok || die "删除 ${id} 失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    fi
```

主流程末尾的 `exit 0` 改为：

```bash
if [ "$CHECK" = true ] && [ $((ADDED + UPDATED + DELETED)) -gt 0 ]; then
  exit 2
fi
exit 0
```

Run: 同 Step 2。Expected: PASS 11/11。

- [ ] **Step 6: shellcheck、全量 IT 与提交**

```bash
shellcheck /Users/linqibin/Projects/Products/patra/patra-infra/scripts/nacos-push.sh
./gradlew :patra-starters:patra-spring-boot-starter-nacos:spotlessApply :patra-starters:patra-spring-boot-starter-nacos:check :patra-starters:patra-spring-boot-starter-nacos:integrationTest
git add patra-infra/scripts/nacos-push.sh patra-starters/patra-spring-boot-starter-nacos/src/integrationTest/java/dev/linqibin/patra/starter/nacos/NacosPushScriptIT.java
git diff --cached --stat
git commit -m "feat(config-center): 推送脚本支持 --check 只对比不写入 (PAP-75)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected：
- shellcheck 无告警。
- 单元测试全部通过：PAP-74 的 38 条加 Task 1 的 15 条。
- 集成测试 20/20 通过：PAP-74 的 9 条加本 Issue 的 11 条。

---

### Task 5: 路由、CD 与模块图

**Files:**
- Modify: `patra-infra/cd/detect-changes.sh`
- Modify: `patra-infra/cd/detect-changes.test.sh`
- Modify: `.github/workflows/cd.yml`
- Modify: `patra-infra/cd/module-graph.json`（`./gradlew dumpModuleGraph` 重新生成）

**Interfaces:**
- Consumes：Task 4 的最终版脚本 `patra-infra/scripts/nacos-push.sh`（退出码 0 / 1）。
- Produces：`detect-changes.sh` 的输出多一个布尔字段 `nacos_changed`；`cd.yml` 多一个 job `nacos-push`；`build-deploy` 依赖它。

- [ ] **Step 1: 写失败测试（只改 yaml）**

在 `detect-changes.test.sh` 的场景 13 之后、汇总之前追加：

```bash
echo "== 场景 14：Nacos 配置与推送脚本 → foundation（CI 检查）+ nacos_changed（CD 推送）=="
F="patra-infra/nacos/dev/patra-ingest.yaml"
assert_field "$F" '.nacos_changed' 'true' "nacos yaml 触发推送"
assert_field "$F" '.backend_units' '["foundation"]' "nacos yaml 只跑 foundation"
assert_field "$F" '.full_run' 'false' "nacos yaml 非全量"
```

Run: `bash /Users/linqibin/Projects/Products/patra/patra-infra/cd/detect-changes.test.sh`
Expected：场景 14 的 3 条全部 ✗：`nacos_changed` 实得 `null`，`backend_units` 和 `full_run` 实得全量。

- [ ] **Step 2: 实现字段与路由**

`detect-changes.sh`：

1. 头注释的输出说明加上 `"nacos_changed":bool`，并补一句：`nacos_changed：patra-infra/nacos/*/*.yaml 或推送脚本有改动、或全量时为 true；CD 据此在部署前推送 Nacos 配置`。
2. `LEARN_CHANGED=false` 下面加：

```bash
NACOS_CHANGED=false   # 同上：全局初始化
```

3. `emit` 改为：

```bash
emit() { # $1=units_json $2=portal $3=docs_only $4=full_run $5=coverage_mode（learn / nacos 走全局变量）
  local learn="${LEARN_CHANGED:-false}" nacos="${NACOS_CHANGED:-false}"
  jq -cn --argjson u "$1" --argjson p "$2" --argjson le "$learn" --argjson n "$nacos" --argjson d "$3" --argjson f "$4" --arg c "$5" \
    '{backend_units:$u, portal_changed:$p, learn_changed:$le, nacos_changed:$n, docs_only:$d, full_run:$f, coverage_mode:$c}'
}
emit_full() { NACOS_CHANGED=true; emit "$ALL_UNITS" "${1:-false}" false true full; }
```

4. 在 `classify` 里「纯基建路径」那个 `case` 之前加：

```bash
    # Nacos 配置与推送脚本：CI 跑 starter（foundation）里的配置检查和推送集成测试，
    # CD 在部署前推送（nacos-push job）。foundation 不可部署，不会触发任何服务重建。
    case "$f" in
      patra-infra/nacos/*/*.yaml|patra-infra/scripts/nacos-push.sh|patra-infra/scripts/nacos-lib.sh)
        NACOS_CHANGED=true; docs_all=false; echo foundation >> "$units_file"; continue;;
    esac
```

5. `classify` 末尾的全量分支改为：`if [ "$full" = true ]; then NACOS_CHANGED=true; emit "$ALL_UNITS" "$portal" false true full; return; fi`

Run: 同 Step 1。Expected: 全部 ✓。

- [ ] **Step 3: 补齐场景 14 其余用例，逐条运行**

每加一条就运行一次。这些用例在 Step 2 的实现下都应直接通过。其中「只改 README」「改 nacos-init.sh」「纯服务代码」三条守的是不该触发推送的情况；对这三条各做一次变异确认（临时把规则的 glob 改宽，看测试变红，再撤回），记进 ledger：

```bash
assert_field "patra-infra/nacos/dev/README.md" '.nacos_changed' 'false' "nacos README 不触发推送"
assert_field "patra-infra/nacos/dev/README.md" '.docs_only' 'true' "nacos README 算 docs_only"
assert_field "patra-infra/scripts/nacos-push.sh" '.nacos_changed' 'true' "推送脚本触发推送"
assert_field "patra-infra/scripts/nacos-push.sh" '.backend_units' '["foundation"]' "推送脚本跑 foundation（IT）"
assert_field "patra-infra/scripts/nacos-lib.sh" '.nacos_changed' 'true' "推送脚本依赖的函数库触发推送"
assert_field "patra-infra/scripts/nacos-init.sh" '.nacos_changed' 'false' "nacos-init 不触发推送"
assert_field "patra-infra/scripts/nacos-init.sh" '.backend_units' '[]' "nacos-init 仍是纯基建"
F="$(printf 'patra-infra/nacos/dev/patra-ingest.yaml\npatra-api/patra-catalog/patra-catalog-infra/src/main/java/X.java')"
assert_field "$F" '.nacos_changed' 'true' "yaml + catalog 混合仍推送"
assert_field "$F" '.backend_units' '["catalog","foundation"]' "yaml + catalog 混合两个单元都跑"
assert_field "settings.gradle.kts" '.nacos_changed' 'true' "全量运行也推送"
assert_field "patra-api/patra-catalog/patra-catalog-infra/src/main/java/X.java" '.nacos_changed' 'false' "纯服务代码不推送"
```

再补两条 dispatch 模式的断言。它们不走 `classify`，直接调用脚本：

```bash
got="$(bash "$DETECT" dispatch catalog | jq -c '.nacos_changed')"
if [ "$got" = "false" ]; then PASS=$((PASS+1)); echo "  ✓ dispatch 单服务（回滚）不推送"; else FAIL=$((FAIL+1)); echo "  ✗ dispatch 单服务 nacos_changed 实得 $got"; fi
got="$(bash "$DETECT" dispatch all | jq -c '.nacos_changed')"
if [ "$got" = "true" ]; then PASS=$((PASS+1)); echo "  ✓ dispatch all 推送"; else FAIL=$((FAIL+1)); echo "  ✗ dispatch all nacos_changed 实得 $got"; fi
```

Run: 同 Step 1。Expected：全部 ✓，最后一行 `失败 0`。

- [ ] **Step 4: 改 `cd.yml`**

1. 头注释的链路说明改成三段：

```yaml
# 持续部署（多服务，Mac mini 原生构建）：merge 到 main（或手动触发）
#   → detect-changes（ubuntu）算出改动服务（含「改公共面→重建全部」规则）与是否推送 Nacos 配置
#   → nacos-push（Mac mini runner，仅 nacos_changed）：把 patra-infra/nacos/dev 同步到 Nacos，
#     失败则不部署（新代码可能依赖新配置）；脚本见 patra-infra/scripts/nacos-push.sh
#   → build-deploy（Mac mini runner）：gradlew bootJar → 原生 arm64 docker build
#     → deploy.sh（部署+健康检查+部署后验证+自动回滚）→ GHCR 归档推送（best-effort）
```

2. `on.push.paths` 在 `'patra-infra/cd/**'` 之后加：

```yaml
      - 'patra-infra/nacos/**'
      - 'patra-infra/scripts/nacos-push.sh'
      - 'patra-infra/scripts/nacos-lib.sh'
```

3. `detect-changes` job 的 `outputs` 加 `nacos_changed: ${{ steps.detect.outputs.nacos_changed }}`；`run` 里在 `echo "services=$SERVICES" >> "$GITHUB_OUTPUT"` 之后加：

```bash
          echo "nacos_changed=$(echo "$R" | jq -r '.nacos_changed')" >> "$GITHUB_OUTPUT"
```

4. 在 `build-deploy` 之前加：

```yaml
  # ---- 2) Mac mini 上推送 Nacos 配置（仅配置或推送脚本有改动、或全量时）----
  nacos-push:
    name: nacos-push
    needs: detect-changes
    if: needs.detect-changes.outputs.nacos_changed == 'true'
    runs-on: [self-hosted, macmini]
    timeout-minutes: 5
    steps:
      - name: 📥 Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          persist-credentials: false
      - name: 🧩 推送 Nacos 配置（全量同步；账号从 runner 用户的 ~/.patra/secrets/nacos-push.env 读）
        run: bash patra-infra/scripts/nacos-push.sh
```

5. `build-deploy` 的注释序号改为 `3)`，`needs` 和 `if` 改为：

```yaml
    needs: [detect-changes, nacos-push]
    # 依赖的 job 被跳过时 GitHub 默认也跳过下游，所以要 !cancelled()；推送失败则不部署
    if: >-
      !cancelled() && needs.detect-changes.result == 'success'
      && needs.detect-changes.outputs.services != '[]'
      && (needs.nacos-push.result == 'success' || needs.nacos-push.result == 'skipped')
```

Run：`ruby -e 'require "yaml"; YAML.load_file("/Users/linqibin/Projects/Products/patra/.github/workflows/cd.yml"); puts "ok"'`。如果本机有 actionlint，再跑 `actionlint /Users/linqibin/Projects/Products/patra/.github/workflows/cd.yml`。
Expected：输出 `ok`；actionlint 无报错。

- [ ] **Step 5: 重新生成模块图**

Run: `./gradlew dumpModuleGraph -q && git -C /Users/linqibin/Projects/Products/patra diff --stat patra-infra/cd/module-graph.json && git -C /Users/linqibin/Projects/Products/patra diff patra-infra/cd/module-graph.json | head -40`
Expected：diff 只涉及 nacos starter 那一项，`tasks` 变为 `["check","integrationTest"]`。出现其他差异时先查明原因：是本分支 PAP-74 的改动带来的就一起提交；与本分支无关的，记一条 Ruling 后再决定。

- [ ] **Step 6: 跑路由测试，然后提交**

```bash
bash /Users/linqibin/Projects/Products/patra/patra-infra/cd/detect-changes.test.sh
git add patra-infra/cd/detect-changes.sh patra-infra/cd/detect-changes.test.sh .github/workflows/cd.yml patra-infra/cd/module-graph.json
git diff --cached --stat
git commit -m "ci(config-center): 配置改动时在部署前推送 Nacos，补全模块图 (PAP-75)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected：路由测试 `失败 0`；提交成功。如果 commitlint 不接受 `ci` 类型，改用 `build`，并记一条 Ruling。

---

### Task 6: 文档

**Files:**
- Create: `patra-infra/nacos/dev/README.md`
- Modify: `patra-infra/CLAUDE.md`
- Modify: `patra-infra/scripts/nacos-lib.sh`（只改头注释）
- Modify: `docs/patra/specs/2026-10-13-nacos-config-push-design.md`

**Interfaces:**
- Consumes：Task 1–5 的最终行为。
- Produces：无。

- [ ] **Step 1: 写 `patra-infra/nacos/dev/README.md`**

```markdown
# Nacos 配置（dev namespace）

这个目录就是 Nacos `dev` namespace、`DEFAULT_GROUP` 分组的全部配置，以 git 为准。

- **文件名即 dataId**：`patra-common.yaml` 是公共配置；`{spring.application.name}.yaml` 是各服务的配置。
- **合入 main 后自动推送**：CD 的 `nacos-push` job 在部署前把整个目录同步到 Nacos。内容不同的发布，相同的跳过，Nacos 里有、目录里没有的删除。推送失败时本次不部署。
- **控制台只在应急时用**：在控制台改的内容，下次推送会被覆盖。回滚配置就 git revert 再合入。

## 每份文件的规则

CI 里的 `NacosConfigFilesTest` 会检查这些规则，不通过的合不进 main：

1. 第一行写签到，必须是一行扁平键，值为文件名：
   - `patra-common.yaml` 写 `patra.config-source.common: patra-common.yaml`
   - 服务文件写 `patra.config-source.service: <文件名>`，例如 `patra.config-source.service: patra-ingest.yaml`

   服务启动时，靠这一行确认确实读到了 Nacos 的配置。
2. **不写密钥**：仓库是公开的。键名以 password、secret、token、credential(s)、private-key、api-key、access-key、secret-key 结尾的，值只能写 `${变量}` 或 `${变量:公开的 dev 默认值}`，也可以留空。变量的值本机放在 `~/.patra/secrets/`，容器经 env_file 注入。
3. 不能有重复键；换行符用 LF。

## 本机手动推送

推送用 `patra-push` 账号，凭据从 `~/.patra/secrets/nacos-push.env` 读。先看会改什么，再推：

    NACOS_ADDR=http://100.103.73.27:8848 bash patra-infra/scripts/nacos-push.sh --check
    NACOS_ADDR=http://100.103.73.27:8848 bash patra-infra/scripts/nacos-push.sh

`--check` 的退出码：0 表示一致，2 表示有差异，1 表示出错。在 mini 上直接运行时不用设 `NACOS_ADDR`。

设计：`docs/patra/specs/2026-10-13-nacos-config-push-design.md`
```

（README 里的命令块用缩进写，避免外层计划文档的代码块嵌套出错。实际写文件时可以改成 bash 围栏代码块。）

- [ ] **Step 2: 改 `patra-infra/CLAUDE.md`**

1. 第一段「这里只包含」的列表加上 `Nacos 配置文件（nacos/）`。可执行测试那句补上：`scripts/nacos-push.sh` 由 starter 的 `NacosPushScriptIT` 对真实 Nacos 测试（`./gradlew :patra-starters:patra-spring-boot-starter-nacos:integrationTest`）。
2. 「Self-hosted Runner 与 CD」一节：
   - 「两段 job」改为「三段 job」，在中间加入 `nacos-push`（mini 上，仅 `nacos_changed`；失败则不部署）。
   - 「选择性构建」里补一句：`patra-infra/nacos/*/*.yaml` 与推送脚本只路由到 `foundation`（CI 检查）并置 `nacos_changed`，不重建服务。
3. scripts 表新增一行：

   `| nacos-push.sh [--check] [目录] | Mac mini（CD）/ MacBook | 把 nacos/dev 同步到 Nacos dev / DEFAULT_GROUP：发布、跳过未变化、删除多余；--check 只对比（0 一致 / 2 有差异 / 1 出错） |`

   `nacos-lib.sh` 那一行的「上面两个脚本」改为「nacos-init、nacos-verify、nacos-push」。

- [ ] **Step 3: 改 `nacos-lib.sh` 头注释**

第一行改为：`# nacos-init.sh、nacos-verify.sh、nacos-push.sh 共用的函数。只供 source，不单独执行。`

- [ ] **Step 4: 同步 spec**

改 `docs/patra/specs/2026-10-13-nacos-config-push-design.md`：
- 状态行：`> **状态**：已实现（2026-10-13）`。
- 第 4 节第 3 条：判定方式改成「以…结尾」，词表与 Task 1 Step 18 一致；补上「值为 `null` 或空串也通过」。
- 第 4 节第 1 条后补：「拒绝重复键（SnakeYAML `setAllowDuplicateKeys(false)`）」。
- 第 4 节加第 5 条：「换行符必须是 LF（脚本用 `grep -x` 匹配签到行，CR 会让它失败）」。
- 第 4 节末段：系统属性改为 `patra.repo-root`，值是相对路径 `../..`，并说明原因。
- 第 5.1 节第 3 步：「不存在」实测为 HTTP 404 加 `code` 20004。
- 第 5.3 节：改为实测结果（参数表与本计划开头的表一致）。
- 第 7.2 节：用例里补上「地址连不上」。

- [ ] **Step 5: 提交**

```bash
git add patra-infra/nacos/dev/README.md patra-infra/CLAUDE.md patra-infra/scripts/nacos-lib.sh docs/patra/specs/2026-10-13-nacos-config-push-design.md
git diff --cached --stat
git commit -m "docs(config-center): 补充 Nacos 配置目录与推送说明 (PAP-75)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected：提交成功。改 `nacos-lib.sh` 会让 `NacosPushScriptIT` 的输入变化，Task 7 会重跑集成测试。

---

### Task 7: 全量验证与 mini 验收

**Files:** 无代码改动。

**Interfaces:**
- Consumes：全部任务的产物。
- Produces：验收记录，写在 ledger 和最终消息里。

- [ ] **Step 1: 本地全量**

```bash
./gradlew :patra-starters:patra-spring-boot-starter-nacos:check :patra-starters:patra-spring-boot-starter-nacos:integrationTest
bash /Users/linqibin/Projects/Products/patra/patra-infra/cd/detect-changes.test.sh
shellcheck /Users/linqibin/Projects/Products/patra/patra-infra/scripts/nacos-push.sh
```

Expected：单元测试 53 条、集成测试 20 条全部通过；路由测试 `失败 0`；shellcheck 无告警。

- [ ] **Step 2: 停下，征得用户同意后再动 mini**

向用户说明接下来四步会对 mini 的 Nacos 做什么：先 `--check`，再推送（新增 7），再推一次（未变化 7），最后 `--check`。还要说明：如果第一次 `--check` 里出现「删除」，就先停下来给用户看，不继续推送。同时确认 mini 的 jq 版本不低于 1.6：

```bash
ssh linqibin@linqibins-mac-mini 'PATH=/opt/homebrew/bin:/usr/local/bin:$PATH jq --version'
```

只有用户明确同意后，才执行 Step 3。

- [ ] **Step 3: 对 mini 验收**

```bash
NACOS_ADDR=http://100.103.73.27:8848 bash /Users/linqibin/Projects/Products/patra/patra-infra/scripts/nacos-push.sh --check; echo "exit=$?"
```

Expected：7 行「新增」，`exit=2`。如果出现任何「删除」行，就停下，把清单给用户看，不继续执行。

```bash
NACOS_ADDR=http://100.103.73.27:8848 bash /Users/linqibin/Projects/Products/patra/patra-infra/scripts/nacos-push.sh; echo "exit=$?"
NACOS_ADDR=http://100.103.73.27:8848 bash /Users/linqibin/Projects/Products/patra/patra-infra/scripts/nacos-push.sh; echo "exit=$?"
NACOS_ADDR=http://100.103.73.27:8848 bash /Users/linqibin/Projects/Products/patra/patra-infra/scripts/nacos-push.sh --check; echo "exit=$?"
```

Expected：
- 第一次推送：`新增 7，更新 0，删除 0，未变化 0`，`exit=0`。
- 第二次推送：`新增 0，更新 0，删除 0，未变化 7`，`exit=0`。
- 最后的 `--check`：`exit=0`。

人工核对这四次的输出里没有密码和令牌。

如果 mini 还连不上（tailscale 路由被抢，见 memory），先按 `docs/mac-mini-connectivity.md` 排查，或者让用户决定改在 mini 上执行。

- [ ] **Step 4: 收尾**

- 走 executing-plans 的最终审查（换一个最强模型的 reviewer 审整条分支），修完再进入下一步。
- 问用户：是否把 PAP-75 置为 `Review`（和 PAP-74 一样）。
- 「合入 main 后由 CD 自动推送」这条要等版本后端 PR 合入后才能验证：看 Actions 里 `nacos-push` 是否成功、日志里有没有凭据，再在本机对 mini 跑一次 `--check`，预期退出码 0。把这条写进最终消息，作为待合入后验证的事项。
