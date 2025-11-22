package com.patra.starter.core.error.pipeline;

import com.patra.starter.core.error.engine.ErrorResolutionEngine;
import com.patra.starter.core.error.model.ErrorResolution;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/// 错误解析管道,执行配置的 {@link ResolutionInterceptor} 链并委托给 {@link ErrorResolutionEngine} 获取标准化的错误表示。
///
/// 执行流程:
///
/// ```
///
/// 异常输入
///   ↓
/// 拦截器链 (按 @Order 排序)
///   ├─ TracingInterceptor
///   ├─ MetricsInterceptor
///   ├─ CircuitBreakerInterceptor (可选)
///   └─ ... (自定义拦截器)
///   ↓
/// ErrorResolutionEngine (核心解析逻辑)
///   ↓
/// ErrorResolution (标准化错误表示)
///
/// ```
///
/// 拦截器的执行顺序由 {@link org.springframework.core.annotation.Order} 注解决定, 数值越小优先级越高。
///
/// @author linqibin
/// @since 0.1.0
public class ErrorResolutionPipeline {

  private final ErrorResolutionEngine engine;
  private final List<ResolutionInterceptor> interceptors;

  /// 构造错误解析管道。
  ///
  /// @param engine 错误解析引擎
  /// @param interceptors 拦截器列表,将按 @Order 注解排序

  public ErrorResolutionPipeline(
      ErrorResolutionEngine engine, List<ResolutionInterceptor> interceptors) {
    this.engine = engine;
    if (interceptors == null || interceptors.isEmpty()) {
      this.interceptors = Collections.emptyList();
    } else {
      List<ResolutionInterceptor> ordered = new ArrayList<>(interceptors);
      AnnotationAwareOrderComparator.sort(ordered);
      this.interceptors = Collections.unmodifiableList(ordered);
    }
  }

  /// 通过拦截器管道解析提供的异常。
  ///
  /// @param exception 要解析的异常
  /// @return 标准化的错误表示
  public ErrorResolution resolve(Throwable exception) {
    ResolutionInvocation invocation = buildInvocationChain();
    return invocation.proceed(exception);
  }

  /// 构建拦截器调用链。
  ///
  /// 使用责任链模式,从后向前构建嵌套的调用链,最内层是引擎解析调用。
  ///
  /// @return 构建好的调用链头部
  private ResolutionInvocation buildInvocationChain() {
    ResolutionInvocation tail = engine::resolve;
    for (int i = interceptors.size() - 1; i >= 0; i--) {
      ResolutionInterceptor interceptor = interceptors.get(i);
      ResolutionInvocation next = tail;
      tail = ex -> interceptor.intercept(ex, next);
    }
    return tail;
  }

  /// 返回按应用顺序排列的拦截器列表。
  ///
  /// @return 已排序的不可变拦截器列表
  public List<ResolutionInterceptor> getInterceptors() {
    return interceptors;
  }
}
