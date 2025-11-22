package com.patra.starter.core.error.pipeline;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.engine.ErrorResolutionEngine;
import com.patra.starter.core.error.model.ErrorResolution;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.Order;

/// {@link ErrorResolutionPipeline} 单元测试。
/// 
/// 测试策略: 应用层服务 - Mock 测试，验证拦截器链执行逻辑。
/// 
/// 测试覆盖:
/// 
/// - ✅ 拦截器链执行顺序
///   - ✅ 空拦截器列表处理
///   - ✅ 单个拦截器执行
///   - ✅ 多个拦截器执行
///   - ✅ @Order 注解排序
///   - ✅ 拦截器前置和后置处理
///   - ✅ 异常传播
/// 
/// @author Patra Team
/// @since 2.0
@DisplayName("ErrorResolutionPipeline 单元测试")
@ExtendWith(MockitoExtension.class)
class ErrorResolutionPipelineTest {

  @Mock private ErrorResolutionEngine mockEngine;

  private ErrorResolution defaultResolution;

  @BeforeEach
  void setUp() {
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    // 使用 lenient stubbing，因为不是所有测试都会调用这些 mock
    lenient().when(errorCode.code()).thenReturn("TEST:0500");
    lenient().when(errorCode.httpStatus()).thenReturn(500);
    defaultResolution = new ErrorResolution(errorCode, 500);

    // 使用 lenient stubbing，因为不是所有测试都会调用 mockEngine
    lenient().when(mockEngine.resolve(any())).thenReturn(defaultResolution);
  }

  @Nested
  @DisplayName("空拦截器列表测试")
  class EmptyInterceptorListTests {

    @Test
    @DisplayName("应该处理 null 拦截器列表")
    void shouldHandleNullInterceptorList() {
      // Given
      ErrorResolutionPipeline pipeline = new ErrorResolutionPipeline(mockEngine, null);
      RuntimeException exception = new RuntimeException("test");

      // When
      ErrorResolution resolution = pipeline.resolve(exception);

      // Then
      assertThat(resolution).isEqualTo(defaultResolution);
      assertThat(pipeline.getInterceptors()).isEmpty();
      verify(mockEngine).resolve(exception);
    }

    @Test
    @DisplayName("应该处理空拦截器列表")
    void shouldHandleEmptyInterceptorList() {
      // Given
      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, Collections.emptyList());
      RuntimeException exception = new RuntimeException("test");

      // When
      ErrorResolution resolution = pipeline.resolve(exception);

      // Then
      assertThat(resolution).isEqualTo(defaultResolution);
      assertThat(pipeline.getInterceptors()).isEmpty();
      verify(mockEngine).resolve(exception);
    }

    @Test
    @DisplayName("空拦截器列表时应该直接调用引擎")
    void shouldCallEngineDirectlyWithNoInterceptors() {
      // Given
      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, Collections.emptyList());
      RuntimeException exception = new RuntimeException("test");

      // When
      pipeline.resolve(exception);

