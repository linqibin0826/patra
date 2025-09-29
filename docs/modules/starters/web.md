## 模块：patra-spring-boot-starter-web

提供 Web 层（Spring MVC / Servlet）统一错误响应与基础类型转换能力：

1. 全局异常 → RFC 7807 ProblemDetail（含扩展字段）
2. 校验（JSR-380）错误收集与脱敏格式化
3. 可插拔字段贡献 SPI（核心 + Web 维度分层）
4. 代理/网关环境路径提取（Forwarded / X-Forwarded-*）
5. 标准化 traceId / timestamp / error code 输出
6. 提供 `String -> ProvenanceCode` 全局 Converter

---

## 1. 快速开始

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-core</artifactId>
</dependency>
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-web</artifactId>
</dependency>
```

YAML：
```yaml
patra:
    error:
        context-prefix: REG   # core starter 中提供错误码分组前缀
    web:
        problem:
            enabled: true
            type-base-url: "https://errors.example.com/"
            include-stack: false
```

Controller 中无需手写 try/catch：
```java
@GetMapping("/namespaces/{id}")
public NamespaceDto find(@PathVariable String id) {
        throw new NamespaceNotFoundException(id); // 自动转换为 ProblemDetail
}
```

---

## 2. 自动配置与 Bean

| Bean | 默认实现 | 作用 |
|------|----------|------|
| Converter<String, ProvenanceCode> | provenanceCodeConverter | PathVariable/RequestParam 自动绑定枚举别名 |
| ValidationErrorsFormatter | DefaultValidationErrorsFormatter | 校验错误收集 + 脱敏 + 截断 |
| ProblemDetailBuilder | ProblemDetailBuilder | 聚合核心与 Web 扩展字段，构建 ProblemDetail |
| ProblemDetailAdapter | DefaultProblemDetailAdapter | 调用核心 `ErrorResolutionPipeline` 适配为响应模型 |
| GlobalRestExceptionHandler | GlobalRestExceptionHandler | 统一异常入口（包含参数校验专用处理） |

> 属性开关：`patra.web.problem.enabled=false` 可整体关闭；单 Bean 可通过自定义同类型 Bean 覆盖。

---

## 3. 配置属性（`patra.web.problem.*`）

| 属性 | 默认 | 说明 |
|------|------|------|
| enabled | true | 是否启用 Web 层错误处理 |
| type-base-url | https://errors.example.com/ | 构造 ProblemDetail.type 前缀（末尾自动补 `/`） |
| include-stack | false | 是否包含堆栈（生产环境禁止开启） |

---

## 4. 请求→响应处理流程

1. 异常抛出（域/应用/运行时）
2. `GlobalRestExceptionHandler` 捕获
3. `ProblemDetailAdapter` 调用 `ErrorResolutionPipeline.resolve(Throwable)` 得到 `ErrorResolution`
4. `ProblemDetailBuilder` 按以下顺序填充：
     - 标准字段：type / title / detail / status
     - 扩展字段：code / path / timestamp / traceId
     - Core ProblemFieldContributor 扩展
     - Web ProblemFieldContributor 扩展（含请求上下文）
5. 参数校验异常：额外加入 `errors` 数组（字段 + 拒绝值 + 消息）
6. 返回 `application/problem+json`

---

## 5. ProblemDetail 字段规范

| 字段 | 来源 | 说明 |
|------|------|------|
| type | `type-base-url` + 错误码小写 | 唯一错误文档定位入口 |
| title | 错误码（原值） | 保持短、标识性强 |
| status | pipeline 解析出的 HTTP 状态 | 非法值回退 500 |
| detail | 异常消息（脱敏后） | 生产建议保持通用语义 |
| code | 错误码（扩展字段） | 与 title 同步，利于前端匹配 |
| traceId | TraceProvider | 分布式追踪 ID |
| path | Forwarded/X-Forwarded-* 优先 | 网关后可见真实路径 |
| timestamp | UTC ISO-8601 | 服务端时间戳 |
| errors | (可选) 校验错误数组 | 仅在校验异常时存在 |

示例：
```json
{
    "type": "https://errors.example.com/reg-1001",
    "title": "REG-1001",
    "status": 404,
    "detail": "Namespace not found: ns-x",
    "code": "REG-1001",
    "traceId": "4f1d9b3c...",
    "path": "/api/registry/namespaces/ns-x",
    "timestamp": "2025-09-28T08:30:20Z"
}
```

---

## 6. 校验错误格式化 (DefaultValidationErrorsFormatter)

处理逻辑：
| 步骤 | 说明 |
|------|------|
| 收集 | `BindingResult#getAllErrors()` → 限制最多 100 条 |
| 字段识别 | FieldError vs ObjectError（全局错误） |
| 脱敏 | 字段名包含 password/token/secret/key/credential/auth/pin/ssn/credit/card/account → `***` |
| 截断告警 | 超过上限写日志 WARN |

