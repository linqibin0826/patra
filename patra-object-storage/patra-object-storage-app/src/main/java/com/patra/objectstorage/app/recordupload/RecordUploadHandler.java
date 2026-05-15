package com.patra.objectstorage.app.recordupload;

import com.patra.objectstorage.domain.model.aggregate.FileMetadata;
import com.patra.objectstorage.domain.model.enums.StorageProvider;
import com.patra.objectstorage.domain.model.vo.BusinessContext;
import com.patra.objectstorage.domain.model.vo.FileChecksum;
import com.patra.objectstorage.domain.model.vo.FileSize;
import com.patra.objectstorage.domain.model.vo.StorageKey;
import com.patra.objectstorage.domain.port.FileMetadataRepository;
import dev.linqibin.commons.cqrs.CommandHandler;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 记录上传命令处理器。
///
/// 应用层命令处理器，负责处理"记录文件上传"命令。当文件上传到对象存储后，
/// 此处理器接收来自 CommandBus 的命令，创建领域对象，并通过仓储持久化元数据。
///
/// 处理器的职责：
///
/// - 验证输入命令
/// - 将命令数据转换为领域对象（聚合根、值对象）
/// - 调用仓储保存聚合根
/// - 记录审计日志
/// - 返回结果对象
///
/// 事务边界在此处定义，确保元数据记录的原子性。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordUploadHandler
    implements CommandHandler<RecordUploadCommand, RecordUploadResult> {

  private final FileMetadataRepository repository;

  /// 处理记录上传命令。
  ///
  /// 接收来自 CommandBus 的上传记录命令，创建文件元数据聚合根并持久化到数据库。
  /// 操作在事务中执行，确保元数据记录的一致性和完整性。
  ///
  /// @param command 来自适配器的已验证上传描述
  /// @return 持久化结果载荷
  @Override
  @Transactional
  public RecordUploadResult handle(RecordUploadCommand command) {
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
