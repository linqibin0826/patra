## 模块：patra-spring-cloud-starter-feign

为服务间调用（Feign）提供：

1. 下游 `application/problem+json` → 结构化异常 `RemoteCallException`
2. 宽容(tolerant)模式：非 ProblemDetail / JSON 解析失败时仍封装 `RemoteCallException`
3. TraceId 透传（请求）与回填（响应头 → ProblemDetail）
4. 观测指标（解析耗时 / 响应体读取 / 解码结果 / TraceId 提取）
5. 语义化错误辅助工具 `RemoteErrorHelper`
6. 可配置读取体截断、脱敏 key、服务名透传

---

## 1. 快速开始

```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-spring-boot-starter-core</artifactId>
</dependency>
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-spring-cloud-starter-feign</artifactId>
</dependency>
```

YAML：
```yaml
patra:
  feign:
    enabled: true
  feign:
    problem:
      enabled: true
      tolerant: true
      max-error-body-size: 65536
      include-stack-trace: false
      observation:
        enabled: true
        slow-parsing-threshold-ms: 150
        slow-body-reading-threshold-ms: 80
```

使用：直接注入 FeignClient，捕获 `RemoteCallException`：
```java
try {
  return client.getResource(id);
} catch (RemoteCallException ex) {
  if (RemoteErrorHelper.isNotFound(ex)) return null; // 或 Optional.empty()
  if (RemoteErrorHelper.isConflict(ex)) handleConflict();
  throw ex; // 其余上抛
}
```

---

## 2. 自动装配与条件

`FeignErrorAutoConfiguration` 条件：
| 条件 | 说明 |
|------|------|
| Class 存在 | `feign.Feign`, `feign.codec.ErrorDecoder`, `org.springframework.http.ProblemDetail` |
| Property | `patra.feign.problem.enabled=true`（默认启用） |

注册 Bean：
| Bean | 说明 |
|------|------|
| `ProblemDetailErrorDecoder` | 解析下游 ProblemDetail / 宽容兜底 |
| `FeignErrorObservationRecorder` | Micrometer 指标或 NO_OP |
| `TraceIdRequestInterceptor` | 将当前 TraceId 写入第一个配置的 header |
| `PatraFeignRequestInterceptor` | 写入调用方服务名等通用头 |

---

## 3. 配置属性

### 3.1 `patra.feign`（`PatraFeignProperties`）
| 属性 | 默认 | 说明 |
|------|------|------|
| enabled | true | 模块开关（关闭后全部拦截/解码不生效） |
| max-error-body-size | 65536 | 兜底最大错误体读取字节（未直接显式使用可按需补充使用） |
| service-header | X-Service-Name | 写入调用方服务名请求头 |
| redact-keys | token,password,secret,apiKey | 需要脱敏匹配（包含匹配，大小写不敏感） |

### 3.2 `patra.feign.problem`（`FeignErrorProperties`）
| 属性 | 默认 | 说明 |
|------|------|------|
| enabled | true | 是否启用 ProblemDetail 解码 |
| tolerant | true | 宽容模式开关（失败/非 problem+json 仍返回 RemoteCallException） |
| max-error-body-size | 65536 | 单次读取最大字节，上限+1 判定截断 |
| include-stack-trace | false | 是否将远端堆栈透出（强烈不建议生产开启） |
| observation.enabled | true | 观测指标开关 |
| observation.slow-parsing-threshold-ms | 150 | 解析慢阈值 WARN |
| observation.log-slow-parsing | true | 是否记录解析慢日志 |
| observation.slow-body-reading-threshold-ms | 80 | 响应体读取慢阈值 |
| observation.log-slow-body-reading | true | 是否记录读取慢日志 |
| observation.log-tolerant-usage | true | 宽容模式触发是否记录 INFO |

---

## 4. 解码流程

1. Feign 调用返回非 2xx → 进入 `ProblemDetailErrorDecoder#decode`
2. Content-Type 判断是否 `application/problem+json`
3. 是：读取响应体（截断保护）→ JSON 反序列化 → 成功则封装 `RemoteCallException(problemDetail)`
4. 解析失败或非 problem+json：
   - 宽容模式：读取（必要时）响应体，构造消息摘要（优先 reasonPhrase / 截断 body）→ `RemoteCallException(status,message,methodKey,traceId)`
   - 严格模式：回退 `FeignException`
5. 记录指标（解析耗时 / 读取耗时 / 是否宽容 / traceId 提取）
6. 返回异常进入调用方 catch 分支

TraceId 提取顺序：`traceId` → `X-B3-TraceId` → `traceparent` → `X-Trace-Id`。

---

## 5. `RemoteCallException` 结构

| 字段 | 说明 |
|------|------|
| errorCode | 下游 ProblemDetail 扩展字段 `code`（可能为空） |
| httpStatus | 下游 HTTP 状态 |
| methodKey | Feign 方法标识（接口#方法(签名)） |
| traceId | 响应头或 ProblemDetail 中的链路 ID |
| extensions | 所有 ProblemDetail.properties 拷贝 |

辅助方法：`hasErrorCode() / hasTraceId() / getExtension(key,Class)`。

---

## 6. `RemoteErrorHelper`

