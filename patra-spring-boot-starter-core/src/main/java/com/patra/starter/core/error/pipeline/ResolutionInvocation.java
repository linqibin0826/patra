package com.patra.starter.core.error.pipeline;

import com.patra.starter.core.error.model.ErrorResolution;

/**
 * 表示错误解析管道中的执行链,允许拦截器将控制权委托给下一步。
 *
 * <p>这是一个函数式接口,拦截器通过调用 {@link #proceed(Throwable)} 方法传递异常到管道下游。
 *
 * @author Papertrace Team
 * @since 2.0
 * @see ResolutionInterceptor
 */
@FunctionalInterface
public interface ResolutionInvocation {

  /**
   * 继续执行拦截器链中的下一步。
   *
   * @param exception 当前正在解析的异常
   * @return 解析后的错误表示
   */
  ErrorResolution proceed(Throwable exception);
}
