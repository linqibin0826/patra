package com.patra.registry.infra.persistence.mapper.expr;

import com.patra.registry.infra.persistence.entity.expr.RegProvExprCapabilityDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_prov_expr_capability`。
///
/// 提供便捷查询以定位字段的有效能力切片。
///
/// @author linqibin
/// @since 0.1.0
public interface RegProvExprCapabilityMapper extends PatraBaseMapper<RegProvExprCapabilityDO> {

  /// 获取请求字段的最具体激活能力切片。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型(支持 ALL 回退)
  /// @param fieldKey 规范字段键
  /// @param now 评估时间戳
  /// @return 在 `now` 时刻有效的能力(可选)
  Optional<RegProvExprCapabilityDO> selectActive(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("fieldKey") String fieldKey,
      @Param("now") Instant now);

  /// 列出提供的操作作用域的所有激活能力切片,合并具有相同 `field_key` 的源级行。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型(支持 ALL 回退)
  /// @param now 评估时间戳
  /// @return 能力列表,每个字段一条
  List<RegProvExprCapabilityDO> selectActiveByTask(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
