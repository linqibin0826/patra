# patra-spring-boot-starter-web

提供基于 Spring MVC 的统一错误响应与类型转换能力，将核心 Starter 的 ErrorResolution 输出为 RFC7807 ProblemDetail。

## 1. 模块定位
- **服务/组件作用**：实现 Web 层全局异常处理、校验错误聚合、Forwarded 头解析、标准字段贡献
- **主要消费者**：所有基于 Servlet/MVC 的微服务（registry、ingest-adapter 等）
- **架构边界**：只处理 Web 横切逻辑；业务控制器仍负责领域校验与响应构造

## 2. 核心能力
- **ProblemDetail 适配**：将 `ErrorResolution` 映射为 RFC7807 结构（code/path/timestamp/traceId）
- **JSR-380 校验格式化**：收集字段错误、脱敏敏感值
- **字段贡献 SPI**：`ProblemFieldContributor` 扩展响应属性
- **类型转换**：提供 `String -> ProvenanceCode` 等全局 Converter
- **代理信息提取**：解析 `Forwarded/X-Forwarded-*` 头，补充客户端地址

详见 `docs/modules/starters/web.md`。

## 3. 分层结构与依赖
- 主要包：`problem`（ProblemDetail 组装）、`advice`（异常处理）、`converter`（类型转换）、`forwarded`
- 依赖：`patra-spring-boot-starter-core`、Spring Boot Web、Jakarta Validation

## 4. 运行与配置
- Maven 引入：
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-web</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- 示例配置：
  ```yaml
  patra:
    error.context-prefix: REG
    web:
      problem:
        enabled: true
        type-base-url: https://errors.papertrace.local/
        include-stack: false
  ```
- 默认开启 `GlobalExceptionHandler`；若需自定义，可实现 `ProblemResponseCustomizer`

## 5. 观测与运维
- ProblemDetail 中默认输出 `code/path/traceId/timestamp`; 需确保日志 JSON 化后携带 TraceId
- 结合核心 Starter 指标，可统计 Web 层错误分布
- 代理头解析需在网关侧信任配置正确，以防伪造

## 6. 测试策略
- 使用 `@WebMvcTest` 验证异常映射、校验错误格式与自定义字段
- Mock Forwarded 头检查客户端地址解析
- 确认 ProblemDetail 输出符合前端契约（字段顺序/命名）

## 7. Roadmap 与风险
| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| ProblemDetail 字段模板化 | 进行中 | 统一多服务扩展字段，防止重复实现 |
| 国际化消息支持 | 规划 | 需引入 MessageSource；注意性能与缓存 |
| 序列化可配置字段 | 规划 | 可按服务选择性输出 stack/detail |

风险：上下游未同步错误字段、Forwarded 头信任链配置错误、业务层重复处理异常。

## 8. 参考资料
- 深度文档：`docs/modules/starters/web.md`
- 核心错误解析：`patra-spring-boot-starter-core/README.md`
- 错误规范：`docs/standards/platform-error-handling.md`