      // Then
      verify(mockEngine, times(1)).resolve(exception);
    }
  }

  @Nested
  @DisplayName("单个拦截器测试")
  class SingleInterceptorTests {

    @Test
    @DisplayName("应该执行单个拦截器")
    void shouldExecuteSingleInterceptor() {
      // Given
      ResolutionInterceptor interceptor = mock(ResolutionInterceptor.class);
      when(interceptor.intercept(any(), any()))
          .thenAnswer(
              invocation -> {
                Throwable ex = invocation.getArgument(0);
                ResolutionInvocation next = invocation.getArgument(1);
                return next.proceed(ex);
              });

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, List.of(interceptor));
      RuntimeException exception = new RuntimeException("test");

      // When
      ErrorResolution resolution = pipeline.resolve(exception);

      // Then
      assertThat(resolution).isEqualTo(defaultResolution);
      verify(interceptor).intercept(eq(exception), any());
      verify(mockEngine).resolve(exception);
    }

    @Test
    @DisplayName("单个拦截器应该能修改返回结果")
    void singleInterceptorShouldBeAbleToModifyResult() {
      // Given
      ErrorCodeLike modifiedErrorCode = mock(ErrorCodeLike.class);
      lenient().when(modifiedErrorCode.code()).thenReturn("MODIFIED:0400");
      lenient().when(modifiedErrorCode.httpStatus()).thenReturn(400);
      ErrorResolution modifiedResolution = new ErrorResolution(modifiedErrorCode, 400);

      ResolutionInterceptor interceptor =
          (ex, invocation) -> {
            invocation.proceed(ex); // 调用下游
            return modifiedResolution; // 返回修改后的结果
          };

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, List.of(interceptor));
      RuntimeException exception = new RuntimeException("test");

      // When
      ErrorResolution resolution = pipeline.resolve(exception);

      // Then
      assertThat(resolution).isEqualTo(modifiedResolution);
      assertThat(resolution.errorCode().code()).isEqualTo("MODIFIED:0400");
    }

    @Test
    @DisplayName("单个拦截器应该能执行前置处理")
    void singleInterceptorShouldExecutePreProcessing() {
      // Given
      List<String> executionLog = new ArrayList<>();

      ResolutionInterceptor interceptor =
          (ex, invocation) -> {
            executionLog.add("interceptor-before");
            ErrorResolution result = invocation.proceed(ex);
            executionLog.add("interceptor-after");
            return result;
          };

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, List.of(interceptor));

      // When
      pipeline.resolve(new RuntimeException("test"));

      // Then
      assertThat(executionLog).containsExactly("interceptor-before", "interceptor-after");
    }
  }

  @Nested
  @DisplayName("多个拦截器测试")
  class MultipleInterceptorsTests {

    @Test
    @DisplayName("应该按顺序执行多个拦截器")
    void shouldExecuteMultipleInterceptorsInOrder() {
      // Given
      ResolutionInterceptor interceptor1 = mock(ResolutionInterceptor.class);
      ResolutionInterceptor interceptor2 = mock(ResolutionInterceptor.class);
      ResolutionInterceptor interceptor3 = mock(ResolutionInterceptor.class);

      when(interceptor1.intercept(any(), any()))
          .thenAnswer(
              invocation ->
                  invocation
                      .<ResolutionInvocation>getArgument(1)
                      .proceed(invocation.getArgument(0)));
      when(interceptor2.intercept(any(), any()))
          .thenAnswer(
              invocation ->
                  invocation
                      .<ResolutionInvocation>getArgument(1)
                      .proceed(invocation.getArgument(0)));
      when(interceptor3.intercept(any(), any()))
          .thenAnswer(
              invocation ->
                  invocation
                      .<ResolutionInvocation>getArgument(1)
                      .proceed(invocation.getArgument(0)));

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(
              mockEngine, List.of(interceptor1, interceptor2, interceptor3));
      RuntimeException exception = new RuntimeException("test");

      // When
      pipeline.resolve(exception);

      // Then - 验证执行顺序
      InOrder inOrder = inOrder(interceptor1, interceptor2, interceptor3, mockEngine);
      inOrder.verify(interceptor1).intercept(eq(exception), any());
      inOrder.verify(interceptor2).intercept(eq(exception), any());
      inOrder.verify(interceptor3).intercept(eq(exception), any());
      inOrder.verify(mockEngine).resolve(exception);
    }

    @Test
    @DisplayName("多个拦截器应该形成调用链")
    void multipleInterceptorsShouldFormInvocationChain() {
      // Given
      List<String> executionLog = new ArrayList<>();

      ResolutionInterceptor interceptor1 =
          (ex, invocation) -> {
            executionLog.add("interceptor1-before");
            ErrorResolution result = invocation.proceed(ex);
            executionLog.add("interceptor1-after");
            return result;
          };

      ResolutionInterceptor interceptor2 =
          (ex, invocation) -> {
            executionLog.add("interceptor2-before");
            ErrorResolution result = invocation.proceed(ex);
            executionLog.add("interceptor2-after");
            return result;
          };

      ResolutionInterceptor interceptor3 =
          (ex, invocation) -> {
            executionLog.add("interceptor3-before");
            ErrorResolution result = invocation.proceed(ex);
            executionLog.add("interceptor3-after");
            return result;
          };

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(
              mockEngine, List.of(interceptor1, interceptor2, interceptor3));

      // When
      pipeline.resolve(new RuntimeException("test"));

      // Then - 验证嵌套执行顺序（洋葱模型）
      assertThat(executionLog)
          .containsExactly(
              "interceptor1-before",
              "interceptor2-before",
              "interceptor3-before",
              "interceptor3-after",
              "interceptor2-after",
              "interceptor1-after");
    }

    @Test
    @DisplayName("最后一个拦截器应该能访问引擎返回的结果")
    void lastInterceptorShouldReceiveEngineResult() {
      // Given
      ResolutionInterceptor interceptor1 = (ex, invocation) -> invocation.proceed(ex);

      ResolutionInterceptor interceptor2 =
          (ex, invocation) -> {
            ErrorResolution engineResult = invocation.proceed(ex);
            // 验证收到的是引擎返回的结果
            assertThat(engineResult).isEqualTo(defaultResolution);
            return engineResult;
          };

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, List.of(interceptor1, interceptor2));

      // When
      ErrorResolution result = pipeline.resolve(new RuntimeException("test"));

      // Then
      assertThat(result).isEqualTo(defaultResolution);
    }
  }

  @Nested
  @DisplayName("@Order 注解排序测试")
  class OrderAnnotationSortingTests {

    @Order(10)
    static class HighPriorityInterceptor implements ResolutionInterceptor {
      private final List<String> executionLog;

      HighPriorityInterceptor(List<String> executionLog) {
        this.executionLog = executionLog;
      }

      @Override
      public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
        executionLog.add("high-priority");
        return invocation.proceed(exception);
      }
    }

    @Order(50)
    static class MediumPriorityInterceptor implements ResolutionInterceptor {
      private final List<String> executionLog;

      MediumPriorityInterceptor(List<String> executionLog) {
        this.executionLog = executionLog;
      }

      @Override
      public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
        executionLog.add("medium-priority");
        return invocation.proceed(exception);
      }
    }

    @Order(100)
    static class LowPriorityInterceptor implements ResolutionInterceptor {
      private final List<String> executionLog;

      LowPriorityInterceptor(List<String> executionLog) {
        this.executionLog = executionLog;
      }

      @Override
      public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
        executionLog.add("low-priority");
        return invocation.proceed(exception);
      }
    }

    @Test
    @DisplayName("应该根据 @Order 注解值排序拦截器（数值越小优先级越高）")
    void shouldSortInterceptorsByOrderAnnotation() {
      // Given
      List<String> executionLog = new ArrayList<>();

      // 故意乱序添加
      List<ResolutionInterceptor> interceptors =
          List.of(
              new LowPriorityInterceptor(executionLog),
              new HighPriorityInterceptor(executionLog),
              new MediumPriorityInterceptor(executionLog));

      ErrorResolutionPipeline pipeline = new ErrorResolutionPipeline(mockEngine, interceptors);

      // When
      pipeline.resolve(new RuntimeException("test"));

      // Then - 应该按 Order 值从小到大执行
      assertThat(executionLog).containsExactly("high-priority", "medium-priority", "low-priority");
    }

    @Test
    @DisplayName("getInterceptors() 应该返回已排序的拦截器列表")
    void getInterceptorsShouldReturnSortedList() {
      // Given
      List<String> executionLog = new ArrayList<>();

      List<ResolutionInterceptor> interceptors =
          List.of(
              new LowPriorityInterceptor(executionLog),
              new HighPriorityInterceptor(executionLog),
              new MediumPriorityInterceptor(executionLog));

      ErrorResolutionPipeline pipeline = new ErrorResolutionPipeline(mockEngine, interceptors);

      // When
      List<ResolutionInterceptor> sortedInterceptors = pipeline.getInterceptors();

      // Then
      assertThat(sortedInterceptors).hasSize(3);
      assertThat(sortedInterceptors.get(0)).isInstanceOf(HighPriorityInterceptor.class);
      assertThat(sortedInterceptors.get(1)).isInstanceOf(MediumPriorityInterceptor.class);
      assertThat(sortedInterceptors.get(2)).isInstanceOf(LowPriorityInterceptor.class);
    }

    @Test
    @DisplayName("getInterceptors() 应该返回不可变列表")
    void getInterceptorsShouldReturnUnmodifiableList() {
      // Given
      ResolutionInterceptor interceptor = mock(ResolutionInterceptor.class);
      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, List.of(interceptor));

      // When
      List<ResolutionInterceptor> interceptors = pipeline.getInterceptors();

      // Then
      assertThatThrownBy(() -> interceptors.add(mock(ResolutionInterceptor.class)))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("拦截器修改行为测试")
  class InterceptorModificationTests {

    @Test
    @DisplayName("拦截器应该能够短路执行（不调用 proceed）")
    void interceptorShouldBeAbleToShortCircuit() {
      // Given
      ErrorCodeLike shortCircuitErrorCode = mock(ErrorCodeLike.class);
      lenient().when(shortCircuitErrorCode.code()).thenReturn("SHORT:0000");
      lenient().when(shortCircuitErrorCode.httpStatus()).thenReturn(200);
      ErrorResolution shortCircuitResolution = new ErrorResolution(shortCircuitErrorCode, 200);

      ResolutionInterceptor shortCircuitInterceptor =
          (ex, invocation) -> shortCircuitResolution; // 不调用 proceed

      ResolutionInterceptor secondInterceptor = mock(ResolutionInterceptor.class);

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(
              mockEngine, List.of(shortCircuitInterceptor, secondInterceptor));

      // When
      ErrorResolution resolution = pipeline.resolve(new RuntimeException("test"));

      // Then
      assertThat(resolution).isEqualTo(shortCircuitResolution);
      verifyNoInteractions(secondInterceptor); // 第二个拦截器不应该被调用
      verifyNoInteractions(mockEngine); // 引擎也不应该被调用
    }

    @Test
    @DisplayName("拦截器应该能够传播修改后的异常")
    void interceptorShouldBeAbleToPropagateModifiedException() {
      // Given
      List<Throwable> capturedExceptions = new ArrayList<>();

      ResolutionInterceptor wrapperInterceptor =
          (ex, invocation) -> {
            // 包装异常
            IllegalStateException wrappedException = new IllegalStateException("wrapped", ex);
            return invocation.proceed(wrappedException);
          };

      ResolutionInterceptor capturingInterceptor =
          (ex, invocation) -> {
            capturedExceptions.add(ex);
            return invocation.proceed(ex);
          };

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(
              mockEngine, List.of(wrapperInterceptor, capturingInterceptor));

      RuntimeException originalException = new RuntimeException("original");

      // When
      pipeline.resolve(originalException);

      // Then
      assertThat(capturedExceptions).hasSize(1);
      assertThat(capturedExceptions.get(0)).isInstanceOf(IllegalStateException.class);
      assertThat(capturedExceptions.get(0).getCause()).isEqualTo(originalException);
    }

    @Test
    @DisplayName("拦截器应该能够链式修改结果")
    void interceptorsShouldBeAbleToChainModifyResults() {
      // Given
      // 创建一个自定义的 ErrorCodeLike 实现来避免 mock 问题
      class ModifiableErrorCode implements ErrorCodeLike {
        private final String code;
        private final int httpStatus;

        ModifiableErrorCode(String code, int httpStatus) {
          this.code = code;
          this.httpStatus = httpStatus;
        }

        @Override
        public String code() {
          return code;
        }

        @Override
        public int httpStatus() {
          return httpStatus;
        }
      }

      ResolutionInterceptor modifier1 =
          (ex, invocation) -> {
            ErrorResolution original = invocation.proceed(ex);
            ErrorCodeLike modified1 =
                new ModifiableErrorCode(
                    "MODIFIED1:" + original.errorCode().code(), original.httpStatus());
            return new ErrorResolution(modified1, original.httpStatus());
          };

      ResolutionInterceptor modifier2 =
          (ex, invocation) -> {
            ErrorResolution original = invocation.proceed(ex);
            ErrorCodeLike modified2 =
                new ModifiableErrorCode(
                    "MODIFIED2:" + original.errorCode().code(), original.httpStatus());
            return new ErrorResolution(modified2, original.httpStatus());
          };

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, List.of(modifier1, modifier2));

      // When
      ErrorResolution result = pipeline.resolve(new RuntimeException("test"));

      // Then
      assertThat(result.errorCode().code()).startsWith("MODIFIED1:MODIFIED2:");
    }
  }

  @Nested
  @DisplayName("异常处理测试")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("拦截器抛出异常时应该向上传播")
    void interceptorExceptionShouldPropagate() {
      // Given
      RuntimeException interceptorException = new RuntimeException("interceptor failed");

      ResolutionInterceptor failingInterceptor =
          (ex, invocation) -> {
            throw interceptorException;
          };

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, List.of(failingInterceptor));

      // When & Then
      assertThatThrownBy(() -> pipeline.resolve(new RuntimeException("test")))
          .isEqualTo(interceptorException);
    }

    @Test
    @DisplayName("引擎抛出异常时应该向上传播")
    void engineExceptionShouldPropagate() {
      // Given
      RuntimeException engineException = new RuntimeException("engine failed");
      when(mockEngine.resolve(any())).thenThrow(engineException);

      ResolutionInterceptor interceptor = (ex, invocation) -> invocation.proceed(ex);

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, List.of(interceptor));

      // When & Then
      assertThatThrownBy(() -> pipeline.resolve(new RuntimeException("test")))
          .isEqualTo(engineException);
    }

    @Test
    @DisplayName("拦截器应该能够捕获并处理下游异常")
    void interceptorShouldBeAbleToCatchDownstreamException() {
      // Given
      // 重置 setUp 中的 stubbing
      reset(mockEngine);

      RuntimeException downstreamException = new RuntimeException("downstream failed");
      when(mockEngine.resolve(any())).thenThrow(downstreamException);

      ErrorCodeLike fallbackErrorCode = mock(ErrorCodeLike.class);
      lenient().when(fallbackErrorCode.code()).thenReturn("FALLBACK:0500");
      lenient().when(fallbackErrorCode.httpStatus()).thenReturn(500);
      ErrorResolution fallbackResolution = new ErrorResolution(fallbackErrorCode, 500);

      ResolutionInterceptor errorHandlingInterceptor =
          (ex, invocation) -> {
            try {
              return invocation.proceed(ex);
            } catch (RuntimeException e) {
              // 捕获并处理异常
              return fallbackResolution;
            }
          };

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, List.of(errorHandlingInterceptor));

      // When
      ErrorResolution result = pipeline.resolve(new RuntimeException("test"));

      // Then
      assertThat(result).isEqualTo(fallbackResolution);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionsTests {

    @Test
    @DisplayName("应该处理大量拦截器")
    void shouldHandleManyInterceptors() {
      // Given
      List<ResolutionInterceptor> manyInterceptors = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        ResolutionInterceptor interceptor = (ex, invocation) -> invocation.proceed(ex);
        manyInterceptors.add(interceptor);
      }

      ErrorResolutionPipeline pipeline = new ErrorResolutionPipeline(mockEngine, manyInterceptors);

      // When
      ErrorResolution result = pipeline.resolve(new RuntimeException("test"));

      // Then
      assertThat(result).isEqualTo(defaultResolution);
      assertThat(pipeline.getInterceptors()).hasSize(100);
    }

    @Test
    @DisplayName("应该处理 null 异常输入")
    void shouldHandleNullException() {
      // Given
      ResolutionInterceptor interceptor = (ex, invocation) -> invocation.proceed(ex);

      ErrorResolutionPipeline pipeline =
          new ErrorResolutionPipeline(mockEngine, List.of(interceptor));

      // When
      pipeline.resolve(null);

      // Then
      verify(mockEngine).resolve(null);
    }
  }
}
