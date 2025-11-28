package com.patra.registry.infra.persistence.mapper.provenance;

import com.patra.registry.infra.persistence.entity.provenance.RegProvWindowOffsetCfgDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_prov_window_offset_cfg`。
///
/// SQL 实现位于 `resources/mapper/RegProvWindowOffsetCfgMapper.xml`。
///
/// @author linqibin
/// @since 0.1.0
public interface RegProvWindowOffsetCfgMapper extends PatraBaseMapper<RegProvWindowOffsetCfgDO> {

  /// 查询激活的时间窗口偏移配置,优先选择特定操作键,回退到 `ALL`。
  ///
  /// SQL 中的排序保证即使在重叠切片期间也能返回确定性结果。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型(支持 ALL 回退)
  /// @param now 查询时间戳
  /// @return 在 `now` 时刻有效的窗口偏移配置(可选)
  Optional<RegProvWindowOffsetCfgDO> selectActiveMerged(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
