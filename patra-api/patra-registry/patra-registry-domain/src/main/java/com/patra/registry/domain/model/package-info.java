/// Registry 领域模型根包 - DDD 战术设计核心。
/// 
/// 本包是 Registry 服务领域模型的根包,包含聚合根、值对象和读模型的组织结构。 领域模型是业务逻辑的核心,封装了 Registry 服务的业务规则、不变性约束和领域概念。
/// 
/// ## 职责
/// 
/// - 定义领域模型的包结构,按 DDD 模式组织(聚合根、值对象、读模型)
///   - 封装业务逻辑和领域知识
///   - 强制执行业务不变性约束
///   - 提供纯粹的领域对象,不依赖框架
///   - 支持 CQRS 模式,分离读写模型
/// 
/// ## 包结构
/// 
/// - {@link com.patra.registry.domain.model.aggregate} - 聚合根包, 定义一致性边界和业务不变性约束
///   - {@link com.patra.registry.domain.model.vo} - 值对象根包, 包含不可变的业务属性对象
///       
/// - {@link com.patra.registry.domain.model.vo.provenance} - 数据源配置值对象
///         - {@link com.patra.registry.domain.model.vo.expr} - 表达式值对象
/// 
///   - `com.patra.registry.domain.model.read` - CQRS 读模型包, 提供查询优化的只读对象
///       
/// - `com.patra.registry.domain.model.read.provenance` - 数据源查询模型
///         - `com.patra.registry.domain.model.read.expr` - 表达式查询模型
/// 
/// ## DDD 战术模式
/// 
/// - **聚合根**: {@link
///       com.patra.registry.domain.model.aggregate.ProvenanceConfiguration}, 定义一致性边界,作为外部访问聚合的唯一入口
///   - **值对象**: {@link com.patra.registry.domain.model.vo.provenance.Provenance}、
///       {@link com.patra.registry.domain.model.vo.provenance.HttpConfig} 等, 封装业务属性和验证逻辑
///   - **仓储接口**: {@link com.patra.registry.domain.port.ProvenanceConfigRepository},
///       定义在领域层,实现在基础设施层
/// 
/// ## CQRS 模式
/// 
/// 本模块采用 CQRS(命令查询职责分离)模式,区分写模型和读模型:
/// 
/// - **写模型**: 聚合根和值对象(`aggregate`、`vo`), 包含业务逻辑和验证规则,用于命令处理
///   - **读模型**: 查询对象(`read`), 简化查询结构,无业务逻辑,用于查询优化
/// 
/// ## 设计原则
/// 
/// - **框架无关**: 所有领域对象都是纯 Java 对象,不依赖 Spring、JPA 等框架
///   - **不可变性**: 值对象和聚合根使用 `record` 或 `final` 字段实现不可变性
///   - **自验证**: 领域对象在构造时自我验证,快速失败
///   - **业务语言**: 使用领域语言命名类和方法,反映业务概念
///   - **一致性边界**: 聚合根确保聚合内对象的一致性
/// 
/// ## 核心领域概念
/// 
/// - **Provenance(数据源)**: 外部数据源的元数据,如 PubMed、Crossref
///   - **Configuration(配置)**: 多维度运营配置,包括 HTTP、重试、分页等
///   - **时态配置**: 所有配置都有生效时间范围,支持时态查询
///   - **配置作用域**: TASK > OPERATION > SOURCE 三级优先级
///   - **表达式**: 动态表达式系统,用于 API 请求生成和字段提取
/// 
/// ## 使用示例
/// 
/// ```java
/// // 创建值对象
/// Provenance pubmed = new Provenance(
///     1L,
///     "PUBMED",
///     "PubMed",
///     "https://eutils.ncbi.nlm.nih.gov",
///     "UTC",
///     null,
///     true,
///     "ACTIVE"
/// );
/// 
/// // 创建聚合根
/// ProvenanceConfiguration config = new ProvenanceConfiguration(
///     pubmed,
///     windowOffset,
///     pagination,
///     http,
///     batching,
///     retry,
///     rateLimit
/// );
/// 
/// // 检查聚合完整性
/// if (config.isComplete()) {
///     // 配置完整且数据源激活
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.domain.model;
