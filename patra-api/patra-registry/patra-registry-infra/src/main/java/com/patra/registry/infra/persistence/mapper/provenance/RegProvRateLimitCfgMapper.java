package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRateLimitCfgDO;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_prov_rate_limit_cfg`。
///
/// SQL 实现位于 `resources/mapper/RegProvRateLimitCfgMapper.xml`。
///
/// @author linqibin
/// @since 0.1.0
public interface RegProvRateLimitCfgMapper extends BaseMapper<RegProvRateLimitCfgDO> {

  /// 查询按数据源和操作作用域限定的有效速率限制配置。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型
  /// @param now 查询时间戳
  /// @return 有效的速率限制配置(可选)
  Optional<RegProvRateLimitCfgDO> selectActiveMerged(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
