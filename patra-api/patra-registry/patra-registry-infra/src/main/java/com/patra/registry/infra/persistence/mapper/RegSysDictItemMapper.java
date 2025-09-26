package com.patra.registry.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemDO;
import org.apache.ibatis.annotations.Param;

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

public interface RegSysDictItemMapper extends BaseMapper<RegSysDictItemDO> {

    /** 按类型编码与项编码查询启用且未删除的字典项。 */
    Optional<RegSysDictItemDO> selectByTypeAndItemCode(@Param("typeCode") String typeCode, 
                                                       @Param("itemCode") String itemCode);

    /** 查询某类型下所有启用项（排序：display_order、item_code）。 */
    List<RegSysDictItemDO> selectEnabledByTypeCode(@Param("typeCode") String typeCode);

    /** 查询某类型默认项（is_default=1 且启用且未删除）。 */
    Optional<RegSysDictItemDO> selectDefaultByTypeCode(@Param("typeCode") String typeCode);

    /** 根据类型 ID 查询启用项（内部使用）。 */
    List<RegSysDictItemDO> selectEnabledByTypeId(@Param("typeId") Long typeId);

    /** 统计某类型启用项数量（健康/统计）。 */
    int countEnabledByTypeCode(@Param("typeCode") String typeCode);

    /** 查询存在多个默认项的类型（数据完整性检查）。 */
    List<String> selectTypesWithMultipleDefaults();

    /** 查询没有默认项的类型（配置缺失检查）。 */
    List<String> selectTypesWithoutDefaults();

    /** 统计全局启用项总数（健康/统计）。 */
    int countTotalEnabled();

    /** 统计全局项总数（含禁用，排除删除）。 */
    int countTotal();
}
