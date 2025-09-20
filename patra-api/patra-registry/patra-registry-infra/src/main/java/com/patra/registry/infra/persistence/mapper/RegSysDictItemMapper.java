package com.patra.registry.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 字典项读取 Mapper（MyBatis-Plus）。
 *
 * <p>服务于 CQRS 查询侧，仅包含只读操作，面向表 sys_dict_item；
 * 包含针对启用项的优化查询。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface RegSysDictItemMapper extends BaseMapper<RegSysDictItemDO> {

    /** 按类型编码与项编码查询启用且未删除的字典项。 */
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

    /** 查询某类型下所有启用项（排序：display_order、item_code）。 */
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

    /** 查询某类型默认项（is_default=1 且启用且未删除）。 */
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

    /** 根据类型 ID 查询启用项（内部使用）。 */
    @Select("""
            SELECT * FROM sys_dict_item 
            WHERE type_id = #{typeId}
              AND enabled = 1 
              AND deleted = 0
            ORDER BY display_order ASC, item_code ASC
            """)
    List<RegSysDictItemDO> selectEnabledByTypeId(@Param("typeId") Long typeId);

    /** 统计某类型启用项数量（健康/统计）。 */
    @Select("""
            SELECT COUNT(*) FROM sys_dict_item di
            JOIN sys_dict_type dt ON dt.id = di.type_id
            WHERE dt.type_code = #{typeCode}
              AND di.enabled = 1 
              AND di.deleted = 0 
              AND dt.deleted = 0
            """)
    int countEnabledByTypeCode(@Param("typeCode") String typeCode);

    /** 查询存在多个默认项的类型（数据完整性检查）。 */
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

    /** 查询没有默认项的类型（配置缺失检查）。 */
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

    /** 统计全局启用项总数（健康/统计）。 */
    @Select("SELECT COUNT(*) FROM sys_dict_item WHERE enabled = 1 AND deleted = 0")
    int countTotalEnabled();

    /** 统计全局项总数（含禁用，排除删除）。 */
    @Select("SELECT COUNT(*) FROM sys_dict_item WHERE deleted = 0")
    int countTotal();
}
