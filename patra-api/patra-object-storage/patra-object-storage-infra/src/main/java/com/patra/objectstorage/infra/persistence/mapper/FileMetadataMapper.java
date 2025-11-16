package com.patra.objectstorage.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.objectstorage.infra.persistence.entity.FileMetadataDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文件元数据MyBatis-Plus映射器。
 *
 * <p>为 <code>storage_file_metadata</code> 表提供数据访问接口。 继承 {@link BaseMapper} 提供标准的CRUD操作,并扩展了自定义查询方法。
 *
 * <p>对应的SQL映射文件位于 resources/mapper/FileMetadataMapper.xml。
 */
@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadataDO> {

  /**
   * 通过规范存储键加载记录。
   *
   * <p>根据唯一的存储键(bucket/objectKey组合)查询文件元数据记录, 用于实现幂等性检查和文件查询功能。
   *
   * @param storageKey 组合的bucket/objectKey值
   * @return 匹配的记录,如果不存在则返回 {@code null}
   */
  FileMetadataDO findByStorageKey(@Param("storageKey") String storageKey);
}
