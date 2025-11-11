package com.patra.starter.provenance.common.provider;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 批次执行的完整参数
 *
 * <p>包含提供者构建上游 API 请求所需的全部信息:
 *
 * <ul>
 *   <li>任务编译产生的基础参数(如 datetype, sort)
 *   <li>分页参数(如 retstart, retmax)
 *   <li>运行时状态(如 PubMed 的 WebEnv, query_key)
 * </ul>
 *
 * <p><b>使用场景:</b>
 *
 * <ul>
 *   <li>提供者从此对象获取所有必需的请求参数
 *   <li>与 {@link ProviderRequest} 一起传递,确保参数完整性
 *   <li>支持有状态的API调用(如PubMed的会话状态管理)
 * </ul>
 *
 * @param query 本次批次执行的编译查询字符串
 * @param params 完整参数载荷(基础 + 分页 + 运行时状态)
 * @author linqibin
 * @since 0.1.0
 */
public record BatchExecutionParams(String query, JsonNode params) {

  /**
   * 创建记录时验证不变式
   *
   * @param query 编译后的查询字符串(某些数据源可为 null)
   * @param params 完整参数载荷
   */
  public BatchExecutionParams {
    // query can be null for some data sources (e.g., date-range only queries)
  }
}
