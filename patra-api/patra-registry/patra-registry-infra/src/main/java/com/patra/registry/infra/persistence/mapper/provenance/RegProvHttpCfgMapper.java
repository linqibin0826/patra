package com.patra.registry.infra.persistence.mapper.provenance;

import com.patra.registry.infra.persistence.entity.provenance.RegProvHttpCfgDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_prov_http_cfg`。
///
/// SQL 查询由 XML 文件定义,位于 `resources/mapper/RegProvHttpCfgMapper.xml`。
///
/// @author linqibin
/// @since 0.1.0
public interface RegProvHttpCfgMapper extends PatraBaseMapper<RegProvHttpCfgDO> {

  /// 查询指定数据源和操作作用域的有效 HTTP 配置。
  ///
  /// 优先选择操作特定配置,未找到时回退到 `ALL`。XML 中的排序保证即使在重叠切片期间也能返回确定性结果。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 操作类型键
  /// @param now 查询时间戳
  /// @return 有效的 HTTP 配置(可选)
  Optional<RegProvHttpCfgDO> selectActiveMerged(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
