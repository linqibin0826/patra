package com.patra.objectstorage.app.recordupload;

import com.patra.objectstorage.domain.model.aggregate.FileMetadata;
import com.patra.objectstorage.domain.model.enums.StorageProvider;
import com.patra.objectstorage.domain.model.vo.BusinessContext;
import com.patra.objectstorage.domain.model.vo.FileChecksum;
import com.patra.objectstorage.domain.model.vo.FileSize;
import com.patra.objectstorage.domain.model.vo.StorageKey;
import com.patra.objectstorage.domain.port.FileMetadataRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 记录上传编排器。
///
/// 应用层服务,负责编排"记录文件上传"用例的执行。当文件上传到对象存储后, 此编排器接收来自适配器层的命令,创建领域对象,并通过仓储持久化元数据。
///
/// 编排器的职责:
///
/// - 验证输入命令
///   - 将命令数据转换为领域对象(聚合根、值对象)
///   - 调用仓储保存聚合根
///   - 记录审计日志
///   - 返回结果对象
///
/// 事务边界在此处定义,确保元数据记录的原子性。
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordUploadOrchestrator {

  private final FileMetadataRepository repository;

  /// 执行记录上传用例。
  ///
  /// 接收来自适配器层的上传记录命令,创建文件元数据聚合根并持久化到数据库。 操作在事务中执行,确保元数据记录的一致性和完整性。
  ///
  /// @param command 来自适配器的已验证上传描述
  /// @return 持久化结果载荷
  @Transactional
  public RecordUploadResult execute(RecordUploadCommand command) {
    Objects.requireNonNull(command, "command 不能为 null");

    FileMetadata metadata =
        FileMetadata.create(
                new StorageKey(command.bucketName(), command.objectKey()),
                new FileSize(command.fileSize()),
                new FileChecksum(command.md5Hash(), command.sha256Hash()),
                new BusinessContext(
                    command.serviceName(),
                    command.businessType(),
                    command.businessId(),
                    command.correlationData()),
                StorageProvider.fromName(command.providerType()))
            .withContentType(command.contentType())
            .withExpiresAt(command.expiresAt())
            .withRecordRemarks(command.recordRemarks())
            .withIpAddress(command.ipAddress());

    FileMetadata saved = repository.save(metadata);
    log.info("文件元数据已记录: id={}, storageKey={}", saved.getId(), saved.getStorageKey().fullKey());
    return new RecordUploadResult(saved.getId(), saved.getUploadedAt());
  }
}
