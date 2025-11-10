package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.vo.storage.StorageUploadRequest;
import com.patra.ingest.domain.model.vo.storage.StorageUploadResult;

/**
 * 对象存储适配器端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>: 与对象存储(MinIO/S3)交互,提供:
 *
 * <ul>
 *   <li>上传能力
 *   <li>标准化的对象路径生成
 * </ul>
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>输出端口(Output Port)</b>,定义在 Domain
 * 层,由基础设施层(Infrastructure)实现,抽象对象存储技术细节。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface StoragePort {

  /**
   * 上传数据到对象存储。
   *
   * <p><b>业务含义</b>: 将数据上传到配置的对象存储服务。
   *
   * @param request 上传请求负载
   * @return 结果,包含对象路径、大小和元数据
   */
  StorageUploadResult upload(StorageUploadRequest request);

  /**
   * 生成对象存储路径。
   *
   * <p><b>路径模式</b>: {@code
   * {bucket}/{provenanceCode-lower}/{yyyy}/{MM}/run_{runId}/batch_{batchNo(000)}.json.gz}
   *
   * @param provenanceCode Provenance 代码
   * @param runId run 标识符
   * @param batchNo 批次编号
   * @return 对象键(不包含 bucket)
   */
  String generateObjectPath(String provenanceCode, Long runId, int batchNo);
}
