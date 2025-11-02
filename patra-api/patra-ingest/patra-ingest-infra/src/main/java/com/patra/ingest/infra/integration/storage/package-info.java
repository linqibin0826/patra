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
 * <p>关键组件:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.infra.integration.storage.LiteratureStorageAdapter} - 实现 {@link
 *       com.patra.ingest.domain.port.LiteratureStoragePort} 将文献存储到对象存储 (S3/MinIO)
 *   <li>{@link com.patra.ingest.infra.integration.storage.StorageMetadataAdapter} - 实现 {@link
 *       com.patra.ingest.domain.port.StorageMetadataPort} 通过 patra-storage 服务记录元数据
 *   <li>{@code acl/} - 防腐层 (ACL) 用于将领域模型转换为目录 API DTO
 * </ul>
 *
 * @since 0.1.0
 */
package com.patra.ingest.infra.integration.storage;
