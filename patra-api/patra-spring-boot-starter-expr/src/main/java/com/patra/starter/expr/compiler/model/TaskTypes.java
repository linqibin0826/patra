package com.patra.starter.expr.compiler.model;

/**
 * 表达式编译器常用的任务类型常量。
 * 
 * <p>任务类型用于区分同一数据源的不同业务场景，系统会根据任务类型选择对应的配置。</p>
 * 
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 使用常量而不是硬编码字符串
 * CompileRequest request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
 *     .forTask(TaskTypes.UPDATE)
 *     .build();
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class TaskTypes {
    
    /**
     * 增量更新任务：用于定期同步新增或变更的数据。
     * 
     * <p>这是最常用的任务类型，通常配置较为激进的查询参数以获取最新数据。</p>
     */
    public static final String UPDATE = "update";
    
    /**
     * 全量同步任务：用于完整的数据同步。
     * 
     * <p>通常用于初始化或定期的完整数据刷新，可能需要特殊的配置以处理大量数据。</p>
     */
    public static final String FULL = "full";
    
    /**
     * 搜索任务：用于交互式搜索场景。
     * 
     * <p>通常对响应时间要求较高，可能配置不同的超时和重试策略。</p>
     */
    public static final String SEARCH = "search";
    
    /**
     * 监控任务：用于系统监控和健康检查。
     * 
     * <p>通常使用简化的查询和快速失败策略。</p>
     */
    public static final String MONITOR = "monitor";
    
    /**
     * 验证任务：用于数据验证和质量检查。
     * 
     * <p>通常需要特殊的查询配置来检查数据完整性。</p>
     */
    public static final String VALIDATE = "validate";
    
    /**
     * 分析任务：用于数据分析和统计。
     * 
     * <p>可能需要不同的查询配置以支持复杂的分析需求。</p>
     */
    public static final String ANALYZE = "analyze";
    
    /**
     * 导出任务：用于数据导出。
     * 
     * <p>通常配置较大的批次大小和较长的超时时间。</p>
     */
    public static final String EXPORT = "export";
    
    // 防止实例化
    private TaskTypes() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}