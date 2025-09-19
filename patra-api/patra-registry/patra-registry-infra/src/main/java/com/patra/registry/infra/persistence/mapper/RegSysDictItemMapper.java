package com.patra.registry.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.RegSysDictItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for dictionary item read operations.
 * Provides data access methods for sys_dict_item table in the CQRS query pipeline.
 * All methods are read-only and optimized for dictionary query performance.
 * Uses v_sys_dict_item_enabled view for optimized enabled item queries.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface RegSysDictItemMapper extends BaseMapper<RegSysDictItemDO> {

    /**
     * Find dictionary item by type code and item code using optimized view.
     * Uses v_sys_dict_item_enabled view for performance optimization.
     * 
     * @param typeCode the dictionary type code, must not be null
     * @param itemCode the dictionary item code, must not be null
     * @return Optional containing the dictionary item if found, enabled, and not deleted, empty otherwise
     */
    @Select("""
            SELECT di.* FROM sys_dict_item di
            JOIN sys_dict_type dt ON dt.id = di.type_id
            WHERE dt.type_code = #{typeCode} 
              AND di.item_code = #{itemCode}
              AND di.enabled = 1 
              AND di.deleted = 0 
              AND dt.deleted = 0
            """)
    Optional<RegSysDictItemDO> selectByTypeAndItemCode(@Param("typeCode") String typeCode, 
                                                       @Param("itemCode") String itemCode);

    /**
     * Find all enabled dictionary items for a specific type.
     * Returns items sorted by display_order ascending, then by item_code ascending.
     * 
     * @param typeCode the dictionary type code, must not be null
     * @return List of enabled dictionary items for the specified type, properly sorted
     */
    @Select("""
            SELECT di.* FROM sys_dict_item di
            JOIN sys_dict_type dt ON dt.id = di.type_id
            WHERE dt.type_code = #{typeCode}
              AND di.enabled = 1 
              AND di.deleted = 0 
              AND dt.deleted = 0
            ORDER BY di.display_order ASC, di.item_code ASC
            """)
    List<RegSysDictItemDO> selectEnabledByTypeCode(@Param("typeCode") String typeCode);

    /**
     * Find the default dictionary item for a specific type.
     * Uses the is_default flag and business constraints to locate the default item.
     * 
     * @param typeCode the dictionary type code, must not be null
     * @return Optional containing the default dictionary item if exists, empty otherwise
     */
    @Select("""
            SELECT di.* FROM sys_dict_item di
            JOIN sys_dict_type dt ON dt.id = di.type_id
            WHERE dt.type_code = #{typeCode}
              AND di.is_default = 1
              AND di.enabled = 1 
              AND di.deleted = 0 
              AND dt.deleted = 0
            LIMIT 1
            """)
    Optional<RegSysDictItemDO> selectDefaultByTypeCode(@Param("typeCode") String typeCode);

    /**
     * Find dictionary items by type ID for internal operations.
     * Used when type ID is already known to avoid additional joins.
     * 
     * @param typeId the dictionary type ID, must not be null
     * @return List of enabled dictionary items for the specified type ID
     */
    @Select("""
            SELECT * FROM sys_dict_item 
            WHERE type_id = #{typeId}
              AND enabled = 1 
              AND deleted = 0
            ORDER BY display_order ASC, item_code ASC
            """)
    List<RegSysDictItemDO> selectEnabledByTypeId(@Param("typeId") Long typeId);

    /**
     * Count enabled items for a specific dictionary type.
     * Used for system health monitoring and metadata operations.
     * 
     * @param typeCode the dictionary type code, must not be null
     * @return count of enabled items for the specified type
     */
    @Select("""
            SELECT COUNT(*) FROM sys_dict_item di
            JOIN sys_dict_type dt ON dt.id = di.type_id
            WHERE dt.type_code = #{typeCode}
              AND di.enabled = 1 
              AND di.deleted = 0 
              AND dt.deleted = 0
            """)
    int countEnabledByTypeCode(@Param("typeCode") String typeCode);

    /**
     * Find types with multiple default items (data integrity check).
     * Used for system health monitoring to detect constraint violations.
     * 
     * @return List of type codes that have more than one default item
     */
    @Select("""
            SELECT dt.type_code FROM sys_dict_item di
            JOIN sys_dict_type dt ON dt.id = di.type_id
            WHERE di.is_default = 1 
              AND di.enabled = 1 
              AND di.deleted = 0 
              AND dt.deleted = 0
            GROUP BY dt.type_code, di.type_id
            HAVING COUNT(*) > 1
            """)
    List<String> selectTypesWithMultipleDefaults();

    /**
     * Find types without any default items.
     * Used for system health monitoring to identify configuration gaps.
     * 
     * @return List of type codes that have no default items
     */
    @Select("""
            SELECT dt.type_code FROM sys_dict_type dt
            LEFT JOIN sys_dict_item di ON dt.id = di.type_id 
              AND di.is_default = 1 
              AND di.enabled = 1 
              AND di.deleted = 0
            WHERE dt.deleted = 0 
              AND di.id IS NULL
            """)
    List<String> selectTypesWithoutDefaults();

    /**
     * Count total enabled dictionary items across all types.
     * Used for system health monitoring and statistics.
     * 
     * @return total count of enabled dictionary items
     */
    @Select("SELECT COUNT(*) FROM sys_dict_item WHERE enabled = 1 AND deleted = 0")
    int countTotalEnabled();

    /**
     * Count total dictionary items (including disabled) across all types.
     * Used for system health monitoring and statistics.
     * 
     * @return total count of all dictionary items (excluding deleted)
     */
    @Select("SELECT COUNT(*) FROM sys_dict_item WHERE deleted = 0")
    int countTotal();
}