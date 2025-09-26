package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvWindowOffsetCfgDO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code reg_prov_window_offset_cfg} 表的只读 Mapper。
 */

public interface RegProvWindowOffsetCfgMapper extends BaseMapper<RegProvWindowOffsetCfgDO> {

    /**
     * 按 TASK → SOURCE、精确 task → ALL、effective_from DESC、id DESC 的顺序挑选唯一记录。
     * 统一在 SQL 内完成候选过滤与优先级排序，保证灰度/重叠期间仍返回确定结果。
     */
    Optional<RegProvWindowOffsetCfgDO> selectActiveMerged(@Param("provenanceId") Long provenanceId,
                                                          @Param("taskTypeKey") String taskTypeKey,
                                                          @Param("now") Instant now);
}
