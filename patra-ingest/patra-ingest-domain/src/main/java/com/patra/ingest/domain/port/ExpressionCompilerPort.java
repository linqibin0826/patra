package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.vo.expression.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.expression.ExprCompilationResult;

/**
 * 表达式编译器端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>: 将采集表达式编译为可执行的查询和参数。
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>输出端口(Output Port)</b>,定义在 Domain 层,由基础设施层(Infrastructure)实现,委托给
 * patra-spring-boot-starter-expr 模块的 ExprCompiler。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExpressionCompilerPort {

  /**
   * 编译表达式。
   *
   * <p><b>实现细节</b>: 基础设施层实现(ExpressionCompilerPortImpl)负责:
   *
   * <ul>
   *   <li>使用 ExprJsonCodec 将 JSON 表达式快照转换为 Expr 对象
   *   <li>构建 CompileRequest 并设置合适的选项
   *   <li>调用 ExprCompiler.compile()
   *   <li>将 CompileResult 转换回领域的 ExprCompilationResult
   * </ul>
   *
   * @param request 编译请求
   * @return 编译结果(包含成功/失败标志、查询、参数、错误消息等)
   */
  ExprCompilationResult compile(ExprCompilationRequest request);
}
