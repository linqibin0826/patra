package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegExprFieldDictDO;

import java.util.List;

/**
 * Read-only mapper for {@code reg_expr_field_dict}.
 * <p>Supplies helper queries for loading canonical expression field metadata.</p>
 *
 * <p>The SQL implementation is located in {@code resources/mapper/RegExprFieldDictMapper.xml}.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface RegExprFieldDictMapper extends BaseMapper<RegExprFieldDictDO> {

    /**
     * Lists all non-deleted expression field definitions ordered by {@code field_key}.
     *
     * @return field definitions
     */
    List<RegExprFieldDictDO> selectAllActive();
}
