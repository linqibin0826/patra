package dev.linqibin.patra.objectstorage.infra.adapter.persistence;

import dev.linqibin.patra.objectstorage.domain.model.aggregate.FileMetadata;
import dev.linqibin.patra.objectstorage.domain.model.vo.StorageKey;
import dev.linqibin.patra.objectstorage.domain.port.FileMetadataRepository;
import dev.linqibin.patra.objectstorage.infra.adapter.persistence.converter.mapper.FileMetadataJpaMapper;
import dev.linqibin.patra.objectstorage.infra.adapter.persistence.dao.FileMetadataDao;
import dev.linqibin.patra.objectstorage.infra.adapter.persistence.entity.FileMetadataEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 文件元数据仓储实现。
///
/// 基础设施层的仓储实现，使用 Spring Data JPA 作为 ORM 框架，实现领域层定义的 {@link FileMetadataRepository}
/// 端口。负责聚合根的持久化、查询和领域对象与 JPA 实体之间的转换。
///
/// 实现要点：
///
/// - 使用转换器（{@link FileMetadataJpaMapper}）在领域模型和 JPA 实体之间进行双向转换
/// - 新建实体时使用雪花 ID 预分配，保持与六边形架构设计一致
/// - JPA 自动管理审计字段（createdAt、updatedAt 等）和乐观锁版本号
/// - 查询操作通过唯一的 storage_key 字段实现幂等性检查
@Repository
@RequiredArgsConstructor
public class FileMetadataRepositoryAdapter implements FileMetadataRepository {

  private final FileMetadataDao dao;
  private final FileMetadataJpaMapper mapper;

  /// 保存文件元数据聚合根。
  ///
  /// 新建实体时预分配雪花 ID，JPA 自动管理审计字段和版本号。
  /// 保存后返回包含数据库生成字段的聚合根。
  ///
  /// @param metadata 文件元数据聚合根
  /// @return 保存后的聚合根，包含数据库生成的字段
  @Override
  public FileMetadata save(FileMetadata metadata) {
    FileMetadataEntity entity = mapper.toEntity(metadata);
    // 新建实体时预分配雪花 ID
    if (metadata.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    } else {
      entity.setId(metadata.getId());
      entity.setVersion(metadata.getVersion());
    }
    FileMetadataEntity saved = dao.save(entity);
    return mapper.toAggregate(saved);
  }

  /// 通过存储键查找文件元数据。
  ///
  /// 使用唯一的 storage_key 字段查找文件元数据记录，实现幂等性检查。
  ///
  /// @param storageKey 存储键
  /// @return 文件元数据聚合根的 Optional 包装
  @Override
  public Optional<FileMetadata> findByStorageKey(StorageKey storageKey) {
    return dao.findByStorageKey(storageKey.fullKey()).map(mapper::toAggregate);
  }
}
