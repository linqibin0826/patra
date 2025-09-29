# patra-common

提供跨微服务复用的领域基础抽象、错误契约与 JSON 规范化工具，是六边形架构最内圈的通用模块。

## 1. 模块定位
- **服务/组件作用**：沉淀聚合根基类、领域事件、统一错误码模型、JSON 规范化及核心枚举/工具
- **主要消费者**：所有 `patra-*` 领域模块（domain 层）与自研 Starter（core/web/feign）
- **架构边界**：仅依赖 JDK、Jackson、Hutool；禁止引入 Spring/MyBatis 等框架，禁止承载具体业务语义

## 2. 核心能力
- **领域建模抽象**：`AggregateRoot`、`ReadOnlyAggregate`、`DomainEvent` 等，确保聚合纯净且可测试
- **错误体系**：`ErrorCodeLike`、`HttpStdErrors`、`ApplicationException`、`DomainException`、`ErrorTrait` 组成统一错误解析基座
- **JSON 规范化**：`JsonMapperHolder` 提供统一 `ObjectMapper`；`JsonNormalizer` 输出确定性 JSON 与哈希原料
- **核心枚举**：来源、优先级、配置作用域等跨服务枚举，统一 JSON 序列化规则
- **通用工具**：`HashUtils` 封装 SHA-256，支撑幂等键与签名计算

> 深入说明（含表格、代码片段）请参阅 `docs/modules/common/deep-dive.md`。

## 3. 分层结构与依赖
```
patra-common/
  └─ src/main/java/com/patra/common/
       domain/    error/    json/    enums/    util/
```
- **依赖**：JDK 21 + Jackson + Hutool（全部由父 POM 管控）
- **禁止事项**：引入框架依赖、放置具体业务聚合/DTO、在模块内编写运行时配置

## 4. 运行与配置
- **引入方式**：所有子模块默认继承 `patra-parent` 即自动获得，不需要额外 Maven 声明；独立使用时可手工添加：
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- **必要配置**：无运行时配置；`JsonMapperHolder` 会在引入 `patra-spring-boot-starter-core` 后自动注册 Spring 容器的 `ObjectMapper`
- **启动步骤**：纯库，无独立启动流程

## 5. 观测与运维
- 模块本身无运行时进程，但其错误码、JsonNormalizer、HashUtils 直接影响各服务的观测数据
- 建议在服务侧接入 `ErrorResolutionEngine` 与 Micrometer 指标时同步验证 `HttpStdErrors` 前缀配置

## 6. 测试策略
- 聚合根/事件抽象：验证事件收集、不可变性与约束断言
- JsonNormalizer：覆盖空值、数组去重、宽松布尔/时间解析、异常分支
- 错误码：断言 `HttpStdErrors` 生成的 HTTP 状态与业务自定义段的枚举值
- 核心枚举：测试别名解析与非法输入兜底

## 7. Roadmap 与风险
| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| 错误解析 trait→HTTP 映射下沉至 Starter | 规划中 | 需与 web/feign starter 协同，避免重复配置 |
| JsonNormalizer 插件化 Hook | 规划中 | 注意性能与线程安全；计划通过 SPI 扩展 |
| 新哈希算法支持（MD5/SHA-1/Blake3） | 规划中 | 需评估安全性与兼容要求 |
| Canonical JSON Benchmark | 规划中 | 需引入 JMH，关注测试依赖体积 |

## 8. 参考资料
- 深入文档：`docs/modules/common/deep-dive.md`
- 错误处理规范：`docs/standards/platform-error-handling.md`
- 跨服务错误最佳实践：`docs/standards/cross-service-error-best-practices.md`
- 采集链路示例中对 Hash/JSON 的使用：`docs/process/ingest-dataflow.md`
