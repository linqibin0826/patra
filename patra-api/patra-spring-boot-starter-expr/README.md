# patra-spring-boot-starter-expr

基于 `patra-expr-kernel` 的表达式编译与渲染 Starter，将 Registry 快照加工为查询/请求片段。

## 1. 模块定位
- **服务/组件作用**：加载 Registry 的字段能力、渲染模板、参数映射，完成表达式规范化、校验与渲染
- **主要消费者**：`patra-ingest`（计划表达式）、后续搜索/分析服务
- **架构边界**：保持无状态；编译流程可通过 Bean 替换，业务逻辑仍在调用方

## 2. 核心能力
- **Normalization**：调用 `ExprCanonicalizer` 统一表达式 JSON，生成指纹
- **Capability Checking**：校验字段可用性、运算符、值类型、长度限制
- **Rendering**：根据渲染规则输出目标查询片段及参数映射
- **诊断信息**：提供 `ValidationReport`、`RenderTrace` 帮助定位失败原因
- **Registry 集成**：内置客户端从 Registry 拉取快照，可按操作类型默认化

本模块 README 覆盖流程与配置；如需对比其它 Starter，请参考各 Starter 模块 README（`patra-spring-boot-starter-*`、`patra-spring-cloud-starter-feign`）。

## 3. 分层结构与依赖
- 核心包：`compiler`（编译器）、`normalizer`、`renderer`、`report`
- 依赖：`patra-expr-kernel`、`patra-spring-boot-starter-core`、Registry API 客户端

## 4. 运行与配置
- Maven 引入：
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-expr</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- 配置示例：
  ```yaml
  patra:
    expr:
      compiler:
        enabled: true
        registry-api:
          enabled: true
          operation-default: SEARCH
  ```
- 扩展点：提供 `ExprCompilerCustomizer`、`CapabilityChecker`、`RenderStrategy` 等 Bean 覆盖点

## 5. 观测与运维
- 可记录编译耗时、失败原因（结合核心 Starter 指标）
- 建议缓存 Registry 快照并监控版本号，以避免表达式编译使用陈旧数据

## 6. 测试策略
- 使用内存快照构造编译器，验证规范化、能力校验、渲染输出
- 模拟非法字段/算子/长度、Registry 快照缺失等错误路径
- 校验渲染结果与目标后端（ES/SQL）翻译器配合

## 7. Roadmap 与风险
| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| 快照缓存策略 | 规划 | 需要与 Registry 缓存失效机制协同 |
| 多后端渲染插件 | 规划 | 插件化带来依赖拆分与测试复杂性 |
| RenderTrace 可视化 | 规划 | 需提供诊断 UI 或日志格式 |

风险：Registry 快照不一致、表达式复杂度过高导致性能下降、扩展 Bean 顺序冲突。

## 8. 参考资料
- 其他 Starter：`patra-spring-boot-starter-core/README.md`、`patra-spring-boot-starter-web/README.md`、`patra-spring-cloud-starter-feign/README.md`、`patra-spring-boot-starter-mybatis/README.md`
- 表达式内核：`patra-expr-kernel/README.md`
- Registry 快照：`docs/modules/registry/deep-dive.md`
