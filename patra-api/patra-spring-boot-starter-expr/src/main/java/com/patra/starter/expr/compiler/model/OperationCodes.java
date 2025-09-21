package com.patra.starter.expr.compiler.model;

/**
 * 表达式编译器常用的操作代码常量。
 * 
 * <p>操作代码用于标识不同的API操作类型，系统会根据操作代码选择对应的端点配置和渲染规则。</p>
 * 
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 使用常量而不是硬编码字符串
 * CompileRequest request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
 *     .forOperation(OperationCodes.SEARCH)
 *     .build();
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class OperationCodes {
    
    /**
     * 搜索操作：用于获取符合条件的记录列表。
     * 
     * <p>这是最常用的操作类型，通常返回 ID 列表或分页结果。</p>
     */
    public static final String SEARCH = "SEARCH";
    
    /**
     * 详情操作：用于获取特定记录的完整信息。
     * 
     * <p>通常需要提供记录 ID，返回详细的元数据信息。</p>
     */
    public static final String DETAIL = "DETAIL";
    
    /**
     * 列表操作：用于获取简化的记录列表。
     * 
     * <p>与 SEARCH 类似，但可能返回更少的字段或不同的格式。</p>
     */
    public static final String LIST = "LIST";
    
    /**
     * 计数操作：用于获取符合条件的记录数量。
     * 
     * <p>只返回计数信息，不返回实际记录数据。</p>
     */
    public static final String COUNT = "COUNT";
    
    /**
     * 获取操作：通用的数据获取操作。
     * 
     * <p>一些API使用 FETCH 而不是 DETAIL 来表示详情获取。</p>
     */
    public static final String FETCH = "FETCH";
    
    /**
     * 查询操作：与搜索类似但语义上更偏向复杂查询。
     */
    public static final String QUERY = "QUERY";
    
    /**
     * 导出操作：用于批量数据导出。
     */
    public static final String EXPORT = "EXPORT";
    
    // 防止实例化
    private OperationCodes() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}