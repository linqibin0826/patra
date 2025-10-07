package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.vo.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.ExprCompilationResult;

/**
 * 表达式编译端口（Port）。
 * <p>将原始表达式编译为数据源可执行的查询与参数。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExpressionCompilerPort {

    /**
     * 编译表达式。 TODO 调用 patra-spring-boot-starter-expr 模块的 compile方法。
     *
     * @param request 编译请求
     * @return 编译结果（包含成功/失败标志、查询、参数、错误信息等）
     */
    ExprCompilationResult compile(ExprCompilationRequest request);
}
