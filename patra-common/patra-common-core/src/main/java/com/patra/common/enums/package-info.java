/**
 * 共享枚举包 - 跨服务使用的通用枚举定义。
 *
 * <p>本包包含 Patra 平台所有微服务共享的枚举定义,提供类型安全的常量和业务语义。
 * 这些枚举用于标识数据源、优先级、配置作用域、日期类型等核心业务概念。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义跨服务共享的枚举类型
 *   <li>提供类型安全的常量定义
 *   <li>支持字符串解析和别名识别
 *   <li>支持 Jackson 序列化/反序列化
 *   <li>提供人类可读的描述信息
 * </ul>
 *
 * <h2>核心枚举</h2>
 *
 * <ul>
 *   <li>{@link com.patra.common.enums.ProvenanceCode} - 数据源枚举,
 *       标识文献的上游来源(PUBMED、PMC、EPMC、OPENALEX 等),支持别名解析
 *   <li>{@link com.patra.common.enums.Priority} - 优先级枚举,
 *       用于任务调度和消息队列(HIGH、MEDIUM、LOW)
 *   <li>{@link com.patra.common.enums.RegistryConfigScope} - 配置作用域枚举,
 *       定义配置的适用范围(SOURCE、OPERATION、TASK),支持优先级规则
 *   <li>{@link com.patra.common.enums.IngestDateType} - 采集日期类型枚举,
 *       定义日期类型(PUBLICATION、ENTREZ、EPMC 等)
 *   <li>{@link com.patra.common.enums.SortDirection} - 排序方向枚举,
 *       定义排序方向(ASC、DESC)
 * </ul>
 *
 * <h2>ProvenanceCode 核心特性</h2>
 *
 * <ul>
 *   <li><strong>字符串解析</strong>: 通过 {@code parse()} 方法支持大小写不敏感的解析
 *   <li><strong>别名识别</strong>: 支持常见别名(如 "medline" → PUBMED, "europepmc" → EPMC)
 *   <li><strong>Jackson 支持</strong>: 自动序列化为字符串代码,反序列化支持别名
 *   <li><strong>人类可读</strong>: 提供 {@code getDescription()} 方法返回友好名称
 * </ul>
 *
 * <h2>RegistryConfigScope 优先级规则</h2>
 *
 * <p>配置作用域定义了配置的适用范围和优先级:
 *
 * <ul>
 *   <li><strong>TASK</strong>: 任务特定配置,最高优先级,覆盖 OPERATION 和 SOURCE
 *   <li><strong>OPERATION</strong>: 操作类型特定配置,中等优先级,覆盖 SOURCE
 *   <li><strong>SOURCE</strong>: 数据源默认配置,最低优先级
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>数据源识别</strong>: 使用 {@link com.patra.common.enums.ProvenanceCode} 标识文献来源
 *   <li><strong>任务调度</strong>: 使用 {@link com.patra.common.enums.Priority} 定义任务优先级
 *   <li><strong>配置管理</strong>: 使用 {@link com.patra.common.enums.RegistryConfigScope} 区分配置作用域
 *   <li><strong>查询排序</strong>: 使用 {@link com.patra.common.enums.SortDirection} 指定排序方向
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 1. ProvenanceCode 解析和使用
 * ProvenanceCode source = ProvenanceCode.parse("pubmed");
 * // 支持别名: "medline" → PUBMED, "europepmc" → EPMC
 *
 * String code = source.getCode();          // "PUBMED"
 * String desc = source.getDescription();   // "PubMed"
 *
 * // 2. 配置作用域优先级
 * RegistryConfigScope scope1 = RegistryConfigScope.TASK;
 * RegistryConfigScope scope2 = RegistryConfigScope.SOURCE;
 * boolean hasHigherPriority = scope1.compareTo(scope2) > 0;  // true
 *
 * // 3. 任务优先级
 * Priority priority = Priority.HIGH;
 * int level = priority.getLevel();  // 数值越大优先级越高
 *
 * // 4. Jackson 序列化
 * ProvenanceCode code = ProvenanceCode.PUBMED;
 * String json = objectMapper.writeValueAsString(code);  // "PUBMED"
 * ProvenanceCode parsed = objectMapper.readValue("\"pubmed\"", ProvenanceCode.class);  // PUBMED
 * }</pre>
 *
 * <h2>Jackson 序列化支持</h2>
 *
 * <p>所有枚举都支持 Jackson 序列化/反序列化:
 *
 * <ul>
 *   <li><strong>序列化</strong>: 枚举值序列化为字符串代码(如 {@code PUBMED})
 *   <li><strong>反序列化</strong>: 字符串反序列化为枚举值,支持大小写不敏感和别名
 *   <li><strong>自定义</strong>: 通过 {@code @JsonCreator} 和 {@code @JsonValue} 自定义序列化行为
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>类型安全</strong>: 使用枚举而非字符串常量,避免拼写错误
 *   <li><strong>扩展性</strong>: 新增枚举值不影响现有代码
 *   <li><strong>向后兼容</strong>: 支持别名识别,兼容旧版本代码
 *   <li><strong>语义明确</strong>: 枚举名称清晰表达业务含义
 *   <li><strong>文档完整</strong>: 提供清晰的 Javadoc 说明每个枚举值的含义
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.common.enums;
