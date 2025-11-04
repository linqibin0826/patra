package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * 批处理配置值对象,定义批量详情请求的参数整形策略。
 *
 * <p><strong>不可变性</strong>:此对象一旦创建不可修改,通过值语义比较相等性。
 *
 * <p><strong>业务约束</strong>:
 *
 * <ul>
 *   <li>配置ID和数据源ID必须为正整数
 *   <li>生效时间(effectiveFrom)不可为空,失效时间(effectiveTo)为null表示永久有效
 *   <li>操作类型(operationType)为null时表示适用于所有操作(ALL/HARVEST/UPDATE/BACKFILL)
 *   <li>所有配置参数为可选项,null时使用应用默认值或端点特定配置
 * </ul>
 *
 * <p><strong>业务语义</strong>:
 *
 * <ul>
 *   <li>批量详情获取:将多个ID打包成一次请求,减少HTTP往返次数
 *   <li>批量大小控制:通过detailFetchBatchSize控制每批次获取的条数
 *   <li>ID参数定制:通过idsParamName和idsJoinDelimiter适配不同API的参数格式
 *   <li>硬上限保护:通过maxIdsPerRequest防止单次请求ID过多导致超时或拒绝
 *   <li>背压控制:通过并发控制和压缩策略优化吞吐量
 * </ul>
 *
 * @param id 配置主键,唯一标识此批处理配置,必须为正整数
 * @param provenanceId 数据源ID外键,引用{@code reg_provenance.id},必须为正整数
 * @param operationType 操作类型,取值为{@code ALL/HARVEST/UPDATE/BACKFILL},null表示适用于所有操作
 * @param effectiveFrom 配置生效时间(包含),标记此配置开始生效的时刻,不可为null
 * @param effectiveTo 配置失效时间(不包含),null表示永久有效
 * @param detailFetchBatchSize 详情获取批量大小(行数),null时使用应用默认值
 * @param idsParamName ID列表参数名称,用于批量详情请求,null时由端点或应用决定
 * @param idsJoinDelimiter ID列表连接分隔符,如逗号或加号,用于拼接ID数组
 * @param maxIdsPerRequest 单次HTTP请求最大ID数量,作为硬上限防止请求过大
 * @author Patra Team
 * @since 2.0
 */
public record BatchingConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer detailFetchBatchSize,
    String idsParamName,
    String idsJoinDelimiter,
    Integer maxIdsPerRequest) {
  /**
   * 规范构造器,强制执行批处理配置的业务约束。
   *
   * <p>验证规则:
   *
   * <ul>
   *   <li>配置ID和数据源ID必须为正整数
   *   <li>生效时间不可为空
   *   <li>所有字符串字段自动trim去除首尾空白
   * </ul>
   *
   * @throws DomainValidationException 如果验证失败
   */
  public BatchingConfig(
      Long id,
      Long provenanceId,
      String operationType,
      Instant effectiveFrom,
      Instant effectiveTo,
      Integer detailFetchBatchSize,
      String idsParamName,
      String idsJoinDelimiter,
      Integer maxIdsPerRequest) {
    DomainValidationException.positive(id, "Batching config id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = operationType != null ? operationType.trim() : null;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.detailFetchBatchSize = detailFetchBatchSize;
    this.idsParamName = idsParamName != null ? idsParamName.trim() : null;
    this.idsJoinDelimiter = idsJoinDelimiter != null ? idsJoinDelimiter.trim() : null;
    this.maxIdsPerRequest = maxIdsPerRequest;
  }
}
