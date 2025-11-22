package com.patra.starter.provenance.common.config;

/// 批处理配置记录
/// 
/// 定义批量数据获取操作的批处理参数,包括批次大小和标识符数量限制。 主要用于优化大量文献详情获取场景,如 PubMed 的 EFetch 批量调用。
/// 
/// @param detailFetchBatchSize 展开详情获取请求时使用的批次大小
/// @param maxIdsPerRequest 单个 API 调用中包含的标识符数量硬上限
/// @param epostThreshold 切换到 EPost 策略的阈值(可为空)
/// @author linqibin
/// @since 0.1.0
public record BatchingConfig(
    Integer detailFetchBatchSize, Integer maxIdsPerRequest, Integer epostThreshold) {}
