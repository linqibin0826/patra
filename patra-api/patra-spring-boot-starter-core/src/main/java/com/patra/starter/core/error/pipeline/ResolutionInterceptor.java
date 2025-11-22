package com.patra.starter.core.error.pipeline;

import com.patra.starter.core.error.model.ErrorResolution;

/// 错误解析管道中的拦截器接口,用于实现横切关注点,如熔断保护和指标收集。
/// 
/// 拦截器按照 {@link org.springframework.core.annotation.Order} 注解指定的顺序执行, 实现类可以在错误解析前后插入自定义逻辑。
/// 
/// 使用示例:
/// 
/// ```java
/// @Component
/// @Order(100)
/// public class MyInterceptor implements ResolutionInterceptor {
///   @Override
///   public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
///     // 前置处理
///     log.info("处理异常: {", exception.getMessage());
/// 
///     // 调用链下游
///     ErrorResolution resolution = invocation.proceed(exception);
/// 
///     // 后置处理
///     return resolution;
/// ```
/// 
/// @author Patra Team
/// @since 2.0
/// @see ResolutionInvocation
/// @see ErrorResolutionPipeline
public interface ResolutionInterceptor {

  /// 应用拦截逻辑并委托给管道中的下一步。
/// 
/// @param exception 正在解析的异常
/// @param invocation 管道调用对象,用于传递控制权给下一个拦截器或引擎
/// @return 解析后的错误
  ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation);
}
