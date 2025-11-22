package com.patra.objectstorage.api.dto;

import java.time.Instant;

/// 记录上传响应。
///
/// 存储服务在元数据持久化完成时返回的响应。包含生成的元数据ID和记录时间戳。
///
/// @param metadataId 元数据ID
/// @param recordedAt 记录时间
public record RecordUploadResponse(Long metadataId, Instant recordedAt) {}
