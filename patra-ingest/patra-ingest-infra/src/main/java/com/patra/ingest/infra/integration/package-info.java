/// 外部系统集成适配器(以领域为中心的组织方式)。
/// 
/// 此包包含与外部系统和限界上下文集成的基础设施适配器。每个子目录代表与特定外部系统的集成。
/// 
/// 组织理念:
/// 
/// - **以领域为中心**: 按外部系统组织(PubMed、Registry、Storage)
///   - **限界上下文**: 每个外部系统被视为独立的限界上下文
///   - **ACL 协同定位**: 防腐层与消费适配器协同定位
///   - **可扩展性**: 为添加新数据源提供清晰的模式(预期 10+ 个数据源)
/// 
/// 结构:
/// 
/// - **pubmed/** - PubMed E-Utilities API 集成
///   - **registry/** - patra-registry 服务集成,用于配置
///   - **storage/** - patra-object-storage 服务和对象存储(S3/MinIO)集成
/// 
/// 命名约定:
/// 
/// - 适配器: `*Adapter.java`(例如 `PubmedSearchAdapter`)
///   - ACL 转换器: `acl/` 子目录中的 `*Converter.java`
/// 
/// @since 0.1.0
package com.patra.ingest.infra.integration;
