package com.patra.ingest.domain.model.vo.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.model.DataType;
import com.patra.common.model.plan.PlanMetadata;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;

/**
 * 任务运行的执行上下文值对象。
 *
 * <p>捕获配置快照和已编译表达式,确保任务执行的可重放性和一致性。
 *
 * @param taskId 任务标识符
 * @param runId 运行标识符
 * @param planId 所属计划标识符
 * @param sliceId 所属切片标识符
 * @param scheduleInstanceId 调度实例标识符(来自 TaskAggregate)
 * @param provenanceCode 来源代码
 * @param operationCode 操作代码
 * @param dataType 数据类型(LITERATURE, JOURNAL, DRUG等)
 * @param configSnapshot 配置快照
 * @param exprHash 表达式哈希
 * @param compiledQuery 已编译的查询
 * @param compiledParams 已编译的查询参数
 * @param normalizedExpression 归一化表达式字符串
 * @param windowSpec 窗口规范
 * @param planMetadata 计划元数据(可选,由批次规划器填充)
 * @author linqibin
 * @since 0.1.0
 */
public record ExecutionContext(
    Long taskId,
    Long runId,
    Long planId,
    Long sliceId,
    Long scheduleInstanceId,
    String provenanceCode,
    String operationCode,
    DataType dataType,
    ProvenanceConfigSnapshot configSnapshot,
    String exprHash,
    String compiledQuery,
    JsonNode compiledParams,
    String normalizedExpression,
    WindowSpec windowSpec,
    PlanMetadata planMetadata) {

  /**
   * 创建带有计划元数据的新上下文
   *
   * @param planMetadata 计划元数据
   * @return 新的执行上下文实例
   */
  public ExecutionContext withPlanMetadata(PlanMetadata planMetadata) {
    return new ExecutionContext(
        this.taskId,
        this.runId,
        this.planId,
        this.sliceId,
        this.scheduleInstanceId,
        this.provenanceCode,
        this.operationCode,
        this.dataType,
        this.configSnapshot,
        this.exprHash,
        this.compiledQuery,
        this.compiledParams,
        this.normalizedExpression,
        this.windowSpec,
        planMetadata
    );
  }
}
