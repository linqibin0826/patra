package com.patra.ingest.domain.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanMetadata;

/**
 * PubMed 搜索元数据端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>: 为已编译的 PubMed 搜索词提供计划元数据,包括:
 *
 * <ul>
 *   <li>总结果计数
 *   <li>可在执行期间重用的缓存句柄
 * </ul>
 *
 * <p><b>实现要求</b>: 实现必须避免执行任何业务逻辑,仅委托给上游客户端/网关。
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>输出端口(Output Port)</b>,定义在 Domain 层,由基础设施层(Infrastructure)实现,抽象
 * PubMed API 访问细节。
 */
public interface PubmedSearchPort {

  /**
   * 为给定的 PubMed 查询和参数准备计划阶段所需的元数据。
   *
   * <p><b>业务含义</b>: 调用 PubMed API 获取搜索元数据,为计划切片提供依据。
   *
   * @param query 已编译的布尔查询字符串
   * @param params 已编译的参数 JSON(例如 datetype/mindate/maxdate/reldate/sort)
   * @param provenanceConfigSnapshot 当前执行的配置快照
   * @return 计划元数据,包括结果计数和可选的 WebEnv 缓存句柄
   */
  PlanMetadata preparePlanMetadata(
      String query, JsonNode params, ProvenanceConfigSnapshot provenanceConfigSnapshot);
}
