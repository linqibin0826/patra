package com.patra.registry.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.vo.expr.ExprSnapshot;
import java.time.Instant;

/// 表达式仓储接口,提供表达式相关领域对象的只读访问。
/// 
/// **职责**:
/// 
/// - 查询表达式字段定义、能力声明、渲染规则和API参数映射
///   - 加载聚合的表达式快照,用于表达式渲染引擎
///   - 支持时态查询,获取特定时刻有效的表达式配置
/// 
/// **业务场景**:表达式快照用于验证用户输入表达式、选择适当的渲染规则、 将标准字段键转换为数据源特定的参数名称。
/// 
/// **注意**:本仓储为只读仓储,不提供写操作。所有方法使用领域语言 表达业务意图,隐藏底层持久化技术细节。
/// 
/// @author Patra Team
/// @since 2.0
public interface ExprRepository {

  /// 加载指定数据源和作用域的聚合表达式快照。
/// 
/// 快照包含字段定义、能力声明、渲染规则和参数映射,这些配置在指定时刻有效。
/// 
/// 业务规则:
/// 
/// - 快照中的所有集合(fields、capabilities、renderRules、apiParamMappings)永不为null,可能为空
///   - 支持作用域优先级:端点级 > 操作级 > 数据源级
///   - 时态查询确保返回的配置在指定时刻有效
/// 
/// @param provenanceCode 数据源代码,标识数据源(如`pubmed`、`crossref`),不可为null
/// @param operationType 操作类型(`HARVEST/UPDATE/BACKFILL`),null表示跨操作查询
/// @param endpointName 端点名称,用于获取端点特定配置,null表示端点无关查询
/// @param at 查询时刻,用于时态过滤,不可为null
/// @return 聚合表达式快照,包含所有表达式相关配置,永不为null
  ExprSnapshot loadSnapshot(
      ProvenanceCode provenanceCode, String operationType, String endpointName, Instant at);
}
