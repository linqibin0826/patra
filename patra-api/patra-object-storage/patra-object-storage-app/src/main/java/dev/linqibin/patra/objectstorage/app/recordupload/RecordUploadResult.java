package dev.linqibin.patra.objectstorage.app.recordupload;

import java.time.Instant;

/// 记录上传结果。
///
/// 结果对象,在元数据持久化成功后返回。包含生成的元数据ID和记录时间戳, 用于向调用方确认记录操作已完成并提供记录标识。
///
/// @param metadataId 生成的元数据ID
/// @param recordedAt 记录时间戳
public record RecordUploadResult(Long metadataId, Instant recordedAt) {}
