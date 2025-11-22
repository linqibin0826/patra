package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvBatchingCfgDO;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_prov_batching_cfg`。
///
/// SQL 语句由 `resources/mapper/RegProvBatchingCfgMapper.xml` 支持。
///
/// @author linqibin
/// @since 0.1.0
public interface RegProvBatchingCfgMapper extends BaseMapper<RegProvBatchingCfgDO> {

  /// 返回指定数据源和操作作用域最具体的批处理配置。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型
  /// @param now 查询时间戳
  /// @return 最具体的批处理配置(可选)
  Optional<RegProvBatchingCfgDO> selectActiveMerged(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
