package com.patra.starter.expr.compiler.model;

/**
 * 常用操作类型常量，帮助调用方避免硬编码字符串。
 *
 * <p>操作类型用于区分同一数据源在不同业务场景下生效的配置切片。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 使用常量而不是硬编码字符串
 * CompileRequest request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
 *     .forOperationType(OperationTypes.UPDATE)
 *     .build();
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class OperationTypes {
    
    /**
     * 初始全量采集：通常在首采或窗口重建时触发。
     */
    public static final String HARVEST = "HARVEST";
    
    /**
     * 历史数据回补或修复。
     */
    public static final String BACKFILL = "BACKFILL";
    
    /**
     * 增量更新操作：用于定期同步新增或变更的数据。
     *
     * <p>这是最常用的操作类型，通常配置较为激进的查询参数以获取最新数据。</p>
     */
    public static final String UPDATE = "UPDATE";
    
    /**
     * 全量同步操作：用于完整的数据同步。
     *
     * <p>通常用于初始化或定期的完整数据刷新，可能需要特殊的配置以处理大量数据。</p>
     */
    public static final String FULL = "FULL";
    
    /**
     * 搜索操作：用于交互式搜索场景。
     *
     * <p>通常对响应时间要求较高，可能配置不同的超时和重试策略。</p>
     */
    public static final String SEARCH = "SEARCH";
    
    /**
     * 指标或统计操作：通常为读取型任务。
     */
    public static final String METRICS = "METRICS";
    
    /**
     * 监控操作：用于系统监控和健康检查。
     *
     * <p>通常使用简化的查询和快速失败策略。</p>
     */
    public static final String MONITOR = "MONITOR";
    
    /**
     * 验证操作：用于数据验证和质量检查。
     *
     * <p>通常需要特殊的查询配置来检查数据完整性。</p>
     */
    public static final String VALIDATE = "VALIDATE";
    
    /**
     * 分析操作：用于数据分析和统计。
     *
     * <p>可能需要不同的查询配置以支持复杂的分析需求。</p>
     */
    public static final String ANALYZE = "ANALYZE";
    
    /**
     * 导出操作：用于数据导出。
     *
     * <p>通常配置较大的批次大小和较长的超时时间。</p>
     */
    public static final String EXPORT = "EXPORT";
    
    // 防止实例化
    private OperationTypes() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}