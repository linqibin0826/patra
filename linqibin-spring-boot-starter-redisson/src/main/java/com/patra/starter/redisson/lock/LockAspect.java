package com.patra.starter.redisson.lock;

import com.patra.starter.redisson.config.RedissonProperties;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/// 分布式锁 AOP 切面。
///
/// 拦截 {@link DistributedLock} 注解标记的方法，自动获取和释放锁。
///
/// @author Patra Team
/// @since 1.0.0
@Slf4j
@Aspect
@RequiredArgsConstructor
public class LockAspect {

  /// 锁键生成器
  private final LockKeyGenerator lockKeyGenerator;

  /// 锁执行器
  private final LockExecutor lockExecutor;

  /// Redisson 配置属性
  private final RedissonProperties properties;

  /// 环绕通知：拦截带有 @DistributedLock 注解的方法。
  ///
  /// @param joinPoint        连接点
  /// @param distributedLock  注解实例
  /// @return 方法执行结果
  /// @throws Throwable 方法执行过程中的异常
  @Around("@annotation(distributedLock)")
  public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock)
      throws Throwable {
    // 获取方法信息
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    Object[] args = joinPoint.getArgs();

    // 生成锁键
    String lockKey = lockKeyGenerator.generateKey(distributedLock.key(), method, args);

    // 构建锁上下文
    LockContext context = buildLockContext(distributedLock, lockKey, method);

    // 执行带锁的业务逻辑
    return lockExecutor.execute(
        context,
        () -> {
          try {
            return joinPoint.proceed();
          } catch (Throwable e) {
            // 将 Throwable 包装为 RuntimeException
            if (e instanceof RuntimeException runtimeException) {
              throw runtimeException;
            }
            throw new RuntimeException("方法执行失败: " + method.getName(), e);
          }
        });
  }

  /// 构建锁上下文。
  ///
  /// @param annotation 注解实例
  /// @param lockKey    完整的锁键
  /// @param method     目标方法
  /// @return 锁上下文
  private LockContext buildLockContext(DistributedLock annotation, String lockKey, Method method) {
    // 解析 waitTime（-1 表示使用配置文件中的默认值）
    long waitTime =
        annotation.waitTime() == -1
            ? properties.getLock().getDefaultWaitTime()
            : annotation.waitTime();

    // 解析 leaseTime（-1 表示启用看门狗）
    long leaseTime =
        annotation.leaseTime() == -1
            ? properties.getLock().getDefaultLeaseTime()
            : annotation.leaseTime();

    return LockContext.builder()
        .lockKey(lockKey)
        .lockType(annotation.lockType())
        .waitTime(waitTime)
        .leaseTime(leaseTime)
        .methodName(method.getName())
        .className(method.getDeclaringClass().getSimpleName())
        .build();
  }
}
