package com.patra.registry.infra.persistence.mapper.provenance;

import com.patra.registry.infra.persistence.entity.provenance.RegProvPaginationCfgDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_prov_pagination_cfg`。
///
/// SQL 定义位于 `resources/mapper/RegProvPaginationCfgMapper.xml`。
///
/// @author linqibin
/// @since 0.1.0
public interface RegProvPaginationCfgMapper extends PatraBaseMapper<RegProvPaginationCfgDO> {

  /// 返回指定作用域的有效分页配置,回退到 `ALL`。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型
  /// @param now 查询时间戳
  /// @return 有效的分页配置(可选)
  Optional<RegProvPaginationCfgDO> selectActiveMerged(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
