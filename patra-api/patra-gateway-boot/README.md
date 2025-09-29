# patra-gateway-boot

Spring Cloud Gateway 边缘服务，实现统一入口、路由与后续安全/观测拓展的骨架工程。

## 1. 模块定位
- **服务/组件作用**：作为平台南北向入口汇总下游服务（registry、ingest 等），治理路由、错误、鉴权与指标
- **主要消费者**：外部客户端、内部前端以及需要统一接入的服务调用方
- **架构边界**：基于 WebFlux/Netty，保持无状态；配置与扩展均通过 Nacos 或独立过滤器管理，禁止直接耦合业务逻辑

## 2. 核心能力
- **路由聚合**：按域名/路径转发至下游服务，支持 StripPrefix、Header/Query Predicate
- **服务发现与负载**：整合 Nacos Discovery + Spring Cloud LoadBalancer，实现动态实例感知
- **错误模型预留**：对接 `patra-spring-boot-starter-core`，统一 ProblemDetail 输出与错误指标
- **横切扩展点**：全局/局部过滤器、限流、熔断、Trace 透传、鉴权、观测指标
- **安全与运维路线图**：按阶段引入 JWT、限流、防爬、熔断、路由可视化

## 3. 分层结构与依赖
```
patra-gateway-boot/
  └─ src/main/java/.../PatraGatewayApplication.java
```
- 计划目录：`config/`（配置 Bean）、`filter/global/`、`filter/route/`、`predicate/`、`error/`、`rate/`、`observability/`、`security/`
- 核心依赖：
  | 依赖 | 作用 |
  |------|------|
  | `patra-spring-boot-starter-core` | 统一错误解析、Trace SPI |
  | `spring-cloud-starter-gateway` | 路由与过滤主框架 |
  | `spring-cloud-starter-loadbalancer` | 服务级负载均衡 |
  | `spring-cloud-starter-alibaba-nacos-discovery/config` | 动态发现与集中配置 |
- 禁止将业务逻辑内嵌到网关层；仅做协议转换与横切治理

## 4. 运行与配置
- **配置来源**：推荐通过 Nacos 维护 `gateway-routes.yaml`；示例：
  ```yaml
  spring:
    cloud:
      gateway:
        routes:
          - id: registry-api
            uri: lb://patra-registry
            predicates:
              - Path=/registry/**
            filters:
              - StripPrefix=1
  ```
- **启动**：`PatraGatewayApplication`（Spring Boot）
- **后续扩展**：可监听 Nacos Config 实现路由热更新；按需新增自定义 Predicate、GatewayFilterFactory

## 5. 观测与运维
- 请求生命周期：`Client → Netty Channel → GlobalFilter 链 → Route → 响应`
- Trace/指标规划：
  | 项目 | 状态 |
  |------|------|
  | TraceId 生成与透传 | 待实现（首个 GlobalFilter 注入 `X-Trace-Id`/`sw8`） |
  | 错误指标 | 规划中，集成 Micrometer 记录 `gateway.errors{status,type}` |
  | 请求指标 | 规划中，记录 QPS、延迟、状态分布 |
  | 日志关联 | 规划中，MDC 注入 TraceId |
- 性能守则：避免阻塞 I/O、关注 backpressure、必要时限制请求体大小并调优 `reactor.netty.pool.*`

## 6. 测试策略
- 单元测试：过滤器/断言工厂逻辑、路由选择、错误映射
- 集成验证：使用 Gatling / wrk / bombardier 对常用路由压测，观察 P95/P99 延迟与错误率
- 配置校验：变更路由前通过 Nacos 测试环境验证；结合 `spring.cloud.gateway.test` profile 做冒烟

## 7. Roadmap 与风险
| 阶段 | 能力 | 验收点 |
|------|------|--------|
| M1 | 最小可观测 | TraceId 生成 + 请求计数指标 |
| M2 | 错误统一 | ProblemDetail 适配 + 异常分类指标 |
| M3 | 安全首批 | JWT 鉴权 + 基础限流 |
| M4 | 可靠性 | 熔断（Resilience4j）+ 重试策略 |
| M5 | 开发者体验 | 路由热更新 + 可视化面板 |

主要风险：过滤器阻塞导致事件循环卡顿、错误透传不一致、安全策略缺失。建议逐项落地并监控。

## 8. 参考资料
- 深入规划：`docs/modules/starters/rocketmq-usage.md`（示例横切文档编排模式）
- 错误模型：`docs/standards/platform-error-handling.md`
- 架构概览：`docs/overview/architecture.md`
- FAQ 提示：
  | 问题 | 要点 |
  |------|------|
  | 为什么选 Spring Cloud Gateway? | 原生响应式、生态完备，与 Spring 体系无缝；Nginx 仍可做 L4/L7 前置 |
  | 新增下游是否改代码? | 使用 Nacos 动态路由即可，仅需更新配置 |
  | 如何压测/扩容? | wrk/bombardier + 关注 P95；服务无状态，支持水平扩容 |
