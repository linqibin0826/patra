package com.patra.starter.redisson.lock;

import com.patra.starter.redisson.exception.LockAcquisitionException;
import com.patra.starter.redisson.exception.LockInfrastructureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁执行器
 * <p>
 * 负责执行锁的获取、释放和业务逻辑调用。
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LockExecutor {

    private final RedissonClient redissonClient;

    /**
     * 执行带锁的业务逻辑
     *
     * @param context        锁上下文
     * @param businessLogic  业务逻辑（Supplier）
     * @param <T>            返回值类型
     * @return 业务逻辑执行结果
     * @throws LockAcquisitionException   锁获取失败时抛出
     * @throws LockInfrastructureException Redis 基础设施错误时抛出
     */
    public <T> T execute(LockContext context, Supplier<T> businessLogic) {
        // 获取锁
        RLock lock = getLock(context);

        // 记录锁获取开始时间
        context.markAcquireStart(System.currentTimeMillis());

        try {
            // 尝试获取锁
            boolean acquired = tryLock(lock, context);

            if (!acquired) {
                log.warn("无法获取分布式锁: {} (等待时间: {} ms)", context.getLockKey(), context.getWaitTime());
                throw new LockAcquisitionException(context.getLockKey(), context.getWaitTime());
            }

            // 记录锁获取成功时间
            context.markAcquired(System.currentTimeMillis());
            log.debug("成功获取分布式锁: {} (等待时间: {} ms)", context.getLockKey(), context.getActualWaitTime());

            // 执行业务逻辑
            return businessLogic.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("锁获取过程被中断: {}", context.getLockKey(), e);
            throw new LockInfrastructureException("锁获取过程被中断", context.getLockKey(), e);

        } catch (Exception e) {
            log.error("Redis 基础设施错误: {}", context.getLockKey(), e);
            throw new LockInfrastructureException(context.getLockKey(), e);

        } finally {
            // 释放锁
            releaseLock(lock, context);
        }
    }

    /**
     * 根据锁类型获取对应的 RLock 实例
     *
     * @param context 锁上下文
     * @return RLock 实例
     */
    private RLock getLock(LockContext context) {
        return switch (context.getLockType()) {
            case REENTRANT -> redissonClient.getLock(context.getLockKey());
            case FAIR -> redissonClient.getFairLock(context.getLockKey());
            case READ -> {
                RReadWriteLock rwLock = redissonClient.getReadWriteLock(context.getLockKey());
                yield rwLock.readLock();
            }
            case WRITE -> {
                RReadWriteLock rwLock = redissonClient.getReadWriteLock(context.getLockKey());
                yield rwLock.writeLock();
            }
        };
    }

    /**
     * 尝试获取锁
     *
     * @param lock    RLock 实例
     * @param context 锁上下文
     * @return true 如果成功获取锁
     * @throws InterruptedException 锁获取过程被中断时抛出
     */
    private boolean tryLock(RLock lock, LockContext context) throws InterruptedException {
        if (context.isWatchdogEnabled()) {
            // 启用看门狗机制（leaseTime = -1）
            log.debug("尝试获取锁（看门狗模式）: {}", context.getLockKey());
            return lock.tryLock(context.getWaitTime(), TimeUnit.MILLISECONDS);
        } else {
            // 手动设置 leaseTime（关闭看门狗）
            log.debug("尝试获取锁（手动 leaseTime: {} ms）: {}", context.getLeaseTime(), context.getLockKey());
            return lock.tryLock(
                context.getWaitTime(),
                context.getLeaseTime(),
                TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * 释放锁
     *
     * @param lock    RLock 实例
     * @param context 锁上下文
     */
    private void releaseLock(RLock lock, LockContext context) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("成功释放分布式锁: {}", context.getLockKey());
            } else {
                log.warn("锁不由当前线程持有，跳过释放: {}", context.getLockKey());
            }
        } catch (Exception e) {
            log.error("释放锁时发生错误: {}", context.getLockKey(), e);
            // 不抛出异常，避免覆盖业务逻辑异常
        }
    }
}
