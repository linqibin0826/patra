package com.patra.starter.core.cqrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.common.cqrs.Command;
import com.patra.common.cqrs.CommandHandler;
import com.patra.common.cqrs.CommandHandlerNotFoundException;
import com.patra.common.cqrs.CommandInterceptor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// SimpleCommandBus 单元测试。
@DisplayName("SimpleCommandBus")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class SimpleCommandBusTest {

  // ==================== 测试用 Command 和 Handler ====================

  record TestCommand(String value) implements Command<String> {}

  record AnotherCommand(int number) implements Command<Integer> {}

  record UnregisteredCommand() implements Command<Void> {}

  static class TestCommandHandler implements CommandHandler<TestCommand, String> {
    @Override
    public String handle(TestCommand command) {
      return "Handled: " + command.value();
    }
  }

  static class AnotherCommandHandler implements CommandHandler<AnotherCommand, Integer> {
    @Override
    public Integer handle(AnotherCommand command) {
      return command.number() * 2;
    }
  }

  static class FailingHandler implements CommandHandler<TestCommand, String> {
    @Override
    public String handle(TestCommand command) {
      throw new RuntimeException("Handler failed");
    }
  }

  // ==================== 测试用拦截器 ====================

  static class RecordingInterceptor implements CommandInterceptor {
    private final String name;
    private final List<String> events;

    RecordingInterceptor(String name, List<String> events) {
      this.name = name;
      this.events = events;
    }

    @Override
    public <R> R intercept(Command<R> command, CommandExecutor<R> next) {
      events.add(name + "-before");
      try {
        R result = next.execute(command);
        events.add(name + "-after");
        return result;
      } catch (Exception e) {
        events.add(name + "-error");
        throw e;
      }
    }
  }

  // ==================== 同步执行测试 ====================

  @Nested
  @DisplayName("handle() 同步执行")
  class HandleSync {

    @Test
    @DisplayName("应该将命令路由到正确的处理器")
    void shouldRouteCommandToCorrectHandler() {
      // given
      var bus = new SimpleCommandBus(List.of(new TestCommandHandler()), List.of(), Runnable::run);

      // when
      String result = bus.handle(new TestCommand("test"));

      // then
      assertThat(result).isEqualTo("Handled: test");
    }

    @Test
    @DisplayName("应该支持多个不同类型的命令处理器")
    void shouldSupportMultipleHandlers() {
      // given
      var bus =
          new SimpleCommandBus(
              List.of(new TestCommandHandler(), new AnotherCommandHandler()),
              List.of(),
              Runnable::run);

      // when
      String stringResult = bus.handle(new TestCommand("hello"));
      Integer intResult = bus.handle(new AnotherCommand(21));

      // then
      assertThat(stringResult).isEqualTo("Handled: hello");
      assertThat(intResult).isEqualTo(42);
    }

    @Test
    @DisplayName("当找不到处理器时应该抛出 CommandHandlerNotFoundException")
    void shouldThrowWhenHandlerNotFound() {
      // given
      var bus = new SimpleCommandBus(List.of(), List.of(), Runnable::run);

      // when & then
      assertThatThrownBy(() -> bus.handle(new UnregisteredCommand()))
          .isInstanceOf(CommandHandlerNotFoundException.class)
          .hasMessageContaining("UnregisteredCommand");
    }

    @Test
    @DisplayName("当命令为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowWhenCommandIsNull() {
      // given
      var bus = new SimpleCommandBus(List.of(), List.of(), Runnable::run);

      // when & then
      assertThatThrownBy(() -> bus.handle(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("当处理器抛出异常时应该传播异常")
    void shouldPropagateHandlerException() {
      // given
      var bus = new SimpleCommandBus(List.of(new FailingHandler()), List.of(), Runnable::run);

      // when & then
      assertThatThrownBy(() -> bus.handle(new TestCommand("test")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Handler failed");
    }
  }

  // ==================== 拦截器测试 ====================

  @Nested
  @DisplayName("拦截器链")
  class InterceptorChain {

    @Test
    @DisplayName("应该按 Order 顺序执行拦截器（外层先进后出）")
    void shouldExecuteInterceptorsInOrder() {
      // given
      var events = new ArrayList<String>();
      var interceptor1 = new RecordingInterceptor("first", events);
      var interceptor2 = new RecordingInterceptor("second", events);

      var bus =
          new SimpleCommandBus(
              List.of(new TestCommandHandler()),
              List.of(interceptor1, interceptor2),
              Runnable::run);

      // when
      bus.handle(new TestCommand("test"));

      // then - first 在外层，second 在内层
      assertThat(events)
          .containsExactly("first-before", "second-before", "second-after", "first-after");
    }

    @Test
    @DisplayName("当处理器失败时拦截器应该能捕获错误")
    void shouldCaptureErrorInInterceptors() {
      // given
      var events = new ArrayList<String>();
      var interceptor = new RecordingInterceptor("interceptor", events);

      var bus =
          new SimpleCommandBus(List.of(new FailingHandler()), List.of(interceptor), Runnable::run);

      // when & then
      assertThatThrownBy(() -> bus.handle(new TestCommand("test")))
          .isInstanceOf(RuntimeException.class);

      assertThat(events).containsExactly("interceptor-before", "interceptor-error");
    }

    @Test
    @DisplayName("没有拦截器时应该直接调用处理器")
    void shouldWorkWithoutInterceptors() {
      // given
      var bus = new SimpleCommandBus(List.of(new TestCommandHandler()), List.of(), Runnable::run);

      // when
      String result = bus.handle(new TestCommand("direct"));

      // then
      assertThat(result).isEqualTo("Handled: direct");
    }
  }

  // ==================== 异步执行测试 ====================

  @Nested
  @DisplayName("handleAsync() 异步执行")
  class HandleAsync {

    @Test
    @DisplayName("应该异步执行命令并返回 Future")
    void shouldExecuteAsyncAndReturnFuture() throws Exception {
      // given
      var bus =
          new SimpleCommandBus(
              List.of(new TestCommandHandler()), List.of(), Runnable::run // 使用同步执行器简化测试
              );

      // when
      CompletableFuture<String> future = bus.handleAsync(new TestCommand("async"));

      // then
      assertThat(future.get()).isEqualTo("Handled: async");
    }

    @Test
    @DisplayName("异步执行失败时 Future 应该包含异常")
    void shouldContainExceptionInFutureWhenFailed() {
      // given
      var bus = new SimpleCommandBus(List.of(new FailingHandler()), List.of(), Runnable::run);

      // when
      CompletableFuture<String> future = bus.handleAsync(new TestCommand("test"));

      // then
      assertThat(future).isCompletedExceptionally();
    }

    @Test
    @DisplayName("当命令为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowWhenAsyncCommandIsNull() {
      // given
      var bus = new SimpleCommandBus(List.of(), List.of(), Runnable::run);

      // when & then
      assertThatThrownBy(() -> bus.handleAsync(null)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ==================== 辅助方法测试 ====================

  @Nested
  @DisplayName("辅助方法")
  class HelperMethods {

    @Test
    @DisplayName("getHandlerCount() 应该返回注册的处理器数量")
    void shouldReturnHandlerCount() {
      // given
      var bus =
          new SimpleCommandBus(
              List.of(new TestCommandHandler(), new AnotherCommandHandler()),
              List.of(),
              Runnable::run);

      // when & then
      assertThat(bus.getHandlerCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("hasHandler() 应该正确检测处理器是否存在")
    void shouldDetectHandlerExistence() {
      // given
      var bus = new SimpleCommandBus(List.of(new TestCommandHandler()), List.of(), Runnable::run);

      // when & then
      assertThat(bus.hasHandler(TestCommand.class)).isTrue();
      assertThat(bus.hasHandler(UnregisteredCommand.class)).isFalse();
    }
  }
}
