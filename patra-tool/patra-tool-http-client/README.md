# patra-tool-http-client

## 能力

给服务提供对外与服务间的 HTTP 调用能力。`HttpClientAutoConfiguration` 提供 `defaultRestClient`、`longRunningRestClient` 与请求日志拦截器；`HttpClientProxyAutoConfiguration` 接入青果隧道代理；`HttpClientStreamingAutoConfiguration` 提供 WebClient 流式下载；`HttpClientDownloadAutoConfiguration` 提供 `DownloadClient` 与 FTP/HTTP 下载器；`HttpClientInterfaceAutoConfiguration` 支撑服务间 `@HttpExchange` 调用（`RestClient.Builder`、`RestClientFactory`、下游 `ProblemDetail` 处理）。

## 配置（前缀 `patra.http-client`）

`http-interface` 对应 record 组件 `httpInterface`（`interface` 是 Java 关键字）。

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关 |
| `rest.timeout.connect` | Duration | `10s` | 连接超时 |
| `rest.timeout.read` | Duration | `30s` | 读超时 |
| `rest.timeout.write` | Duration | `30s` | 写超时 |
| `rest.logging.enabled` | boolean | `true` | 请求日志拦截器开关 |
| `rest.logging.log-headers` | boolean | `false` | 是否打请求头 |
| `rest.logging.log-body` | boolean | `false` | 是否打请求体 |
| `rest.logging.max-body-log-length` | int | `1024` | 请求体最多打多少字符，最小 0 |
| `rest.clients.<name>.enabled` | boolean | `true` | 具名客户端开关（`default`、`long-running`、`pubmed` 等） |
| `rest.clients.<name>.base-url` | String | 无 | 具名客户端基址 |
| `rest.clients.<name>.default-headers.<header>` | Map<String, String> | 空 | 具名客户端默认请求头 |
| `rest.clients.<name>.timeout.connect` | Duration | 无 | 该客户端自己的连接超时；整个 `timeout` 不配则用 `rest.timeout`，配了则未写的子键取 `10s` |
| `rest.clients.<name>.timeout.read` | Duration | 无 | 该客户端自己的读超时；子键默认 `30s`，规则同上 |
| `rest.clients.<name>.timeout.write` | Duration | 无 | 该客户端自己的写超时；子键默认 `30s`，规则同上 |
| `rest.streaming.enabled` | boolean | `true` | WebClient 流式下载开关 |
| `proxy.tunnel.enabled` | boolean | `false` | 隧道代理（青果）开关 |
| `proxy.tunnel.host` | String | 无 | 代理主机 |
| `proxy.tunnel.port` | int | `0` | 代理端口 |
| `proxy.tunnel.auth-key` | String | 无 | 认证 key |
| `proxy.tunnel.auth-pwd` | String | 无 | 认证密码 |
| `download.enabled` | boolean | `true` | 流式下载开关 |
| `download.base-dir` | Path | 无（取系统临时目录下的 `patra-downloads`） | 默认落盘目录 |
| `download.temp-dir` | Path | 无（取系统临时目录） | 临时目录 |
| `download.write-strategy` | WriteStrategy | `OVERWRITE` | 目标已存在时怎么办 |
| `download.create-dirs` | boolean | `true` | 是否自动建目录 |
| `download.cleanup-on-failure` | boolean | `true` | 失败时是否删掉半成品 |
| `download.buffer-size` | int | `65536` | 读写缓冲区字节数，最小 1024 |
| `download.retry.enabled` | boolean | `true` | 下载重试开关 |
| `download.retry.max-attempts` | int | `3` | 最多尝试次数，最小 1 |
| `download.retry.initial-backoff` | Duration | `2s` | 首次退避 |
| `download.retry.max-backoff` | Duration | `30s` | 最大退避 |
| `download.ftp.enabled` | boolean | `true` | FTP 下载开关 |
| `download.ftp.username` | String | 无 | 用户名 |
| `download.ftp.password` | String | 无 | 密码 |
| `download.ftp.connect-timeout` | Duration | `30s` | 连接超时 |
| `download.ftp.data-timeout` | Duration | `30m` | 数据超时 |
| `download.ftp.passive-mode` | boolean | `true` | 是否被动模式 |
| `download.ftp.default-content-type` | String | `application/xml` | 没有扩展名时的默认内容类型 |
| `http-interface.enabled` | boolean | `true` | 服务间调用开关（`@HttpExchange` + `lb://`） |
| `http-interface.connect-timeout` | Duration | `5s` | 默认连接超时 |
| `http-interface.read-timeout` | Duration | `30s` | 默认读超时 |
| `http-interface.connection-pool.max-conn-total` | int | `100` | 连接总数上限，最小 1 |
| `http-interface.connection-pool.max-conn-per-route` | int | `20` | 每路由连接上限，最小 1 |
| `http-interface.connection-pool.validate-after-inactivity` | Duration | `5s` | 空闲多久后先验证再用 |
| `http-interface.connection-pool.evict-idle-connections-after` | Duration | `30s` | 空闲多久后回收 |
| `http-interface.error-handling.problem-detail-enabled` | boolean | `true` | 是否解析下游的 ProblemDetail |
| `http-interface.error-handling.tolerant` | boolean | `true` | 解析不了时是否包成通用远程异常而不是直接抛 |
| `http-interface.error-handling.max-error-body-size` | int | `65536` | 最多读多少字节错误体，最小 0 |
| `http-interface.groups.<group>.base-url` | String | 无 | 下游服务组基址（如 `lb://patra-registry`；组名如 `registry`、`storage`） |
| `http-interface.groups.<group>.connect-timeout` | Duration | 无 | 该组自己的连接超时，空则用 `http-interface.connect-timeout` |
| `http-interface.groups.<group>.read-timeout` | Duration | 无 | 该组自己的读超时，空则用 `http-interface.read-timeout` |

## 怎么覆盖

```yaml
patra:
  http-client:
    rest:
      clients:
        pubmed:
          base-url: https://eutils.ncbi.nlm.nih.gov
    http-interface:
      groups:
        registry:
          base-url: lb://patra-registry
          read-timeout: 10s
```

## 出哪些默认值

无。
