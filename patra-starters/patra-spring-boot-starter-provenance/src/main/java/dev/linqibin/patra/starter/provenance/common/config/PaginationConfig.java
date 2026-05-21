package dev.linqibin.patra.starter.provenance.common.config;

/// 分页配置记录
///
/// 定义访问 Provenance 数据源时的分页行为,包括默认页大小和单次执行的最大页数限制。 这些参数会被建议给上游 API,用于控制数据批量获取的粒度。
///
/// @param pageSizeValue 建议给上游 API 的默认页大小
/// @param maxPagesPerExecution 单次执行中处理的最大页数
/// @author linqibin
/// @since 0.1.0
public record PaginationConfig(Integer pageSizeValue, Integer maxPagesPerExecution) {}
