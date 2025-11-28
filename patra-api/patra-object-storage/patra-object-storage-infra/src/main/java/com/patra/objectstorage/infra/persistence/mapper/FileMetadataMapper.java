package com.patra.objectstorage.infra.persistence.mapper;

import com.patra.objectstorage.infra.persistence.entity.FileMetadataDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/// 文件元数据MyBatis-Plus映射器。
///
/// 为 `storage_file_metadata` 表提供数据访问接口。 继承 {@link PatraBaseMapper} 提供标准的CRUD操作,并扩展了自定义查询方法。
///
/// 对应的SQL映射文件位于 resources/mapper/FileMetadataMapper.xml。
@Mapper
public interface FileMetadataMapper extends PatraBaseMapper<FileMetadataDO> {

  /// 通过规范存储键加载记录。
  ///
  /// 根据唯一的存储键(bucket/objectKey组合)查询文件元数据记录, 用于实现幂等性检查和文件查询功能。
  ///
  /// @param storageKey 组合的bucket/objectKey值
  /// @return 匹配的记录,如果不存在则返回 `null`
  FileMetadataDO findByStorageKey(@Param("storageKey") String storageKey);
}
