## 模块：patra-gateway-boot (边缘网关服务)

作为平台统一入口（Edge Service），基于 Spring Cloud Gateway（WebFlux 响应式）实现：

1. 路由聚合：对下游微服务（registry / ingest / 未来 search 等）统一暴露域名与路径前缀
2. 服务发现：Nacos（动态实例列表 + 健康状态）
3. 负载均衡：Spring Cloud LoadBalancer（替代 Ribbon）
4. 预留截断点：鉴权 / 限流 / 熔断 / 统一错误模型（ProblemDetail）/ Trace 透传
5. 安全文档化入口：后续可挂 Swagger 聚合或自定义 OpenAPI 代理

当前仓库内实现最小可运行骨架，聚焦运行时拓展空间，而非功能堆叠。

---

## 1. 目录概览

```
patra-gateway-boot/
  └─ src/main/java/.../PatraGatewayApplication.java  # 启动类
```

后续扩展建议目录（未创建）：

```
config/           # 网关过滤器/路由配置 Bean
filter/global/    # GlobalFilter (trace / metrics / auth)
filter/route/     # 局部 Route 自定义过滤器工厂
predicate/        # 自定义路由断言
error/            # 统一异常与 ProblemDetail 适配
rate/             # 限流适配层 (Redis / Sentinel / TokenBucket)
observability/    # Micrometer / SkyWalking tags enrich
security/         # 鉴权/认证（JWT / OAuth2）
```

---

## 2. 依赖

| 依赖 | 作用 |
|------|------|
| patra-spring-boot-starter-core | 统一错误基类 + Trace Provider SPI |
| spring-cloud-starter-gateway | 核心路由/过滤框架（Netty + Reactor） |
| spring-cloud-starter-loadbalancer | Replaced Ribbon，基于 ServiceInstance 负载策略 |
| spring-cloud-starter-alibaba-nacos-discovery | 动态服务发现 |
| spring-cloud-starter-alibaba-nacos-config | 外部化配置中心 |

---

## 3. 最小运行流程（请求生命周期）

```
Client → Gateway (Netty Channel) → GlobalFilter 链
   ① 预处理：Trace/Correlation 注入 (预留)
   ② 鉴权/限流 (待实现)
   ③ 路由匹配 (RoutePredicateHandlerMapping)
   ④ 下游转发 (ReactiveLoadBalancerClientFilter)
   ⑤ 响应回写 + 错误统一封装 (计划接入 ProblemDetail)
```

---

## 4. 路由配置建议

优先使用 Nacos 配置中心集中管理：`gateway-routes.yaml`（示例占位）

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
        - id: ingest-api
          uri: lb://patra-ingest
          predicates:
            - Path=/ingest/**
          filters:
            - StripPrefix=1
```

后续可引入：自定义 Predicate（Header / GrayRelease），动态配置热刷新（监听 Nacos Config 回调）。

---

## 5. 统一错误与 ProblemDetail 规划

| 阶段 | 动作 | 目标 |
|------|------|------|
| P0 | 基础全局异常捕获 (ErrorWebExceptionHandler) | 将下游 4xx/5xx 包装为平台统一结构 |
| P1 | 与 core/web starter 间复用 ProblemDetailBuilder | 避免重复字段拼装逻辑 |
| P2 | 下游特定错误码透传策略（白名单） | 兼顾一致性与可调试性 |
| P3 | 标准化错误指标 (meter: gateway.errors{status,type}) | 观测 & 报警 |

---

## 6. Trace & 观测计划

| 组件 | 方案 | 状态 |
|------|------|------|
| TraceId 生成 | 在最前置 GlobalFilter，如果请求未携带则生成 | 待实现 |
| 下游透传 | 添加到 `X-Trace-Id` / `sw8`（SkyWalking） | 待实现 |
| 指标 | 请求计数/延迟/状态分布（Micrometer + Prometheus） | 待实现 |
| 日志关联 | MDC 注入 traceId | 待实现 |

---

## 7. 性能基线 & 优化思路

| 关注点 | 建议 |
|--------|------|
| 事件循环阻塞 | 全部过滤器避免阻塞调用（禁止直接 JDBC/阻塞 IO） |
| Backpressure | 定制大响应（流式下游）时注意数据分块 flush |
| 连接复用 | 保持 Netty 默认连接池；必要时调优 `reactor.netty.pool.*` |
| 大头部/体积 | 限制请求体（后续可加 RequestSizeFilter） |

---

## 8. 安全拓展（路线）

| 优先级 | 能力 | 说明 |
|--------|------|------|
| High | JWT 验证过滤器 | 解码 + 过期校验 + 黑名单钩子 |
| High | 基于角色/Scope 的路由访问控制 | 与 registry/SSOT 权限数据对接 |
| Mid | 自适应限流 | LeakyBucket / TokenBucket / Sentinel 模式切换 |
| Mid | 防爬虫 | IP/UA 规则 + 速率统计 |
| Low | 内容安全 | 基本 Header 白名单 & CORS 统一策略 |

---

## 9. Roadmap

| 阶段 | 里程碑 | 验收点 |
|------|--------|--------|
| M1 | 最小可观测 | TraceId 生成 + 请求计数指标 |
| M2 | 错误统一 | ProblemDetail 适配 + 异常分类指标 |
| M3 | 安全第一批 | JWT + 基础限流 |
| M4 | 可靠性 | 熔断（ReactiveResilience4j）+ 重试策略 |
| M5 | 开发者体验 | 路由热更新 + 路由可视化面板 |

---

## 10. FAQ

| 问题 | 说明 |
|------|------|
| 为什么选 Gateway 而非 Zuul / Nginx+Lua? | 原生响应式 + 与 Spring 体系无缝 + 生态丰富；Nginx 仍可做 L4/L7 前置。 |
| 下游服务新增是否要改代码? | 若使用 Nacos + 动态路由配置，无需修改代码（配置热加载）。 |
| 如何压测? | 使用 wrk / bombardier，对典型路由 1k 并发，监控 P95 延迟与错误率。 |
| 如何扩容? | 纯无状态，水平扩容（K8s HPA / Docker Compose scale）即可。 |

---

## 11. 贡献指引

提交新过滤器请：
1. 区分 GlobalFilter (链首/链尾位置) vs GatewayFilterFactory (按路由注册)
2. 保证无阻塞（必要时使用异步客户端）
3. 添加最小单测（对过滤器逻辑）
4. 文档更新（README 对应章节）

---

最小骨架已就绪，按上述路线逐步增强，不一次性“大爆炸”集成以降低演进风险。