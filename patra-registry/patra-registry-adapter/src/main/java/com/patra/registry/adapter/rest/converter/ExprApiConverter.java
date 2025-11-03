package com.patra.registry.adapter.rest.converter;

import com.patra.registry.api.dto.expr.ApiParamMappingResp;
import com.patra.registry.api.dto.expr.ExprCapabilityResp;
import com.patra.registry.api.dto.expr.ExprFieldResp;
import com.patra.registry.api.dto.expr.ExprRenderRuleResp;
import com.patra.registry.api.dto.expr.ExprSnapshotResp;
import com.patra.registry.domain.model.read.expr.ApiParamMappingQuery;
import com.patra.registry.domain.model.read.expr.ExprCapabilityQuery;
import com.patra.registry.domain.model.read.expr.ExprFieldQuery;
import com.patra.registry.domain.model.read.expr.ExprRenderRuleQuery;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Expression 查询 DTO 到 API 响应 DTO 的转换器。
 *
 * <p>使用 MapStruct 自动生成转换代码,将读侧领域查询对象转换为外部 API 契约 DTO,供其他微服务的 Feign 客户端消费。
 *
 * <p>转换方法:
 *
 * <ul>
 *   <li>{@link #toResp(ExprFieldQuery)} - 转换单个字段查询对象
 *   <li>{@link #toResp(List)} - 转换字段查询对象列表
 *   <li>{@link #toResp(ApiParamMappingQuery)} - 转换 API 参数映射查询对象
 *   <li>{@link #toResp(ExprCapabilityQuery)} - 转换能力查询对象
 *   <li>{@link #toResp(ExprRenderRuleQuery)} - 转换渲染规则查询对象
 *   <li>{@link #toResp(ExprSnapshotQuery)} - 转换聚合快照查询对象
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ExprApiConverter {

  /**
   * 转换单个表达式字段查询对象为 API 响应 DTO。
   *
   * @param query 应用层产生的字段查询对象
   * @return RPC 契约暴露的响应 DTO
   */
  ExprFieldResp toResp(ExprFieldQuery query);

  /**
   * 转换表达式字段查询对象列表为 API 响应 DTO 列表。
   *
   * @param queries 字段查询对象集合
   * @return 响应 DTO 列表,保持迭代顺序
   */
  List<ExprFieldResp> toResp(List<ExprFieldQuery> queries);

  /**
   * 转换 API 参数映射查询对象为响应 DTO。
   *
   * @param query 应用层产生的映射查询对象
   * @return 反映映射配置的 API 响应 DTO
   */
  ApiParamMappingResp toResp(ApiParamMappingQuery query);

  /**
   * 转换表达式能力查询对象为 API 响应 DTO。
   *
   * @param query 能力查询对象
   * @return 下游客户端消费的响应 DTO
   */
  ExprCapabilityResp toResp(ExprCapabilityQuery query);

  /**
   * 转换表达式渲染规则查询对象为 API 响应 DTO。
   *
   * @param query 渲染规则查询对象
   * @return 渲染规则响应 DTO
   */
  ExprRenderRuleResp toResp(ExprRenderRuleQuery query);

  /**
   * 转换聚合表达式快照查询对象为 API 响应 DTO。
   *
   * @param query 聚合快照查询对象
   * @return 分发给调用者的快照响应 DTO
   */
  ExprSnapshotResp toResp(ExprSnapshotQuery query);
}
