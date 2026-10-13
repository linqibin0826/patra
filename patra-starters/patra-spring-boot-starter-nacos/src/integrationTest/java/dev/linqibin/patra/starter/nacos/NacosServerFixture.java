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
