package com.patra.registry.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictTypeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 字典类型读取 Mapper（MyBatis-Plus）。
 *
 * <p>服务于 CQRS 查询侧，仅包含只读操作，面向表 sys_dict_type。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface RegSysDictTypeMapper extends BaseMapper<RegSysDictTypeDO> {

    /** 按类型编码查询类型（使用稳定业务键 type_code）。 */
    @Select("SELECT * FROM sys_dict_type WHERE type_code = #{typeCode} AND deleted = 0")
    Optional<RegSysDictTypeDO> selectByTypeCode(@Param("typeCode") String typeCode);

    /** 查询全部未删除的字典类型（按 type_code 排序）。 */
    @Select("SELECT * FROM sys_dict_type WHERE deleted = 0 ORDER BY type_code")
    List<RegSysDictTypeDO> selectAllEnabled();

    /** 统计未删除的字典类型总数（健康/统计）。 */
    @Select("SELECT COUNT(*) FROM sys_dict_type WHERE deleted = 0")
    int countTotal();

    /** 查询允许自定义项的类型（allow_custom_items=1）。 */
    @Select("SELECT * FROM sys_dict_type WHERE allow_custom_items = 1 AND deleted = 0 ORDER BY type_code")
    List<RegSysDictTypeDO> selectCustomizableTypes();

    /** 查询系统管理的类型（is_system=1）。 */
    @Select("SELECT * FROM sys_dict_type WHERE is_system = 1 AND deleted = 0 ORDER BY type_code")
    List<RegSysDictTypeDO> selectSystemTypes();
}
