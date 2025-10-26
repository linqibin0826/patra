package com.patra.storage.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.storage.infra.persistence.entity.FileMetadataDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis-Plus mapper for <code>storage_file_metadata</code> table. */
@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadataDO> {

  /**
   * Loads a record by its canonical storage key.
   *
   * @param storageKey combined bucket/objectKey value
   * @return matching record or {@code null}
   */
  FileMetadataDO findByStorageKey(@Param("storageKey") String storageKey);
}
