/// 共享枚举包 - 跨服务使用的通用枚举定义。
///
/// 本包包含 Patra 平台所有微服务共享的枚举定义,提供类型安全的常量和业务语义。 这些枚举用于标识数据源、优先级、配置作用域、日期类型等核心业务概念。
///
/// ## 职责
///
/// - 定义跨服务共享的枚举类型
///   - 提供类型安全的常量定义
///   - 支持字符串解析和别名识别
///   - 支持 Jackson 序列化/反序列化
///   - 提供人类可读的描述信息
///
/// ## 核心枚举
///
/// - {@link com.patra.common.enums.ProvenanceCode} - 数据源枚举, 标识文献的上游来源(PUBMED、PMC、EPMC、OPENALEX
///       等),支持别名解析
///   - {@link com.patra.common.enums.Priority} - 优先级枚举, 用于任务调度和消息队列(HIGH、MEDIUM、LOW)
///   - {@link com.patra.common.enums.RegistryConfigScope} - 配置作用域枚举,
///       定义配置的适用范围(SOURCE、OPERATION、TASK),支持优先级规则
///   - {@link com.patra.common.enums.IngestDateType} - 采集日期类型枚举, 定义日期类型(PUBLICATION、ENTREZ、EPMC 等)
///   - {@link com.patra.common.enums.SortDirection} - 排序方向枚举, 定义排序方向(ASC、DESC)
///
/// ## ProvenanceCode 核心特性
///
/// - **字符串解析**: 通过 `parse()` 方法支持大小写不敏感的解析
///   - **别名识别**: 支持常见别名(如 "medline" → PUBMED, "europepmc" → EPMC)
///   - **Jackson 支持**: 自动序列化为字符串代码,反序列化支持别名
///   - **人类可读**: 提供 `getDescription()` 方法返回友好名称
///
/// ## RegistryConfigScope 优先级规则
///
/// 配置作用域定义了配置的适用范围和优先级:
///
/// - **TASK**: 任务特定配置,最高优先级,覆盖 OPERATION 和 SOURCE
///   - **OPERATION**: 操作类型特定配置,中等优先级,覆盖 SOURCE
///   - **SOURCE**: 数据源默认配置,最低优先级
///
/// ## 使用场景
///
/// - **数据源识别**: 使用 {@link com.patra.common.enums.ProvenanceCode} 标识文献来源
///   - **任务调度**: 使用 {@link com.patra.common.enums.Priority} 定义任务优先级
///   - **配置管理**: 使用 {@link com.patra.common.enums.RegistryConfigScope} 区分配置作用域
///   - **查询排序**: 使用 {@link com.patra.common.enums.SortDirection} 指定排序方向
///
/// ## 使用示例
///
/// ```java
/// // 1. ProvenanceCode 解析和使用
/// ProvenanceCode source = ProvenanceCode.parse("pubmed");
/// // 支持别名: "medline" → PUBMED, "europepmc" → EPMC
///
/// String code = source.getCode();          // "PUBMED"
/// String desc = source.getDescription();   // "PubMed"
///
/// // 2. 配置作用域优先级
/// RegistryConfigScope scope1 = RegistryConfigScope.TASK;
/// RegistryConfigScope scope2 = RegistryConfigScope.SOURCE;
/// boolean hasHigherPriority = scope1.compareTo(scope2) > 0;  // true
///
/// // 3. 任务优先级
/// Priority priority = Priority.HIGH;
/// int level = priority.getLevel();  // 数值越大优先级越高
///
/// // 4. Jackson 序列化
/// ProvenanceCode code = ProvenanceCode.PUBMED;
/// String json = objectMapper.writeValueAsString(code);  // "PUBMED"
/// ProvenanceCode parsed = objectMapper.readValue("\"pubmed\"", ProvenanceCode.class);  // PUBMED
/// ```
///
/// ## Jackson 序列化支持
///
/// 所有枚举都支持 Jackson 序列化/反序列化:
///
/// - **序列化**: 枚举值序列化为字符串代码(如 `PUBMED`)
///   - **反序列化**: 字符串反序列化为枚举值,支持大小写不敏感和别名
///   - **自定义**: 通过 `@JsonCreator` 和 `@JsonValue` 自定义序列化行为
///
/// ## 设计原则
///
/// - **类型安全**: 使用枚举而非字符串常量,避免拼写错误
///   - **扩展性**: 新增枚举值不影响现有代码
///   - **向后兼容**: 支持别名识别,兼容旧版本代码
///   - **语义明确**: 枚举名称清晰表达业务含义
///   - **文档完整**: 提供清晰的 Javadoc 说明每个枚举值的含义
///
/// @since 0.1.0
/// @author linqibin
package com.patra.common.enums;
