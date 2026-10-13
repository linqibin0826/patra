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

  private static final String COMMON_DATA_ID = "patra-common.yaml";

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

  @Test
  @DisplayName("缺公共配置：拒绝推送，Nacos 里没有任何写入")
  void should_refuse_without_common_file() throws Exception {
    writeConfig("patra-ingest.yaml", "b: 1\n");

    ScriptResult result = push();

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output()).contains("缺少 patra-common.yaml");
    assertThat(nacos.dataIds()).isEmpty();
  }

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

  @Test
  @DisplayName("地址连不上：报出地址，退出码 1")
  void should_report_unreachable_nacos() throws Exception {
    writeConfig("patra-common.yaml", "a: 1\n");

    ScriptResult result = run("http://127.0.0.1:1", pushPassword);

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output()).contains("连不上 Nacos：http://127.0.0.1:1");
  }

  /// 在 `dev` 目录写一份配置：第一行是签到，后面接正文。
  ///
  /// @param dataId 文件名
  /// @param body 签到之后的正文
  /// @throws IOException 写入失败时
  private void writeConfig(String dataId, String body) throws IOException {
    String kind = COMMON_DATA_ID.equals(dataId) ? "common" : "service";
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
