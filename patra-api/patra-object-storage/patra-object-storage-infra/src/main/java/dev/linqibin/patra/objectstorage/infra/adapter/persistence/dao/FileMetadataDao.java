package dev.linqibin.patra.objectstorage.infra.adapter.persistence.dao;

import dev.linqibin.patra.objectstorage.infra.adapter.persistence.entity.FileMetadataEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// 文件元数据 JPA Repository。
///
/// 为 `storage_file_metadata` 表提供数据访问接口。
/// 继承 `JpaRepository` 提供标准 CRUD 操作，并扩展自定义查询方法。
public interface FileMetadataDao extends JpaRepository<FileMetadataEntity, Long> {

  /// 通过规范存储键加载记录。
  ///
  /// 根据唯一的存储键（bucket/objectKey 组合）查询文件元数据记录，
  /// 用于实现幂等性检查和文件查询功能。
  ///
  /// @param storageKey 组合的 bucket/objectKey 值
  /// @return 匹配的记录，如果不存在则返回空 Optional
  Optional<FileMetadataEntity> findByStorageKey(String storageKey);
}
