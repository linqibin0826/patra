# patra-expr-kernel

统一的不可变表达式 AST 内核，支撑跨服务的过滤条件编排、规范化与签名。

## 1. 模块定位
- **服务/组件作用**：构建最小而稳定的布尔表达式模型（AND/OR/NOT/CONST/ATOM），输出可序列化、可规范化、可哈希的语义快照
- **主要消费者**：`patra-spring-boot-starter-expr`、`patra-ingest`、未来的检索/分析服务
- **架构边界**：纯 Java 库，无任何框架依赖；算子集合保持精简，扩展通过外围编译阶段实现

## 2. 核心能力
- **AST 定义**：sealed interface `Expr` + record 节点，保证不可变与类型安全
- **序列化协议**：`ExprJsonCodec` 提供稳定 JSON Schema 与前向兼容策略
- **规范化与签名**：`ExprCanonicalizer` 产出 canonical JSON 与 SHA-256 指纹，支撑缓存、幂等、审计
- **Visitor 机制**：`ExprVisitor` 让 ES/SQL/自定义引擎可独立实现翻译器
- **工具工厂**：`Exprs` 快速构造表达式，减少样板代码

> 详尽说明（AST 表格、JSON 示例、性能策略）见 `docs/modules/expr-kernel/deep-dive.md`。

## 3. 分层结构与依赖
- 目录概览：`expr/`（AST）、`json/`（Codec）、`canonical/`（规范化）、`visitor/`（Visitor 接口）
- 依赖：仅依赖 JDK 21、Jackson（由父 POM 管控）
- 禁止事项：引入框架依赖、把聚合/查询语义直接放入 AST（需经外层转译）

## 4. 运行与配置
- **引入方式**：
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-expr-kernel</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- **必要配置**：无；若与 Spring 集成，`patra-spring-boot-starter-expr` 会注入额外的编译与规则
- **基本用法**：通过 `Exprs` 构建表达式 → `Exprs.toJson` 序列化 → `ExprCanonicalizer` 生成签名

## 5. 观测与运维
- 模块本身无运行态指标；其规范化输出用于跟踪表达式命中情况
- 建议在使用方记录 `canonicalJson/hash` 以便排障和缓存命中分析

## 6. 测试策略
- AST：验证 record 不变量（字段非空、Operator/Value 匹配）
- JSON Codec：序列化/反序列化互逆、前向兼容（忽略未知字段）
- Canonicalizer：覆盖空值、数组去重、数值规范化；对复杂表达式进行快照对比
- Visitor：为自定义翻译器提供最小单元测试集合

## 7. Roadmap 与风险
| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| JSON Schema 发布 | High | 需与调用方同步升级；Schema 变更需走版本管理 |
| Snapshot 缓存 SPI | High | 注意线程安全与内存占用 |
| TextMatch 扩展 | Mid | 保留最小算子集，需评估编译器兼容 | 
| Canonical 性能优化 | Low | 大数组排序可能成为瓶颈，待基准验证 |

## 8. 参考资料
- 深度文档：`docs/modules/expr-kernel/deep-dive.md`
- 表达式编译 Starter：`patra-spring-boot-starter-expr/README.md`
- 采集链路示例：`docs/process/ingest-dataflow.md`