| 方法 | 语义 |
|------|------|
| isNotFound | 404 或 错误码 *-0404 |
| isConflict | 409 或 错误码 *-0409 |
| isUnauthorized | 401 或 *-0401 |
| isForbidden | 403 或 *-0403 |
| isUnprocessableEntity | 422 或 *-0422 |
| isTooManyRequests | 429 或 *-0429 |
| isClientError | 4xx |
| isServerError | 5xx |
| is(ex,code) | 精确匹配业务错误码 |
| isAnyOf(ex,codes...) | 多码匹配 |
| hasErrorCode | 是否存在业务错误码 |
| hasTraceId | 是否存在 traceId |
| isRetryable | 5xx / 429 / 408 / 503 / 504 |

> 已去除 README 旧版中未在源码出现的帮助方法（如 isQuotaExceeded / isTimeout），保持与实现一致。

---

## 7. 使用模式示例

```java
public NamespaceDto find(String id) {
  try {
    return client.getNamespace(id);
  } catch (RemoteCallException ex) {
    if (RemoteErrorHelper.isNotFound(ex)) {
      throw new NamespaceMissingException(id); // 或返回 null
    }
    if (RemoteErrorHelper.isRetryable(ex)) {
      // 交给上层重试 / 断路器
      throw new TransientRemoteException(ex);
    }
    throw ex; // 其他交由统一异常策略
  }
}
```

配合 Resilience4j：
```java
@CircuitBreaker(name="registry")
@Retry(name="registry")
public NamespaceDto guarded(String id) {
  try { return client.getNamespace(id); }
  catch (RemoteCallException ex) {
    if (RemoteErrorHelper.isNotFound(ex)) return null;
    throw ex; // 交由断路器记录失败或重试器策略
  }
}
```

---

## 8. 观测指标 (Micrometer)

| 指标名 | 标签 | 描述 |
|--------|------|------|
| papertrace.feign.error.parsing | method,status,success | ProblemDetail 解析耗时 Timer |
| papertrace.feign.error.decoding | method,status,success,tolerant | 解码结果计数 |
| papertrace.feign.error.body.read | method,truncated | 响应体读取耗时 Timer |
| papertrace.feign.error.traceid | method,found,header | TraceId 抽取计数 |

慢日志：
| 触发 | 条件 |
|------|------|
| 解析慢 | duration >= slow-parsing-threshold-ms 且 log-slow-parsing=true |
| 读取慢 | duration >= slow-body-reading-threshold-ms 且 log-slow-body-reading=true |
| 宽容模式 | tolerant=true 且 log-tolerant-usage=true（INFO） |

---

## 9. 性能与安全

| 关注点 | 策略 |
|--------|------|
| 大响应体 | 限制 max-error-body-size + 截断标记 |
| JSON 解析失败 | 宽容模式回退短消息，避免级联异常 |
| 敏感字段 | 通过 redact-keys 统一过滤（需在上层使用时应用） |
| Trace 丢失 | 多头部回退顺序 + no-op trace provider 时安全降级 |
| 指标开销 | 未启用或无 MeterRegistry → NO_OP |

---

## 10. 常见排障

| 现象 | 排查步骤 |
|------|----------|
| 仍抛出 FeignException | 检查 tolerant=false 或 content-type 是否正确 problem+json |
| 没有指标 | `patra.feign.problem.observation.enabled` 是否为 true 且存在 MeterRegistry |
| traceId 为空 | 核心 trace provider 是否配置；响应头中是否存在任何支持的 key |
| errorCode 为空 | 下游 ProblemDetail 是否包含扩展字段 code |

调试日志：
```yaml
logging:
  level:
    com.patra.starter.feign.error: DEBUG
    feign.Logger: DEBUG
```

---

## 11. Roadmap

| 优先级 | 项目 | 描述 |
|--------|------|------|
| High | 错误码统计指标 | 按业务错误码分 bucket 计数 |
| High | 可配置 traceId 头添加策略 | 多头部同步写入选项 |
| Mid | 响应体采样存档 | 仅在特定错误码时采样日志（脱敏） |
| Mid | Retry 策略集成 | 基于 isRetryable 自动分类标签 |
| Low | OpenTelemetry 集成 | 附加 span event 记录解析耗时 |

---

## 12. 与 Web Starter 差异

| 维度 | Feign Starter | Web Starter |
|------|---------------|-------------|
| 方向 | 下游消费 | 上游出站（提供 HTTP 接口） |
| 主对象 | RemoteCallException | ProblemDetailResponse (Web) |
| 宽容模式 | 支持 tolerant | 不适用（由控制器异常统一） |
| 指标 | 解码/解析/体读取 | 解析/构建/链路（核心 + Web） |
| Trace 处理 | 透传请求 + 回填响应 | 构建响应时写 traceId |

---

## 13. 参考源码

| 位置 | 说明 |
|------|------|
| `error/config/FeignErrorAutoConfiguration.java` | ProblemDetail 解码自动配置 |
| `error/decoder/ProblemDetailErrorDecoder.java` | 核心解码实现 |
| `error/exception/RemoteCallException.java` | 结构化远端异常 |
| `error/interceptor/TraceIdRequestInterceptor.java` | TraceId 请求头注入 |
| `error/observation/MicrometerFeignErrorObservationRecorder.java` | 指标记录实现 |
| `runtime/PatraFeignAutoConfiguration.java` | 通用拦截器注册 |
| `runtime/PatraFeignRequestInterceptor.java` | 服务名等请求头注入 |

---

如需反馈/增强，请附：methodKey、HTTP 状态、content-type、截断标记(truncated?)、是否 tolerant、traceId、期望行为。