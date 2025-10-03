# patra-spring-cloud-starter-feign

统一 Feign 调用错误解码与 Trace 透传的 Starter。

## 1. 模块定位
- **服务/组件作用**：将下游 ProblemDetail/JSON 错误解析为 `RemoteCallException`，保持错误码语义一致
- **主要消费者**：所有通过 Feign 调用内部服务的模块（ingest、registry 等）
- **架构边界**：只处理 HTTP 客户端横切逻辑；业务降级由调用方实现

## 2. 核心能力
- **ProblemDetail 解码**：兼容 `application/problem+json`，提取 `code`、`traceId` 等字段
- **宽容模式**：非 ProblemDetail / JSON 解析失败时仍封装 `RemoteCallException`
- **Trace 透传**：请求头注入 TraceId，响应头写回 ProblemDetail
- **观测指标**：解析耗时、慢解析阈值、Trace 提取结果
- **工具类**：`RemoteErrorHelper` 提供 `isNotFound/isRetryable` 等辅助判断

本模块 README 提供配置项与扩展示例；如需对比其它 Starter，请参考各 Starter 模块 README（`patra-spring-boot-starter-*`）。

## 3. 分层结构与依赖
- 包结构：`decoder`（错误解码）、`observation`、`tracing`、`support`
- 依赖：`patra-spring-boot-starter-core`、Spring Cloud OpenFeign、Micrometer

## 4. 运行与配置
- Maven 引入：
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-cloud-starter-feign</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- 默认开启 `PatraRemoteExceptionDecoder`；可通过 `patra.feign.problem.enabled=false` 禁用
- 常用配置：
  ```yaml
  patra:
    feign:
      problem:
        enabled: true
        tolerant: true
        max-error-body-size: 65536
        include-stack-trace: false
        observation:
          enabled: true
          slow-parsing-threshold-ms: 150
  ```

## 5. 观测与运维
- 指标：解码耗时、解析失败次数、Trace 缺失统计
- 可对慢解析（例如超大错误体）触发告警；合理配置 `max-error-body-size`
- Trace 透传需要上游 Web/Gateway 注入 TraceId

## 6. 测试策略
- 使用 `@FeignClient` + MockWebServer 模拟下游 ProblemDetail、普通 JSON、纯文本
- 验证 tolerant 模式、错误码提取、Trace header 透传
- 结合 `RemoteErrorHelper` 检查业务降级逻辑

## 7. Roadmap 与风险
| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| Reactive 客户端支持 | 规划 | 需兼容 WebClient + Reactor 上下文 |
| 多协议容错 | 规划 | 支持 XML/Protobuf 错误体解析 |
| 指标细粒度标签 | 规划 | 按下游服务/错误码统计解析耗时 |

风险：下游未返回 ProblemDetail、body 过大导致内存占用、Trace 头缺失。确保 tolerant 模式启用并监控异常。

## 8. 参考资料
- 其他 Starter：`patra-spring-boot-starter-core/README.md`、`patra-spring-boot-starter-web/README.md`、`patra-spring-boot-starter-mybatis/README.md`、`patra-spring-boot-starter-expr/README.md`
- 错误处理规范：`docs/standards/platform-error-handling.md`
- 核心 Starter：`patra-spring-boot-starter-core/README.md`
