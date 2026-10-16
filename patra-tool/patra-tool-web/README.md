# patra-tool-web

## 能力

给 Servlet Web 服务提供统一的错误响应和 JSON 输出。`WebErrorAutoConfiguration` 注册错误解析引擎、解析管道、ProblemDetail 构建器和全局异常处理器（RFC 7807）；`WebJsonAutoConfiguration` 让 MVC 响应里的包装类型 `Long` 输出为字符串。`@WebMvcTest` 切片经 `META-INF/spring/org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc.imports` 自动带上这两个自动配置。服务通过实现 `dev.patra.tool.web.error.spi.ErrorMappingContributor` 把自己的领域异常映射成错误码。

## 配置（前缀 `patra.web`）

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关 |
| `error.enabled` | boolean | `true` | 关掉后不注册引擎与全局处理器 |
| `error.context-prefix` | String | 无 | 错误码前缀（CAT、REG…），空则用 UNKNOWN |
| `error.type-base-url` | String | `https://errors.example.com/` | ProblemDetail `type` 的基址 |
| `error.include-stack` | boolean | `false` | 响应里是否带堆栈 |
| `error.engine.max-cause-depth` | int | `10` | 遍历 cause 链的最大深度 |
| `error.engine.enable-trait-mapping` | boolean | `true` | 是否按 ErrorTrait 语义映射 |
| `error.engine.enable-naming-heuristic` | boolean | `true` | 是否按异常类名启发式映射 |
| `error.observation.enabled` | boolean | `true` | 慢解析观测开关 |
| `error.observation.slow-threshold-ms` | long | `200` | 超过多少毫秒算慢 |
| `error.observation.log-slow-resolution` | boolean | `true` | 慢解析是否打 WARN |
| `error.trace.header-names` | List\<String\> | `traceId`、`X-B3-TraceId`、`traceparent` | 追踪 ID 从 MDC 的哪些键取，按顺序取第一个非空的 |

## 怎么覆盖

```yaml
patra:
  web:
    error:
      context-prefix: CAT
      include-stack: true
```

## 出哪些默认值

无。
