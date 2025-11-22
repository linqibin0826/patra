# 配置问题

## Bean 命名冲突解决

**问题**: 在应用同时依赖 `patra-spring-boot-starter-core` 和 `patra-spring-boot-starter-rest-client` 时，两个模块都会创建追踪拦截器，默认方法名 `tracingInterceptor()` 导致 Bean 冲突。

**解决方案**: 根据模块用途进行有区别的 Bean 命名:

| 模块 | Bean 类型 | 方法名 | 场景 |
|------|----------|--------|------|
| `patra-spring-boot-starter-core` | `TracingInterceptor` (错误处理) | `errorResolutionTracingInterceptor()` | 在错误解析管道中传播追踪上下文 |
| `patra-spring-boot-starter-rest-client` | `TracingInterceptor` (HTTP 客户端) | `restClientTracingInterceptor()` | 在 REST 请求中传播追踪上下文 |

**实现细节**:
- `CoreErrorAutoConfiguration.errorResolutionTracingInterceptor(TraceProvider)` - 第 175 行
- `RestClientAutoConfiguration.restClientTracingInterceptor(RestClientProperties)` - 第 131 行
- 两个 Bean 会根据其 `@Order` 在各自的管道中独立执行，不会相互影响

**受影响的文档**:
- `patra-spring-boot-starter-core/README.md` - 已更新 Bean 列表
- `patra-spring-boot-starter-core/src/main/java/com/patra/starter/core/error/config/package-info.java` - 记录了 Bean 重命名原因
- `patra-spring-boot-starter-rest-client/README.md` - 已注明 Bean 名称更新
- `patra-spring-boot-starter-rest-client/src/main/java/com/patra/starter/restclient/config/package-info.java` - 记录了 Bean 命名说明

**时间线**:
- 2025-01-22 - 检测并解决 Bean 命名冲突问题，更新相关文档

---
