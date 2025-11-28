package com.patra.registry.infra.persistence.mapper.provenance;

import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_provenance`。
///
/// 提供辅助查询以通过业务代码加载数据源元数据。
///
/// @author linqibin
/// @since 0.1.0
public interface RegProvenanceMapper extends PatraBaseMapper<RegProvenanceDO> {

  /// 通过其稳定的业务代码获取激活的数据源行。
  ///
  /// @param code 数据源代码(例如,pubmed)
  /// @return 可选的数据源定义
  Optional<RegProvenanceDO> selectByCode(@Param("code") String code);

  /// 列出按代码排序的所有激活数据源定义。
  ///
  /// @return 数据源列表
  List<RegProvenanceDO> selectAllActive();
}
