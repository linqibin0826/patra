package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprRenderRuleDO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_prov_expr_render_rule`。
///
/// 提供辅助查询以定位表达式原子的激活渲染规则。
///
/// @author linqibin
/// @since 0.1.0
public interface RegProvExprRenderRuleMapper extends BaseMapper<RegProvExprRenderRuleDO> {

  /// 查询匹配指定维度的最具体激活渲染规则。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型(支持 ALL 回退)
  /// @param fieldKey 规范字段键
  /// @param opCode 表达式操作符代码
  /// @param matchTypeKey 规范化的匹配类型键
  /// @param negatedKey 规范化的否定键
  /// @param valueTypeKey 规范化的值类型键
  /// @param emitTypeCode 发射类型(QUERY/PARAMS)
  /// @param now 评估时间戳
  /// @return 在 `now` 时刻有效的渲染规则(可选)
  Optional<RegProvExprRenderRuleDO> selectActive(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("fieldKey") String fieldKey,
      @Param("opCode") String opCode,
      @Param("matchTypeKey") String matchTypeKey,
      @Param("negatedKey") String negatedKey,
      @Param("valueTypeKey") String valueTypeKey,
      @Param("emitTypeCode") String emitTypeCode,
      @Param("now") Instant now);

  /// 列出提供的数据源和操作作用域的激活渲染规则,每个规则签名合并源级回退。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型
  /// @param now 评估时间戳
  /// @return 渲染规则列表,每个唯一规则签名一条
  List<RegProvExprRenderRuleDO> selectActiveByTask(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
