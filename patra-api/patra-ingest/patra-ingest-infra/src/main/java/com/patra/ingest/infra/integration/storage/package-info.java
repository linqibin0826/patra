/// patra-object-storage 服务和对象存储集成适配器。
/// 
/// 此包包含用于集成的适配器:
/// 
/// - **patra-object-storage** 服务 - 元数据记录服务
///   - **对象存储** - 用于出版物 JSON 文件存储的 S3/MinIO
/// 
/// ## 关键组件
/// 
/// - {@link com.patra.ingest.infra.integration.storage.PublicationStorageAdapter} - 实现 {@link
///       com.patra.ingest.domain.port.PublicationStoragePort} 直接存储 {@link
///       com.patra.common.model.CanonicalPublication} 到对象存储 (S3/MinIO)
///   - {@link com.patra.ingest.infra.integration.storage.StorageMetadataAdapter} - 实现 {@link
///       com.patra.ingest.domain.port.StorageMetadataPort} 通过 patra-object-storage 服务记录元数据
/// 
/// ## 架构决策（2025-01-16）
/// 
/// **使用共享内核模型**:
/// 
/// - ✅ 直接存储共享内核模型 {@link com.patra.common.model.CanonicalPublication}
///   - ✅ 避免引入额外的 DTO 转换层
///   - ✅ 简化存储逻辑，保证数据与业务模型完全一致
/// 
/// **存储格式**: JSON 序列化的 `List<CanonicalPublication>`
/// 
/// @since 0.1.0
package com.patra.ingest.infra.integration.storage;
