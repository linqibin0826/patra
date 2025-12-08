package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRetryCfgDO;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_prov_retry_cfg`。
///
/// SQL 语句定义在 `resources/mapper/RegProvRetryCfgMapper.xml`。
///
/// @author linqibin
/// @since 0.1.0
public interface RegProvRetryCfgMapper extends BaseMapper<RegProvRetryCfgDO> {

  /// 获取指定数据源和操作作用域的有效重试配置。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型
  /// @param now 查询时间戳
  /// @return 有效的重试配置(可选)
  Optional<RegProvRetryCfgDO> selectActiveMerged(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
