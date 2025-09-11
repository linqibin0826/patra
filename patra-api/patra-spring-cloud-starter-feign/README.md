# patra-spring-cloud-starter-feign

统一 Feign 错误处理与上下文透传：
- Provider 侧抛 `PatraHttpException`（web starter 负责输出 application/problem+json）。
- Consumer 侧由本模块将非 2xx 响应解析为 `PlatformError` 并抛出 `PatraFeignException`，供业务按 `code/status/category` 分支。

> 兼容性：旧的 `PatraRemoteException` 已标记为废弃并继承 `PatraFeignException`，旧代码仍可运行，但建议尽快迁移。

## 特性
- 全局 `RequestInterceptor`���自动设置 `Accept: application/problem+json`，透传 `X-Service`、`X-TraceId` 与白名单请求头。
- 全局 `ErrorDecoder`：优先解析 `problem+json`；非规范响应回退为平台通用 COM 错误；统一抛 `PatraFeignException`。
- 可选 `WebAdvice`：在网关/BFF 将 `PatraFeignException` 原样输出为 `application/problem+json`（默认关闭）。

## 开启方式
- 仅需引入依赖，默认启用：`patra.feign.enabled=true`。
- OpenFeign 会自动装配拦截器与错误解码器（按 Spring Bean 发现机制）。

## 常用配置（application.yml）
```yaml
patra:
  feign:
    enabled: true
    accept-problem-json: true
    max-error-body-size: 65536
    strict-problem-media-type: false
    attach-response-headers-to-extras: true
    trace-id-header: X-TraceId
    service-header: X-Service
    propagate-headers:
      - X-Request-Id
      - X-Tenant-Id
    redact-keys:
      - token
      - password
      - secret
      - apiKey
    web-advice-enabled: false
```

## 消费者用法
- 捕获 `PatraFeignException`：
  - `ex.error().code().toString()` 获取错误码（如 `COM-N0601`）。
  - `ex.status()` 获取建议/实际 HTTP 状态。
  - `ex.error().extras()` 获取扩展上下文。

## 说明
- Provider 侧无需改动，延续使用 `PatraHttpException` 抛错。
- 本模块与 `patra-spring-boot-starter-core` 协同工作，依赖其中的 `PlatformErrorCodec`、`PlatformErrorFactory`、`COMErrors` 等。

## 版本
- since 0.1.0
