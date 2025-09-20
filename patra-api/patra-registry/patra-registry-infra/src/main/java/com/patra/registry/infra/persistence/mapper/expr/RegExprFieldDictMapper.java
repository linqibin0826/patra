package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegExprFieldDictDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * {@code reg_expr_field_dict} 表的只读 Mapper。
 */
@Mapper
public interface RegExprFieldDictMapper extends BaseMapper<RegExprFieldDictDO> {

    /** 查询全部未删除的字段字典（按字段键排序）。 */
    @Select("SELECT * FROM reg_expr_field_dict WHERE deleted = 0 ORDER BY field_key")
    List<RegExprFieldDictDO> selectAllActive();
}
