# patra-spring-boot-starter-web

> Web 层自动配置,提供 REST 控制器、验证、Problem Detail 错误处理和 Feign 客户端支持。

## 📌 目的

为 REST API 提供**Web 特定**的自动配置:
- Spring Web MVC 配置
- 验证框架(`@Valid`)
- Problem Detail 错误映射(RFC 7807)
- Feign 客户端集成
- CORS 配置
- 请求/响应日志记录

## 🔧 自动配置

### REST 控制器支持
- 自动注册 `@RestControllerAdvice`
- 全局异常处理器
- 所有错误的 Problem Detail 响应

### 验证
- 启用 JSR-303 Bean 验证
- 自动注册自定义验证器
- 验证错误映射到 Problem Detail

### Feign 客户端
- Feign 错误的错误解码器
- 用于追踪传播的请求拦截器
- 重试策略

## 🔗 依赖

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-web</artifactId>
</dependency>
```

包含: `patra-spring-boot-starter-core`、Spring Web、Spring Cloud OpenFeign

## 🚀 用法

### 适配器层
```java
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    @PostMapping
    public PlanResponse create(@Valid @RequestBody CreatePlanRequest request) {
        // 自动验证,错误映射到 Problem Detail
        return orchestrator.create(request.toCommand());
    }
}
```

### 错误处理
```java
@Component
public class IngestErrorMapping implements ErrorMappingContributor {
    @Override
    public ProblemDetail map(Exception ex) {
        if (ex instanceof PlanNotFoundException) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        }
        return null;
    }
}
```

---

**最后更新**: 2025-01-12
