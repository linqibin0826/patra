package com.patra.objectstorage.app.recordupload;

import java.time.Instant;
import java.util.Map;

/**
 * 记录上传命令。
 *
 * <p>命令对象,描述需要记录的成功上传操作。封装了文件的存储位置、大小、校验和、 业务上下文等所有必需信息,用于在应用层创建文件元数据记录。
 *
 * <p>命令对象是不可变的,通过紧凑构造函数确保可变输入(如Map和数组)被防御性复制。
 *
 * @param bucketName 存储桶名称
 * @param objectKey 对象键
 * @param fileSize 文件大小(字节)
 * @param contentType MIME内容类型
 * @param md5Hash MD5哈希值
 * @param sha256Hash SHA-256哈希值
 * @param serviceName 服务名称
 * @param businessType 业务类型
 * @param businessId 业务ID
 * @param correlationData 关联数据
 * @param providerType 存储提供商类型
 * @param expiresAt 过期时间
 * @param ipAddress IP地址
 * @param recordRemarks 记录备注
 */
public record RecordUploadCommand(
    String bucketName,
    String objectKey,
    long fileSize,
    String contentType,
    String md5Hash,
    String sha256Hash,
    String serviceName,
    String businessType,
    String businessId,
    Map<String, Object> correlationData,
    String providerType,
    Instant expiresAt,
    byte[] ipAddress,
    String recordRemarks) {

  /** 紧凑构造函数,对可变输入进行防御性复制。 */
  public RecordUploadCommand {
    correlationData = correlationData == null ? Map.of() : Map.copyOf(correlationData);
    ipAddress = ipAddress == null ? null : ipAddress.clone();
  }
}
