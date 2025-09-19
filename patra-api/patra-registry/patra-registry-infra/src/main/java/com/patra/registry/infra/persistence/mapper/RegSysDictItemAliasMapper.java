package com.patra.registry.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.RegSysDictItemAliasDO;
import com.patra.registry.infra.persistence.entity.RegSysDictItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for dictionary item alias read operations.
 * Provides data access methods for sys_dict_item_alias table in the CQRS query pipeline.
 * All methods are read-only and support external system integration through alias mappings.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface RegSysDictItemAliasMapper extends BaseMapper<RegSysDictItemAliasDO> {

    /**
     * Find dictionary item by external system alias.
     * Resolves external codes to internal dictionary items for system integration.
     * 
     * @param sourceSystem the external system identifier, must not be null
     * @param externalCode the external system's code, must not be null
     * @return Optional containing the mapped dictionary item if found and enabled, empty otherwise
     */
    @Select("""
            SELECT di.* FROM sys_dict_item di
            JOIN sys_dict_item_alias dia ON dia.item_id = di.id
            JOIN sys_dict_type dt ON dt.id = di.type_id
            WHERE dia.source_system = #{sourceSystem}
              AND dia.external_code = #{externalCode}
              AND di.enabled = 1 
              AND di.deleted = 0 
              AND dt.deleted = 0
              AND dia.deleted = 0
            """)
    Optional<RegSysDictItemDO> selectItemByAlias(@Param("sourceSystem") String sourceSystem, 
                                                 @Param("externalCode") String externalCode);

    /**
     * Find all aliases for a specific dictionary item.
     * Used for understanding external system mappings and integration documentation.
     * 
     * @param itemId the dictionary item ID, must not be null
     * @return List of aliases for the specified dictionary item
     */
    @Select("""
            SELECT * FROM sys_dict_item_alias 
            WHERE item_id = #{itemId} 
              AND deleted = 0
            ORDER BY source_system, external_code
            """)
    List<RegSysDictItemAliasDO> selectByItemId(@Param("itemId") Long itemId);

    /**
     * Find aliases by source system.
     * Used for system-specific integration and migration operations.
     * 
     * @param sourceSystem the external system identifier, must not be null
     * @return List of aliases from the specified source system
     */
    @Select("""
            SELECT * FROM sys_dict_item_alias 
            WHERE source_system = #{sourceSystem} 
              AND deleted = 0
            ORDER BY external_code
            """)
    List<RegSysDictItemAliasDO> selectBySourceSystem(@Param("sourceSystem") String sourceSystem);

    /**
     * Find alias by exact source system and external code combination.
     * Used for validating alias uniqueness and direct alias lookups.
     * 
     * @param sourceSystem the external system identifier, must not be null
     * @param externalCode the external system's code, must not be null
     * @return Optional containing the alias if found, empty otherwise
     */
    @Select("""
            SELECT * FROM sys_dict_item_alias 
            WHERE source_system = #{sourceSystem} 
              AND external_code = #{externalCode}
              AND deleted = 0
            """)
    Optional<RegSysDictItemAliasDO> selectBySourceAndCode(@Param("sourceSystem") String sourceSystem, 
                                                          @Param("externalCode") String externalCode);

    /**
     * Find all aliases for dictionary items of a specific type.
     * Used for type-specific integration analysis and documentation.
     * 
     * @param typeCode the dictionary type code, must not be null
     * @return List of aliases for items belonging to the specified type
     */
    @Select("""
            SELECT dia.* FROM sys_dict_item_alias dia
            JOIN sys_dict_item di ON di.id = dia.item_id
            JOIN sys_dict_type dt ON dt.id = di.type_id
            WHERE dt.type_code = #{typeCode}
              AND dia.deleted = 0 
              AND di.deleted = 0 
              AND dt.deleted = 0
            ORDER BY dia.source_system, dia.external_code
            """)
    List<RegSysDictItemAliasDO> selectByTypeCode(@Param("typeCode") String typeCode);

    /**
     * Count total aliases in the system.
     * Used for system health monitoring and statistics.
     * 
     * @return total count of non-deleted aliases
     */
    @Select("SELECT COUNT(*) FROM sys_dict_item_alias WHERE deleted = 0")
    int countTotal();

    /**
     * Find all distinct source systems.
     * Used for understanding external system integration landscape.
     * 
     * @return List of distinct source system identifiers
     */
    @Select("""
            SELECT DISTINCT source_system FROM sys_dict_item_alias 
            WHERE deleted = 0 
            ORDER BY source_system
            """)
    List<String> selectDistinctSourceSystems();

    /**
     * Count aliases by source system.
     * Used for integration analysis and system health monitoring.
     * 
     * @param sourceSystem the external system identifier, must not be null
     * @return count of aliases for the specified source system
     */
    @Select("""
            SELECT COUNT(*) FROM sys_dict_item_alias 
            WHERE source_system = #{sourceSystem} 
              AND deleted = 0
            """)
    int countBySourceSystem(@Param("sourceSystem") String sourceSystem);
}