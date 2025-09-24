package com.patra.registry.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemAliasDO;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 字典项别名读取 Mapper（MyBatis-Plus）。
 *
 * <p>服务于 CQRS 查询侧，仅包含只读操作，面向表 sys_dict_item_alias；
 * 支持通过别名桥接外部系统与内部字典项。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */

public interface RegSysDictItemAliasMapper extends BaseMapper<RegSysDictItemAliasDO> {

    /** 按外部系统别名查找对应的内部字典项（需启用且未删除）。 */
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

    /** 查询某个字典项的全部别名。 */
    @Select("""
            SELECT * FROM sys_dict_item_alias 
            WHERE item_id = #{itemId} 
              AND deleted = 0
            ORDER BY source_system, external_code
            """)
    List<RegSysDictItemAliasDO> selectByItemId(@Param("itemId") Long itemId);

    /** 按来源系统查询别名集合。 */
    @Select("""
            SELECT * FROM sys_dict_item_alias 
            WHERE source_system = #{sourceSystem} 
              AND deleted = 0
            ORDER BY external_code
            """)
    List<RegSysDictItemAliasDO> selectBySourceSystem(@Param("sourceSystem") String sourceSystem);

    /** 按来源系统+外部编码精确查询别名（用于唯一性校验/直查）。 */
    @Select("""
            SELECT * FROM sys_dict_item_alias 
            WHERE source_system = #{sourceSystem} 
              AND external_code = #{externalCode}
              AND deleted = 0
            """)
    Optional<RegSysDictItemAliasDO> selectBySourceAndCode(@Param("sourceSystem") String sourceSystem, 
                                                          @Param("externalCode") String externalCode);

    /** 查询某类型下所有字典项的别名集合。 */
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

    /** 统计系统内别名总数（未删除）。 */
    @Select("SELECT COUNT(*) FROM sys_dict_item_alias WHERE deleted = 0")
    int countTotal();

    /** 查询所有不同的来源系统标识（了解外部集成面貌）。 */
    @Select("""
            SELECT DISTINCT source_system FROM sys_dict_item_alias 
            WHERE deleted = 0 
            ORDER BY source_system
            """)
    List<String> selectDistinctSourceSystems();

    /** 按来源系统统计别名数量（分析/健康）。 */
    @Select("""
            SELECT COUNT(*) FROM sys_dict_item_alias 
            WHERE source_system = #{sourceSystem} 
              AND deleted = 0
            """)
    int countBySourceSystem(@Param("sourceSystem") String sourceSystem);
}
