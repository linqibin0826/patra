package com.patra.registry.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.RegSysDictTypeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for dictionary type read operations.
 * Provides data access methods for sys_dict_type table in the CQRS query pipeline.
 * All methods are read-only and optimized for dictionary query performance.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface RegSysDictTypeMapper extends BaseMapper<RegSysDictTypeDO> {

    /**
     * Find dictionary type by type code.
     * Uses the stable business key (type_code) to locate dictionary types.
     * 
     * @param typeCode the dictionary type code to search for, must not be null
     * @return Optional containing the dictionary type if found and not deleted, empty otherwise
     */
    @Select("SELECT * FROM sys_dict_type WHERE type_code = #{typeCode} AND deleted = 0")
    Optional<RegSysDictTypeDO> selectByTypeCode(@Param("typeCode") String typeCode);

    /**
     * Find all enabled dictionary types ordered by type code.
     * Returns only non-deleted dictionary types for system health and metadata operations.
     * 
     * @return List of all enabled dictionary types, ordered alphabetically by type_code
     */
    @Select("SELECT * FROM sys_dict_type WHERE deleted = 0 ORDER BY type_code")
    List<RegSysDictTypeDO> selectAllEnabled();

    /**
     * Count total number of dictionary types.
     * Used for system health monitoring and statistics.
     * 
     * @return total count of non-deleted dictionary types
     */
    @Select("SELECT COUNT(*) FROM sys_dict_type WHERE deleted = 0")
    int countTotal();

    /**
     * Find dictionary types that allow custom items.
     * Used for administrative operations and system configuration validation.
     * 
     * @return List of dictionary types where allow_custom_items = 1 and not deleted
     */
    @Select("SELECT * FROM sys_dict_type WHERE allow_custom_items = 1 AND deleted = 0 ORDER BY type_code")
    List<RegSysDictTypeDO> selectCustomizableTypes();

    /**
     * Find system-managed dictionary types.
     * Used for identifying platform-managed dictionaries that should not be modified by business users.
     * 
     * @return List of dictionary types where is_system = 1 and not deleted
     */
    @Select("SELECT * FROM sys_dict_type WHERE is_system = 1 AND deleted = 0 ORDER BY type_code")
    List<RegSysDictTypeDO> selectSystemTypes();
}