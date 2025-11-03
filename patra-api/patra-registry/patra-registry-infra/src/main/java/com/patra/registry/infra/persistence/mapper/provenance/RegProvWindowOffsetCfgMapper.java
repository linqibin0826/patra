package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvWindowOffsetCfgDO;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/**
 * 只读 Mapper,用于表 {@code reg_prov_window_offset_cfg}. SQL implementation located in {@code
 * resources/mapper/RegProvWindowOffsetCfgMapper.xml}.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface RegProvWindowOffsetCfgMapper extends BaseMapper<RegProvWindowOffsetCfgDO> {

  /**
   * Selects the active window-offset configuration, preferring the specific operation key and
   * falling back to {@code ALL}. Ordering is handled within the SQL to guarantee deterministic
   * results even during overlapping slices.
   */
  Optional<RegProvWindowOffsetCfgDO> selectActiveMerged(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
