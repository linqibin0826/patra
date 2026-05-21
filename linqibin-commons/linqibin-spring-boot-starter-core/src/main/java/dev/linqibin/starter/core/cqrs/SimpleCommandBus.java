package dev.linqibin.starter.core.cqrs;

import dev.linqibin.commons.cqrs.Command;
import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.commons.cqrs.CommandHandlerNotFoundException;
import dev.linqibin.commons.cqrs.CommandInterceptor;
import dev.linqibin.commons.cqrs.CommandInterceptor.CommandExecutor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/// CommandBus 的 Spring 实现。
///
/// 自动扫描所有 CommandHandler Bean，根据 Command 类型路由到对应 Handler。
/// 支持拦截器链和异步执行。
///
/// ## 特性
///
/// - **自动注册**：通过构造器注入自动收集所有 CommandHandler Bean
/// - **类型路由**：根据 Command 的 Class 类型精确路由到对应 Handler
/// - **拦截器链**：按 `@Order` 顺序执行拦截器
/// - **异步支持**：通过 `handleAsync()` 提交到异步线程池
///
/// ## 线程安全
///
/// 本类是线程安全的，Handler 注册表使用 ConcurrentHashMap。
public class SimpleCommandBus implements CommandBus {

  private static final Logger log = LoggerFactory.getLogger(SimpleCommandBus.class);

  private final Map<Class<?>, CommandHandler<?, ?>> handlers = new ConcurrentHashMap<>();
  private final List<CommandInterceptor> interceptors;
  private final Executor asyncExecutor;

  /// 构造 SimpleCommandBus。
  ///
  /// @param handlerBeans 所有 CommandHandler Bean，由 Spring 自动注入
  /// @param interceptors 所有 CommandInterceptor Bean，由 Spring 自动注入
  /// @param asyncExecutor 异步执行器
  public SimpleCommandBus(
      List<CommandHandler<?, ?>> handlerBeans,
      List<CommandInterceptor> interceptors,
      Executor asyncExecutor) {
    this.interceptors =
        interceptors.stream().sorted(AnnotationAwareOrderComparator.INSTANCE).toList();
    this.asyncExecutor = asyncExecutor;
    registerHandlers(handlerBeans);

    log.info("CommandBus 初始化完成: {} 个 Handler, {} 个拦截器", handlers.size(), interceptors.size());
  }

  private void registerHandlers(List<CommandHandler<?, ?>> handlerBeans) {
    for (CommandHandler<?, ?> handler : handlerBeans) {
      Class<?> commandType = extractCommandType(handler.getClass());
      if (commandType != null) {
        CommandHandler<?, ?> existing = handlers.put(commandType, handler);
        if (existing != null) {
          log.warn(
              "Command {} 存在重复的 Handler: {} 被 {} 覆盖",
              commandType.getSimpleName(),
              existing.getClass().getSimpleName(),
              handler.getClass().getSimpleName());
        }
        log.debug(
            "注册 Handler: {} -> {}",
            commandType.getSimpleName(),
            handler.getClass().getSimpleName());
      }
    }
  }

  /// 从 Handler 类提取其处理的 Command 类型。
  ///
  /// 遍历类实现的接口，找到 `CommandHandler<C, R>` 并提取泛型参数 C。
  ///
  /// @param handlerClass Handler 类
  /// @return Command 类型，如果无法提取则返回 null
  private Class<?> extractCommandType(Class<?> handlerClass) {
    // 检查直接实现的接口
    for (Type iface : handlerClass.getGenericInterfaces()) {
      Class<?> commandType = extractFromParameterizedType(iface);
      if (commandType != null) {
        return commandType;
      }
    }

    // 检查父类（支持抽象基类）
    Class<?> superclass = handlerClass.getSuperclass();
    if (superclass != null && superclass != Object.class) {
      return extractCommandType(superclass);
    }

    return null;
  }

  private Class<?> extractFromParameterizedType(Type type) {
    if (type instanceof ParameterizedType pt) {
      Type rawType = pt.getRawType();
      if (rawType == CommandHandler.class) {
        Type cmdType = pt.getActualTypeArguments()[0];
        if (cmdType instanceof Class<?> cls) {
          return cls;
        }
        // 处理嵌套泛型的情况
        if (cmdType instanceof ParameterizedType nestedPt) {
          Type nestedRaw = nestedPt.getRawType();
          if (nestedRaw instanceof Class<?> cls) {
            return cls;
          }
        }
      }
      // 检查是否是 CommandHandler 的子接口
      if (rawType instanceof Class<?> rawClass && CommandHandler.class.isAssignableFrom(rawClass)) {
        return extractFromParameterizedType(rawClass.getGenericInterfaces()[0]);
      }
    }
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R handle(Command<R> command) {
    if (command == null) {
      throw new IllegalArgumentException("Command must not be null");
    }

    CommandHandler<Command<R>, R> handler =
        (CommandHandler<Command<R>, R>) handlers.get(command.getClass());

    if (handler == null) {
      throw new CommandHandlerNotFoundException(command.getClass());
    }

    // 构建拦截器链
    CommandExecutor<R> executor = buildInterceptorChain(handler);
    return executor.execute(command);
  }

  private <R> CommandExecutor<R> buildInterceptorChain(CommandHandler<Command<R>, R> handler) {
    // 最内层：实际的 Handler 调用
    CommandExecutor<R> executor = cmd -> handler.handle((Command<R>) cmd);

    // 从后向前包装拦截器，使得 Order 小的在外层
    for (int i = interceptors.size() - 1; i >= 0; i--) {
      CommandInterceptor interceptor = interceptors.get(i);
      CommandExecutor<R> next = executor;
      executor = cmd -> interceptor.intercept(cmd, next);
    }

    return executor;
  }

  @Override
  public <R> CompletableFuture<R> handleAsync(Command<R> command) {
    if (command == null) {
      throw new IllegalArgumentException("Command must not be null");
    }
    return CompletableFuture.supplyAsync(() -> handle(command), asyncExecutor);
  }

  /// 获取已注册的 Handler 数量（用于测试和监控）。
  ///
  /// @return Handler 数量
  public int getHandlerCount() {
    return handlers.size();
  }

  /// 检查是否存在指定 Command 类型的 Handler。
  ///
  /// @param commandType Command 类型
  /// @return 如果存在返回 true
  public boolean hasHandler(Class<?> commandType) {
    return handlers.containsKey(commandType);
  }
}
