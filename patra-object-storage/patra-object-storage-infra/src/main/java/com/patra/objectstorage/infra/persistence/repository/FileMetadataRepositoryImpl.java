package com.patra.objectstorage.infra.persistence.repository;

import com.patra.objectstorage.domain.model.aggregate.FileMetadata;
import com.patra.objectstorage.domain.model.vo.StorageKey;
import com.patra.objectstorage.domain.port.FileMetadataRepository;
import com.patra.objectstorage.infra.persistence.converter.FileMetadataConverter;
import com.patra.objectstorage.infra.persistence.entity.FileMetadataDO;
import com.patra.objectstorage.infra.persistence.mapper.FileMetadataMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 文件元数据仓储实现。
///
/// 基础设施层的仓储实现,使用MyBatis-Plus作为ORM框架,实现领域层定义的 {@link FileMetadataRepository}
/// 端口。负责聚合根的持久化、查询和领域对象与数据对象之间的转换。
///
/// 实现要点:
///
/// - 使用转换器({@link FileMetadataConverter})在领域模型和数据模型之间进行双向转换
///   - 保存操作根据聚合根是否有ID判断执行insert还是update
///   - 保存后重新查询以获取数据库生成的字段(如ID、version、审计时间戳)
///   - 查询操作通过唯一的storage_key字段实现幂等性检查
///
@Repository
@RequiredArgsConstructor
public class FileMetadataRepositoryImpl implements FileMetadataRepository {

  private final FileMetadataMapper mapper;
  private final FileMetadataConverter converter;

  /// 保存文件元数据聚合根。
  ///
  /// 根据聚合根是否有ID判断执行insert还是update操作,保存后重新查询以获取数据库生成的字段。
  ///
  /// @param metadata 文件元数据聚合根
  /// @return 保存后的聚合根,包含数据库生成的字段
  @Override
  public FileMetadata save(FileMetadata metadata) {
    FileMetadataDO dataObject = converter.toDO(metadata);
    if (metadata.getId() == null) {
      mapper.insert(dataObject);
    } else {
      mapper.updateById(dataObject);
    }
    FileMetadataDO persisted = mapper.selectById(dataObject.getId());
    return converter.toAggregate(persisted);
  }

  /// 通过存储键查找文件元数据。
  ///
  /// 使用唯一的storage_key字段查找文件元数据记录,实现幂等性检查。
  ///
  /// @param storageKey 存储键
  /// @return 文件元数据聚合根的Optional包装
  @Override
  public Optional<FileMetadata> findByStorageKey(StorageKey storageKey) {
    FileMetadataDO dataObject = mapper.findByStorageKey(storageKey.fullKey());
    return Optional.ofNullable(converter.toAggregate(dataObject));
  }
}
