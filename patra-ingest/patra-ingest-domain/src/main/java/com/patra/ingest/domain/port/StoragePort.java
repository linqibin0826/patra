package com.patra.ingest.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.storage.StorageUploadRequest;
import com.patra.ingest.domain.model.vo.storage.StorageUploadResult;

/// 对象存储适配器端口(六边形架构 - Domain → Infrastructure)。
/// 
/// **职责**: 与对象存储(MinIO/S3)交互,提供:
/// 
/// - 上传能力
///   - 标准化的对象路径生成
/// 
/// **端口语义**: 此接口是六边形架构中的 **输出端口(Output Port)**,定义在 Domain
/// 层,由基础设施层(Infrastructure)实现,抽象对象存储技术细节。
/// 
/// @author linqibin
/// @since 0.1.0
public interface StoragePort {

  /// 上传数据到对象存储。
/// 
/// **业务含义**: 将数据上传到配置的对象存储服务。
/// 
/// @param request 上传请求负载
/// @return 结果,包含对象路径、大小和元数据
  StorageUploadResult upload(StorageUploadRequest request);

  /// 生成对象存储路径。
/// 
/// **路径模式**: `{bucket`/{provenanceCode-lower}/{yyyy}/{MM}/run_{runId}/batch_{batchNo(000)}.json.gz}
/// 
/// @param provenanceCode Provenance 代码
/// @param runId run 标识符
/// @param batchNo 批次编号
/// @return 对象键(不包含 bucket)
  String generateObjectPath(ProvenanceCode provenanceCode, Long runId, int batchNo);
}
