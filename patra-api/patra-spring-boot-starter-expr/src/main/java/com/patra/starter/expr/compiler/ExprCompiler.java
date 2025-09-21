package com.patra.starter.expr.compiler;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.*;
import com.patra.starter.expr.compiler.model.CompileRequest;
import com.patra.starter.expr.compiler.model.CompileRequestBuilder;
import com.patra.starter.expr.compiler.model.CompileResult;

import java.util.List;

public interface ExprCompiler {

    /**
     * 编译表达式为查询语句和参数。
     * 
     * @param request 编译请求
     * @return 编译结果
     */
    CompileResult compile(CompileRequest request);
    
    /**
     * 便捷方法：使用默认参数编译表达式。
     * 
     * <p>使用 SEARCH 操作和默认编译选项。</p>
     * 
     * @param expression 表达式
     * @param provenance 数据来源
     * @return 编译结果
     */
    default CompileResult compile(Expr expression, ProvenanceCode provenance) {
        CompileRequest request = CompileRequestBuilder.of(expression, provenance).build();
        return compile(request);
    }
    
    /**
     * 便捷方法：编译指定任务类型的表达式。
     * 
     * @param expression 表达式
     * @param provenance 数据来源
     * @param taskType 任务类型
     * @return 编译结果
     */
    default CompileResult compile(Expr expression, ProvenanceCode provenance, String taskType) {
        CompileRequest request = CompileRequestBuilder.of(expression, provenance)
            .forTask(taskType)
            .build();
        return compile(request);
    }
    
    /**
     * 便捷方法：编译指定任务类型和操作的表达式。
     * 
     * @param expression 表达式
     * @param provenance 数据来源  
     * @param taskType 任务类型
     * @param operationCode 操作代码
     * @return 编译结果
     */
    default CompileResult compile(Expr expression, ProvenanceCode provenance, String taskType, String operationCode) {
        CompileRequest request = CompileRequestBuilder.of(expression, provenance)
            .forTask(taskType)
            .forOperation(operationCode)
            .build();
        return compile(request);
    }

    default boolean containsAtom(Expr expression) {
        return containsAtomInternal(expression);
    }

    private static boolean containsAtomInternal(Expr expr) {
        if (expr instanceof Atom) {
            return true;
        }
        if (expr instanceof And(List<Expr> children)) {
            return children.stream().anyMatch(ExprCompiler::containsAtomInternal);
        }
        if (expr instanceof Or(List<Expr> children)) {
            return children.stream().anyMatch(ExprCompiler::containsAtomInternal);
        }
        if (expr instanceof Not(Expr child)) {
            return containsAtomInternal(child);
        }
        return false;
    }
}
