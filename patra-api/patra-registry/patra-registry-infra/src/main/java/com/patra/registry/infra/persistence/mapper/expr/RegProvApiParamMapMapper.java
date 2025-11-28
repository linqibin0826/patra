package com.patra.registry.infra.persistence.mapper.expr;

import com.patra.registry.infra.persistence.entity.expr.RegProvApiParamMapDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_prov_api_param_map`。
///
/// 为标准化键解析数据源特定的参数映射。
///
/// @author linqibin
/// @since 0.1.0
public interface RegProvApiParamMapMapper extends PatraBaseMapper<RegProvApiParamMapDO> {

  /// 获取指定数据源、端点和标准键的最具体激活映射。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型(支持 ALL 回退)
  /// @param endpointName 端点名称(NULL 表示所有端点)
  /// @param stdKey 标准化参数键
  /// @param now 评估时间戳
  /// @return 在 `now` 时刻有效的映射(可选)
  Optional<RegProvApiParamMapDO> selectActive(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("endpointName") String endpointName,
      @Param("stdKey") String stdKey,
      @Param("now") Instant now);

  /// 列出指定作用域的所有激活映射,每个 `(endpoint_name, std_key)` 签名最多返回一行。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型
  /// @param endpointName 端点名称(NULL 表示所有端点)
  /// @param now 评估时间戳
  /// @return 激活映射列表
  List<RegProvApiParamMapDO> selectActiveByTask(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("endpointName") String endpointName,
      @Param("now") Instant now);
}
