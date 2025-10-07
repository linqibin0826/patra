package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.vo.StorageUploadRequest;
import com.patra.ingest.domain.model.vo.StorageUploadResult;

/**
 * 对象存储适配器端口（Port）。
 * <p>封装对象存储（MinIO/S3）的上传能力。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface StorageAdapter {

    /**
     * 上传数据到对象存储。
     *
     * @param request 上传请求
     * @return 上传结果（包含对象路径、大小等）
     */
    StorageUploadResult upload(StorageUploadRequest request);

    /**
     * 生成对象存储路径。
     * <p>路径格式：{bucket}/{provenanceCode-lower}/{yyyy}/{MM}/run_{runId}/batch_{batchNo(三位补零)}.json.gz</p>
     *
     * @param provenanceCode 数据源编码
     * @param runId 运行ID
     * @param batchNo 批次号
     * @return 对象路径（不含bucket）
     */
    String generateObjectPath(String provenanceCode, Long runId, int batchNo);
}
