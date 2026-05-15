package dev.linqibin.patra.objectstorage.domain.port;

import dev.linqibin.patra.objectstorage.domain.model.aggregate.FileMetadata;
import dev.linqibin.patra.objectstorage.domain.model.vo.StorageKey;
import java.util.Optional;

/// 文件元数据仓储抽象。
///
/// 定义持久化 {@link FileMetadata} 聚合根的仓储接口,作为领域层的端口, 由基础设施层的仓储实现提供具体的持久化逻辑。
///
/// 遵循端口-适配器模式,领域层通过此接口与持久化机制解耦, 基础设施层负责实现数据库访问、ORM映射和事务管理。
public interface FileMetadataRepository {

  /// 持久化提供的聚合根。
  ///
  /// 保存新的或更新现有的文件元数据记录。实现需要处理:
  ///
  /// - 为新记录生成主键ID并通过 {@link FileMetadata#assignId} 回填
  ///   - 通过 {@link FileMetadata#updateVersion} 更新乐观锁版本号
  ///   - 确保 storage_key 唯一约束不被违反
  ///
  /// @param metadata 要存储的聚合根
  /// @return 持久化后的聚合根(已填充标识符和版本号)
  FileMetadata save(FileMetadata metadata);

  /// 通过规范存储键加载元数据。
  ///
  /// 根据存储键(bucket/objectKey组合)查询文件元数据记录, 用于实现上传记录的幂等性检查和文件查询功能。
  ///
  /// @param storageKey 存储桶/对象键元组
  /// @return 存在时返回元数据,否则返回空
  Optional<FileMetadata> findByStorageKey(StorageKey storageKey);
}