自定义：实现 `ValidationErrorsFormatter` Bean 即可覆盖。

---

## 7. 扩展点（SPI）

| 接口 | 方向 | 典型用途 |
|------|------|----------|
| ProblemFieldContributor (core starter) | 核心字段 | 与业务无关的通用扩展（如 host, env） |
| WebProblemFieldContributor | Web 上下文字段 | userAgent / clientIp / geo 信息 |
| ValidationErrorsFormatter | 校验错误格式 | 自定义脱敏策略、错误分组 |

> 避免在 contributor 中写入超大对象；大对象请转换为可追踪 ID。

---

## 8. 自定义示例

自定义 Web 字段：
```java
@Component
class UserAgentContributor implements WebProblemFieldContributor {
    public void contribute(Map<String,Object> f, Throwable ex, HttpServletRequest req){
        f.put("userAgent", req.getHeader("User-Agent"));
        f.put("clientIp", Optional.ofNullable(req.getHeader("X-Forwarded-For"))
                .map(h -> h.split(",")[0]).orElse(req.getRemoteAddr()));
    }
}
```

自定义校验错误格式化：
```java
@Component
class SimpleValidationFormatter implements ValidationErrorsFormatter {
    public List<ValidationError> formatWithMasking(BindingResult br){
        return br.getFieldErrors().stream()
            .map(e -> new ValidationError(e.getField(), redact(e.getField(), e.getRejectedValue()), e.getDefaultMessage()))
            .toList();
    }
    private Object redact(String field, Object val){
        if(field.toLowerCase().contains("password")) return "***"; return val; }
}
```

---

## 9. 性能 & 安全

| 关注点 | 策略 |
|--------|------|
| 大量校验错误 | 截断 100 条，日志提示 | 
| 堆栈暴露 | 默认关闭 include-stack |
| 敏感信息泄漏 | message 正则脱敏 + validation masking |
| 规则执行开销 | 核心解析在 core starter 中缓存（若已实现）|
| 路径获取 | 多头部容错，避免 NPE |

---

## 10. 最佳实践

| 场景 | 建议 |
|------|------|
| 统一错误码文档 | 与 type-base-url 对应，生成静态页面或 OpenAPI link |
| 前端提示 | 使用 code + errors[*].field 进行 i18n 映射 |
| 追踪 | traceId 贯穿日志 / 前端错误反馈收集表单 |
| 单元测试 | 断言 ProblemDetail 的关键字段（type/code/status/path）|

---

## 11. Roadmap

| 优先级 | 项目 | 描述 |
|--------|------|------|
| High | Stack 输出安全开关细化 | dev/prod profile 自动判定 |
| High | OpenAPI 集成 | 生成错误码规范文档 endpoint |
| Mid | i18n 支持 | ProblemDetail title/detail 可按 Accept-Language 动态翻译 |
| Mid | 可配置截断策略 | 校验错误数量、字段白名单 |
| Low | 链路可观测增强 | 输出 correlationId / spanId |

---

## 12. FAQ

| 问题 | 回答 |
|------|------|
| 为什么不默认输出堆栈? | 避免敏感信息泄漏与响应膨胀；调试时临时开启 |
| errors 字段为空怎么办? | 仅校验异常才存在；其他场景省略字段更简洁 |
| 如何扩展 ProblemDetail 字段? | 实现 ProblemFieldContributor / WebProblemFieldContributor |
| traceId 缺失? | 确认核心 starter TraceProvider 是否正确接入（日志 MDC）|

---

## 13. 参考源码

| 位置 | 说明 |
|------|------|
| `web/autoconfig/WebConversionAutoConfiguration.java` | 字符串到 ProvenanceCode 转换自动配置 |
| `web/error/config/WebErrorAutoConfiguration.java` | Web 错误自动配置入口 |
| `web/error/builder/ProblemDetailBuilder.java` | ProblemDetail 构造逻辑 |
| `web/error/handler/GlobalRestExceptionHandler.java` | 全局异常处理器 |
| `web/error/formatter/DefaultValidationErrorsFormatter.java` | 校验错误脱敏与截断 |
| `web/error/adapter/DefaultProblemDetailAdapter.java` | 解析结果适配 -> ProblemDetail |

---

如需新增功能或发现问题，请附：请求路径、traceId、异常类型、当前响应 JSON、期望结果。