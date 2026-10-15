package dev.patra.tool.launch;

import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
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
import java.util.List;
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

  /// 推送账号名，权限与 mini 上的 `patra-push` 相同。
  static final String PUSH_USER = "patra-push-it";

  private static final String PUSH_ROLE = "ROLE_PATRA_PUSH_IT";
  private static final String NAMESPACE = PatraApplication.NAMESPACE;
  private static final String GROUP = "DEFAULT_GROUP";

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
    publish(GROUP, dataId, content);
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
                "namespaceId",
                NAMESPACE,
                "groupName",
                GROUP,
                "dataId",
                dataId,
                "pageNo",
                "1",
                "pageSize",
                "100"),
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
                "namespaceId",
                NAMESPACE,
                "groupName",
                GROUP,
                "dataId",
                "",
                "pageNo",
                "1",
                "pageSize",
                "500",
                "search",
                "accurate"),
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
            "namespaceId", NAMESPACE,
            "namespaceName", NAMESPACE));
  }

  /// 发 POST 表单请求，要求 HTTP 200 且响应 `code` 为 0。失败时只报路径和状态码，不报响应体。
  ///
  /// @param path 不含 `/nacos` 前缀的路径
  /// @param form 表单字段
  private void postExpectingOk(String path, Map<String, String> form) {
    requireOk(post(path, form), path);
  }

  /// 要求 HTTP 200 且响应 `code` 为 0。失败时只报路径和状态码，不报响应体。
  ///
  /// @param response 响应
  /// @param path 请求路径，用于报错
  private static void requireOk(HttpResponse<String> response, String path) {
    if (response.statusCode() != 200 || !CODE_OK.matcher(response.body()).find()) {
      throw new IllegalStateException("Nacos 接口失败：" + path + " HTTP " + response.statusCode());
    }
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
                "namespaceId",
                NAMESPACE,
                "groupName",
                GROUP,
                "dataId",
                "",
                "pageNo",
                "1",
                "pageSize",
                "1",
                "search",
                "accurate"),
            matcher.group(1));
    return list.statusCode() == 200 && CODE_OK.matcher(list.body()).find();
  }

  /// 发 POST 表单请求，带上管理员令牌。
  ///
  /// @param path 不含 `/nacos` 前缀的路径
  /// @param form 表单字段
  /// @return 响应
  private HttpResponse<String> post(String path, Map<String, String> form) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://" + serverAddr() + "/nacos" + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encode(form)));
    return send(request, token);
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

  /// 把键值对编码成 `application/x-www-form-urlencoded` 格式（表单和查询串通用）。
  ///
  /// @param form 键值对
  /// @return 编码后的字符串
  private static String encode(Map<String, String> form) {
    return form.entrySet().stream()
        .map(
            entry ->
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  /// 发送请求：设 15 秒超时，`bearer` 非空时带上令牌。
  ///
  /// @param request 请求
  /// @param bearer 令牌，空串表示不带
  /// @return 响应
  private HttpResponse<String> send(HttpRequest.Builder request, String bearer) {
    request.timeout(Duration.ofSeconds(15));
    if (!bearer.isEmpty()) {
      request.header("Authorization", "Bearer " + bearer);
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
