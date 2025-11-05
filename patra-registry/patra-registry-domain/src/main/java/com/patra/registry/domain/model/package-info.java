/**
 * Registry 领域模型根包 - DDD 战术设计核心。
 *
 * <p>本包是 Registry 服务领域模型的根包,包含聚合根、值对象和读模型的组织结构。
 * 领域模型是业务逻辑的核心,封装了 Registry 服务的业务规则、不变性约束和领域概念。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义领域模型的包结构,按 DDD 模式组织(聚合根、值对象、读模型)
 *   <li>封装业务逻辑和领域知识
 *   <li>强制执行业务不变性约束
 *   <li>提供纯粹的领域对象,不依赖框架
 *   <li>支持 CQRS 模式,分离读写模型
 * </ul>
 *
 * <h2>包结构</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.domain.model.aggregate} - 聚合根包,
 *       定义一致性边界和业务不变性约束
 *   <li>{@link com.patra.registry.domain.model.vo} - 值对象根包,
 *       包含不可变的业务属性对象
 *       <ul>
 *         <li>{@link com.patra.registry.domain.model.vo.provenance} - 数据源配置值对象
 *         <li>{@link com.patra.registry.domain.model.vo.expr} - 表达式值对象
 *       </ul>
 *   <li>{@code com.patra.registry.domain.model.read} - CQRS 读模型包,
 *       提供查询优化的只读对象
 *       <ul>
 *         <li>{@code com.patra.registry.domain.model.read.provenance} - 数据源查询模型
 *         <li>{@code com.patra.registry.domain.model.read.expr} - 表达式查询模型
 *       </ul>
 * </ul>
 *
 * <h2>DDD 战术模式</h2>
 *
 * <ul>
 *   <li><strong>聚合根</strong>: {@link com.patra.registry.domain.model.aggregate.ProvenanceConfiguration},
 *       定义一致性边界,作为外部访问聚合的唯一入口
 *   <li><strong>值对象</strong>: {@link com.patra.registry.domain.model.vo.provenance.Provenance}、
 *       {@link com.patra.registry.domain.model.vo.provenance.HttpConfig} 等,
 *       封装业务属性和验证逻辑
 *   <li><strong>仓储接口</strong>: {@link com.patra.registry.domain.port.ProvenanceConfigRepository},
 *       定义在领域层,实现在基础设施层
 * </ul>
 *
 * <h2>CQRS 模式</h2>
 *
 * <p>本模块采用 CQRS(命令查询职责分离)模式,区分写模型和读模型:
 *
 * <ul>
 *   <li><strong>写模型</strong>: 聚合根和值对象({@code aggregate}、{@code vo}),
 *       包含业务逻辑和验证规则,用于命令处理
 *   <li><strong>读模型</strong>: 查询对象({@code read}),
 *       简化查询结构,无业务逻辑,用于查询优化
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>框架无关</strong>: 所有领域对象都是纯 Java 对象,不依赖 Spring、JPA 等框架
 *   <li><strong>不可变性</strong>: 值对象和聚合根使用 {@code record} 或 {@code final} 字段实现不可变性
 *   <li><strong>自验证</strong>: 领域对象在构造时自我验证,快速失败
 *   <li><strong>业务语言</strong>: 使用领域语言命名类和方法,反映业务概念
 *   <li><strong>一致性边界</strong>: 聚合根确保聚合内对象的一致性
 * </ul>
 *
 * <h2>核心领域概念</h2>
 *
 * <ul>
 *   <li><strong>Provenance(数据源)</strong>: 外部数据源的元数据,如 PubMed、Crossref
 *   <li><strong>Configuration(配置)</strong>: 多维度运营配置,包括 HTTP、重试、分页等
 *   <li><strong>时态配置</strong>: 所有配置都有生效时间范围,支持时态查询
 *   <li><strong>配置作用域</strong>: TASK > OPERATION > SOURCE 三级优先级
 *   <li><strong>表达式</strong>: 动态表达式系统,用于 API 请求生成和字段提取
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 创建值对象
 * Provenance pubmed = new Provenance(
 *     1L,
 *     "PUBMED",
 *     "PubMed",
 *     "https://eutils.ncbi.nlm.nih.gov",
 *     "UTC",
 *     null,
 *     true,
 *     "ACTIVE"
 * );
 *
 * // 创建聚合根
 * ProvenanceConfiguration config = new ProvenanceConfiguration(
 *     pubmed,
 *     windowOffset,
 *     pagination,
 *     http,
 *     batching,
 *     retry,
 *     rateLimit
 * );
 *
 * // 检查聚合完整性
 * if (config.isComplete()) {
 *     // 配置完整且数据源激活
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.domain.model;
