package dev.patra.tool.httpclient.autoconfigure;

import dev.patra.tool.httpclient.download.WriteStrategy;
import jakarta.validation.constraints.Min;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// HTTP 客户端配置，前缀 `patra.http-client`。
///
/// @param enabled 总开关
/// @param rest 外部站点用的 `RestClient`
/// @param proxy 出站代理
/// @param download 流式下载
/// @param httpInterface 服务间调用（`@HttpExchange` + `lb://`）；键名 `http-interface`
@Validated
@ConfigurationProperties(prefix = "patra.http-client")
public record HttpClientProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue Rest rest,
    @DefaultValue Proxy proxy,
    @DefaultValue Download download,
    @DefaultValue HttpInterface httpInterface) {

  /// 外部站点客户端。
  ///
  /// @param timeout 默认超时
  /// @param logging 请求日志拦截器
  /// @param clients 具名客户端（`default`、`long-running`…）
  /// @param streaming WebClient 流式下载开关
  public record Rest(
      @DefaultValue Timeout timeout,
      @DefaultValue Logging logging,
      @DefaultValue Map<String, Client> clients,
      @DefaultValue Streaming streaming) {
    public Rest {
      clients = Map.copyOf(clients);
    }

    /// 超时。
    ///
    /// @param connect 连接超时
    /// @param read 读超时
    /// @param write 写超时
    public record Timeout(
        @DefaultValue("10s") Duration connect,
        @DefaultValue("30s") Duration read,
        @DefaultValue("30s") Duration write) {}

    /// 请求日志拦截器。
    ///
    /// @param enabled 开关
    /// @param logHeaders 是否打请求头
    /// @param logBody 是否打请求体
    /// @param maxBodyLogLength 请求体最多打多少字符
    public record Logging(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean logHeaders,
        @DefaultValue("false") boolean logBody,
        @DefaultValue("1024") @Min(0) int maxBodyLogLength) {}

    /// 具名客户端（`long-running`、`pubmed`…）。
    ///
    /// @param enabled 开关
    /// @param baseUrl 基址
    /// @param defaultHeaders 默认请求头
    /// @param timeout 该客户端自己的超时；空则用默认超时
    public record Client(
        @DefaultValue("true") boolean enabled,
        String baseUrl,
        @DefaultValue Map<String, String> defaultHeaders,
        Timeout timeout) {
      public Client {
        defaultHeaders = Map.copyOf(defaultHeaders);
      }
    }

    /// WebClient 流式下载。
    ///
    /// @param enabled 开关
    public record Streaming(@DefaultValue("true") boolean enabled) {}
  }

  /// 出站代理。
  ///
  /// @param tunnel 隧道代理
  public record Proxy(@DefaultValue Tunnel tunnel) {

    /// 隧道代理（青果）。
    ///
    /// @param enabled 开关
    /// @param host 代理主机
    /// @param port 代理端口
    /// @param authKey 认证 key
    /// @param authPwd 认证密码
    public record Tunnel(
        @DefaultValue("false") boolean enabled,
        String host,
        @DefaultValue("0") int port,
        String authKey,
        String authPwd) {}
  }

  /// 流式下载。
  ///
  /// @param enabled 开关
  /// @param baseDir 默认落盘目录；空则用系统临时目录下的 `patra-downloads`
  /// @param tempDir 临时目录；空则用系统临时目录
  /// @param writeStrategy 目标已存在时怎么办
  /// @param createDirs 是否自动建目录
  /// @param cleanupOnFailure 失败时是否删掉半成品
  /// @param bufferSize 读写缓冲区字节数
  /// @param retry 下载重试
  /// @param ftp FTP 下载
  public record Download(
      @DefaultValue("true") boolean enabled,
      Path baseDir,
      Path tempDir,
      @DefaultValue("OVERWRITE") WriteStrategy writeStrategy,
      @DefaultValue("true") boolean createDirs,
      @DefaultValue("true") boolean cleanupOnFailure,
      @DefaultValue("65536") @Min(1024) int bufferSize,
      @DefaultValue Retry retry,
      @DefaultValue Ftp ftp) {
    public Download {
      Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
      baseDir = baseDir != null ? baseDir : tmp.resolve("patra-downloads");
      tempDir = tempDir != null ? tempDir : tmp;
    }

    /// 下载重试。
    ///
    /// @param enabled 开关
    /// @param maxAttempts 最多尝试次数
    /// @param initialBackoff 首次退避
    /// @param maxBackoff 最大退避
    public record Retry(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("3") @Min(1) int maxAttempts,
        @DefaultValue("2s") Duration initialBackoff,
        @DefaultValue("30s") Duration maxBackoff) {}

    /// FTP 下载。
    ///
    /// @param enabled 开关
    /// @param username 用户名
    /// @param password 密码
    /// @param connectTimeout 连接超时
    /// @param dataTimeout 数据超时
    /// @param passiveMode 是否被动模式
    /// @param defaultContentType 没有扩展名时的默认内容类型
    public record Ftp(
        @DefaultValue("true") boolean enabled,
        String username,
        String password,
        @DefaultValue("30s") Duration connectTimeout,
        @DefaultValue("30m") Duration dataTimeout,
        @DefaultValue("true") boolean passiveMode,
        @DefaultValue("application/xml") String defaultContentType) {}
  }

  /// 服务间调用。
  ///
  /// @param enabled 开关
  /// @param connectTimeout 默认连接超时
  /// @param readTimeout 默认读超时
  /// @param connectionPool Apache HttpClient 连接池
  /// @param errorHandling 下游 ProblemDetail 的处理
  /// @param groups 一组组下游服务（`registry`、`storage`）
  public record HttpInterface(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("5s") Duration connectTimeout,
      @DefaultValue("30s") Duration readTimeout,
      @DefaultValue ConnectionPool connectionPool,
      @DefaultValue ErrorHandling errorHandling,
      @DefaultValue Map<String, Group> groups) {
    public HttpInterface {
      groups = Map.copyOf(groups);
    }

    /// Apache HttpClient 连接池。
    ///
    /// @param maxConnTotal 连接总数上限
    /// @param maxConnPerRoute 每路由连接上限
    /// @param validateAfterInactivity 空闲多久后先验证再用
    /// @param evictIdleConnectionsAfter 空闲多久后回收
    public record ConnectionPool(
        @DefaultValue("100") @Min(1) int maxConnTotal,
        @DefaultValue("20") @Min(1) int maxConnPerRoute,
        @DefaultValue("5s") Duration validateAfterInactivity,
        @DefaultValue("30s") Duration evictIdleConnectionsAfter) {}

    /// 下游 ProblemDetail 的处理。
    ///
    /// @param problemDetailEnabled 是否解析下游的 ProblemDetail
    /// @param tolerant 解析不了时是否包成通用远程异常而不是直接抛
    /// @param maxErrorBodySize 最多读多少字节错误体
    public record ErrorHandling(
        @DefaultValue("true") boolean problemDetailEnabled,
        @DefaultValue("true") boolean tolerant,
        @DefaultValue("65536") @Min(0) int maxErrorBodySize) {}

    /// 一组下游服务（`registry`、`storage`）。
    ///
    /// @param baseUrl 基址（`lb://patra-registry`）
    /// @param connectTimeout 该组自己的连接超时；空则用默认
    /// @param readTimeout 该组自己的读超时；空则用默认
    public record Group(String baseUrl, Duration connectTimeout, Duration readTimeout) {}
  }
}
