/**
 * 外部系统集成适配器(以领域为中心的组织方式)。
 *
 * <p>此包包含与外部系统和限界上下文集成的基础设施适配器。每个子目录代表与特定外部系统的集成。
 *
 * <p>组织理念:
 *
 * <ul>
 *   <li><strong>以领域为中心</strong>: 按外部系统组织(PubMed、Registry、Storage)
 *   <li><strong>限界上下文</strong>: 每个外部系统被视为独立的限界上下文
 *   <li><strong>ACL 协同定位</strong>: 防腐层与消费适配器协同定位
 *   <li><strong>可扩展性</strong>: 为添加新数据源提供清晰的模式(预期 10+ 个数据源)
 * </ul>
 *
 * <p>结构:
 *
 * <ul>
 *   <li><strong>pubmed/</strong> - PubMed E-Utilities API 集成
 *   <li><strong>registry/</strong> - patra-registry 服务集成,用于配置
 *   <li><strong>storage/</strong> - patra-storage 服务和对象存储(S3/MinIO)集成
 * </ul>
 *
 * <p>命名约定:
 *
 * <ul>
 *   <li>适配器: {@code *Adapter.java}(例如 {@code PubmedSearchAdapter})
 *   <li>ACL 转换器: {@code acl/} 子目录中的 {@code *Converter.java}
 * </ul>
 *
 * @since 0.1.0
 */
package com.patra.ingest.infra.integration;
