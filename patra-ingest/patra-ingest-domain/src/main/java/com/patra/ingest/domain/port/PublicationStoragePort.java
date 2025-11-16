package com.patra.ingest.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.CanonicalPublication;
import java.util.List;
import lombok.Builder;

/**
 * 出版物存储端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>: 将出版物负载存储到对象存储(S3/MinIO)。此端口抽象了上传序列化出版物的技术细节,基础设施适配器负责:
 *
 * <ul>
 *   <li>将出版物序列化为 JSON 格式
 *   <li>计算文件校验和(MD5、SHA-256)
 *   <li>上传到对象存储并附加元数据
 *   <li>返回存储位置和文件信息
 * </ul>
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>输出端口(Output Port)</b>,定义在 Domain
 * 层,由基础设施层(Infrastructure)实现,确保领域逻辑与存储技术解耦。
 */
public interface PublicationStoragePort {

  /**
   * 批量存储标准化出版物到对象存储。
   *
   * <p><b>业务含义</b>: 将领域归一化的出版物列表持久化到对象存储。
   *
   * @param publication 领域归一化的出版物列表
   * @param context 存储上下文,包含执行元数据
   * @return 存储结果,包含位置和校验和
   */
  StorageResult store(List<CanonicalPublication> publication, StorageContext context);

  /**
   * 存储结果,包含文件位置和完整性信息。
   *
   * @param storageKey 完整存储标识符(bucket/key 组合)
   * @param bucketName 对象存储桶名称
   * @param objectKey 桶内对象键
   * @param fileSize 文件大小(字节)
   * @param md5 MD5 校验和(十六进制格式)
   * @param sha256 SHA-256 校验和(十六进制格式)
   * @param publicationCount 存储的出版物数量
   */
  @Builder
  record StorageResult(
      String storageKey,
      String bucketName,
      String objectKey,
      long fileSize,
      String md5,
      String sha256,
      int publicationCount) {}

  /**
   * 存储上下文,提供文件组织的执行元数据。
   *
   * @param runId 任务 run 标识符
   * @param batchNo 执行批次编号
   * @param provenanceCode 归一化的数据源标识符
   */
  @Builder
  record StorageContext(Long runId, int batchNo, ProvenanceCode provenanceCode) {}
}
