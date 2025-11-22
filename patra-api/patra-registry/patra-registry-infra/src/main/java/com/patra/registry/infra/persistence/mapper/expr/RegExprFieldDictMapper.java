package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegExprFieldDictDO;
import java.util.List;

/// 只读 Mapper,用于表 `reg_expr_field_dict`。
/// 
/// 提供用于加载规范表达式字段元数据的辅助查询。
/// 
/// SQL 实现位于 `resources/mapper/RegExprFieldDictMapper.xml`。
/// 
/// @author linqibin
/// @since 0.1.0
public interface RegExprFieldDictMapper extends BaseMapper<RegExprFieldDictDO> {

  /// 列出按 `field_key` 排序的所有未删除表达式字段定义。
/// 
/// @return 字段定义列表
  List<RegExprFieldDictDO> selectAllActive();
}
