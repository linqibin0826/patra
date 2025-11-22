package com.patra.registry.app.converter;

import com.patra.registry.domain.model.read.expr.ApiParamMappingQuery;
import com.patra.registry.domain.model.read.expr.ExprCapabilityQuery;
import com.patra.registry.domain.model.read.expr.ExprFieldQuery;
import com.patra.registry.domain.model.read.expr.ExprRenderRuleQuery;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import com.patra.registry.domain.model.vo.expr.ApiParamMapping;
import com.patra.registry.domain.model.vo.expr.ExprCapability;
import com.patra.registry.domain.model.vo.expr.ExprField;
import com.patra.registry.domain.model.vo.expr.ExprRenderRule;
import com.patra.registry.domain.model.vo.expr.ExprSnapshot;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/// 表达式领域对象到查询 DTO 转换器。
///
/// 职责：
///
/// - 将表达式领域对象(字段、能力、渲染规则、参数映射)转换为只读查询 DTO
///   - 支持外部客户端的数据契约消费
///   - 隔离表达式元数据的内部实现和外部表示
///
/// 设计模式：MapStruct 自动生成转换代码,避免手写样板映射。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ExprQueryAssembler {
  /// 转换单个表达式字段值对象为查询 DTO。
  ///
  /// @param field 领域层的表达式字段
  /// @return 对应的查询 DTO
  ExprFieldQuery toQuery(ExprField field);

  /// 转换表达式字段集合为查询 DTO 列表。
  ///
  /// @param fields 领域层的表达式字段集合
  /// @return 查询 DTO 列表,保持迭代顺序
  List<ExprFieldQuery> toFieldQueries(List<ExprField> fields);

  /// 转换 API 参数映射为查询 DTO。
  ///
  /// @param mapping 领域层的 API 参数映射
  /// @return 查询 DTO,镜像映射属性
  ApiParamMappingQuery toQuery(ApiParamMapping mapping);

  /// 转换 API 参数映射集合为查询 DTO 列表。
  ///
  /// @param mappings 领域层的 API 参数映射集合
  /// @return 查询 DTO 列表,保持提供的顺序
  List<ApiParamMappingQuery> toMappingQueries(List<ApiParamMapping> mappings);

  /// 转换表达式能力为查询 DTO。
  ///
  /// @param capability 领域层的能力值对象
  /// @return 查询 DTO,描述能力信息
  ExprCapabilityQuery toQuery(ExprCapability capability);

  /// 转换表达式能力集合为查询 DTO 列表。
  ///
  /// @param capabilities 领域层的能力集合
  /// @return 查询 DTO 列表,镜像提供的能力
  List<ExprCapabilityQuery> toCapabilityQueries(List<ExprCapability> capabilities);

  /// 转换渲染规则为查询 DTO。
  ///
  /// @param rule 领域层的渲染规则值对象
  /// @return 查询 DTO,描述渲染规则
  ExprRenderRuleQuery toQuery(ExprRenderRule rule);

  /// 转换渲染规则集合为查询 DTO 列表。
  ///
  /// @param rules 领域层的渲染规则集合
  /// @return 查询 DTO 列表,匹配渲染规则
  List<ExprRenderRuleQuery> toRenderRuleQueries(List<ExprRenderRule> rules);

  /// 转换表达式快照聚合根为查询 DTO。
  ///
  /// @param snapshot 领域层的表达式快照聚合根
  /// @return 查询 DTO,包含字段、能力、渲染规则和参数映射
  default ExprSnapshotQuery toQuery(ExprSnapshot snapshot) {
    return new ExprSnapshotQuery(
        toFieldQueries(snapshot.fields()),
        toCapabilityQueries(snapshot.capabilities()),
        toRenderRuleQueries(snapshot.renderRules()),
        toMappingQueries(snapshot.apiParamMappings()));
  }
}
