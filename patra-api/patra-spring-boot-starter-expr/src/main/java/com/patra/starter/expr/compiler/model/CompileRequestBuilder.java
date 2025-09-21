package com.patra.starter.expr.compiler.model;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;

import java.util.Objects;

/**
 * {@link CompileRequest} 的便捷构建器。
 * 
 * <p>提供链式调用 API，简化复杂参数设置，并提供常用配置的预设。</p>
 * 
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 最简单的搜索请求
 * CompileRequest request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
 *     .build();
 * 
 * // 指定操作类型的请求  
 * CompileRequest request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
 *     .forOperation(OperationCodes.DETAIL)
 *     .build();
 * 
 * // 完整配置
 * CompileRequest request = CompileRequestBuilder.of(expr, ProvenanceCode.CROSSREF)
 *     .forTask(TaskTypes.UPDATE)
 *     .forOperation(OperationCodes.SEARCH)
 *     .withOptions(CompileOptions.defaults().withStrict(false))
 *     .build();
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 * @see CompileRequest
 * @see CompileOptions
 */
public class CompileRequestBuilder {
    
    private final Expr expression;
    private final ProvenanceCode provenance;
    private String taskType;
    private String operationCode = OperationCodes.SEARCH; // 默认搜索操作
    private CompileOptions options = CompileOptions.defaults();
    
    private CompileRequestBuilder(Expr expression, ProvenanceCode provenance) {
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        this.provenance = Objects.requireNonNull(provenance, "provenance cannot be null");
    }
    
    /**
     * 创建构建器实例。
     * 
     * @param expression 表达式，不能为 null
     * @param provenance 数据来源，不能为 null
     * @return 构建器实例
     */
    public static CompileRequestBuilder of(Expr expression, ProvenanceCode provenance) {
        return new CompileRequestBuilder(expression, provenance);
    }
    
    /**
     * 设置任务类型。
     * 
     * <p>任务类型用于区分同一数据源的不同业务场景，例如：</p>
     * <ul>
     *   <li>{@link TaskTypes#UPDATE} - 增量更新场景</li>
     *   <li>{@link TaskTypes#FULL} - 全量同步场景</li>
     *   <li>{@link TaskTypes#SEARCH} - 搜索场景</li>
     * </ul>
     * 
     * @param taskType 任务类型，null 表示使用来源级配置
     * @return 当前构建器实例
     */
    public CompileRequestBuilder forTask(String taskType) {
        this.taskType = taskType;
        return this;
    }
    
    /**
     * 设置操作代码。
     * 
     * <p>操作代码定义了具体的 API 操作类型，常用值：</p>
     * <ul>
     *   <li>{@link OperationCodes#SEARCH} - 搜索操作（默认）</li>
     *   <li>{@link OperationCodes#DETAIL} - 详情获取</li>
     *   <li>{@link OperationCodes#LIST} - 列表查询</li>
     *   <li>{@link OperationCodes#COUNT} - 计数查询</li>
     * </ul>
     * 
     * @param operationCode 操作代码，会自动转换为大写
     * @return 当前构建器实例
     */
    public CompileRequestBuilder forOperation(String operationCode) {
        this.operationCode = operationCode;
        return this;
    }
    
    /**
     * 设置编译选项。
     * 
     * @param options 编译选项，null 将使用默认选项
     * @return 当前构建器实例
     */
    public CompileRequestBuilder withOptions(CompileOptions options) {
        this.options = options != null ? options : CompileOptions.defaults();
        return this;
    }
    
    /**
     * 设置为严格模式。
     * 
     * @param strict true 为严格模式，false 为宽松模式
     * @return 当前构建器实例
     */
    public CompileRequestBuilder withStrict(boolean strict) {
        this.options = this.options.withStrict(strict);
        return this;
    }
    
    /**
     * 设置查询长度限制。
     * 
     * @param maxQueryLength 最大查询长度，0 表示不限制
     * @return 当前构建器实例
     */
    public CompileRequestBuilder withMaxQueryLength(int maxQueryLength) {
        this.options = this.options.withMaxQueryLength(maxQueryLength);
        return this;
    }
    
    /**
     * 设置时区。
     * 
     * @param timezone 时区标识，如 "UTC"、"Asia/Shanghai"
     * @return 当前构建器实例
     */
    public CompileRequestBuilder withTimezone(String timezone) {
        this.options = this.options.withTimezone(timezone);
        return this;
    }
    
    /**
     * 启用或禁用跟踪模式。
     * 
     * @param traceEnabled true 启用跟踪，false 禁用跟踪
     * @return 当前构建器实例
     */
    public CompileRequestBuilder withTraceEnabled(boolean traceEnabled) {
        this.options = this.options.withTraceEnabled(traceEnabled);
        return this;
    }
    
    /**
     * 构建 {@link CompileRequest} 实例。
     * 
     * @return 编译请求实例
     */
    public CompileRequest build() {
        return new CompileRequest(expression, provenance, taskType, operationCode, options);
    }
}