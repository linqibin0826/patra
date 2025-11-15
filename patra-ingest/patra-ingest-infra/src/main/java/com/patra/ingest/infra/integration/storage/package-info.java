/**
 * patra-storage 服务和对象存储集成适配器。
 *
 * <p>此包包含用于集成的适配器:
 *
 * <ul>
 *   <li><strong>patra-storage</strong> 服务 - 元数据记录服务
 *   <li><strong>对象存储</strong> - 用于文献 JSON 文件存储的 S3/MinIO
 * </ul>
 *
 * <h2>关键组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.infra.integration.storage.LiteratureStorageAdapter} - 实现 {@link
 *       com.patra.ingest.domain.port.LiteratureStoragePort} 直接存储 {@link
 *       com.patra.common.model.CanonicalLiterature} 到对象存储 (S3/MinIO)
 *   <li>{@link com.patra.ingest.infra.integration.storage.StorageMetadataAdapter} - 实现 {@link
 *       com.patra.ingest.domain.port.StorageMetadataPort} 通过 patra-storage 服务记录元数据
 * </ul>
 *
 * <h2>架构决策（2025-01-16）</h2>
 *
 * <p><strong>移除 ACL 转换层</strong>:
 *
 * <ul>
 *   <li>✅ 直接存储共享内核模型 {@link com.patra.common.model.CanonicalLiterature}
 *   <li>✅ 移除 patra-catalog-api DTO 依赖（LiteratureDTO, AuthorDTO, JournalDTO）
 *   <li>✅ 简化存储逻辑，保证数据与业务模型完全一致
 * </ul>
 *
 * <p><strong>存储格式</strong>: JSON 序列化的 {@code List<CanonicalLiterature>}
 *
 * @since 0.1.0
 */
package com.patra.ingest.infra.integration.storage;
