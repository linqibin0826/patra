package com.patra.starter.observability.handler;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/// 性能观测处理器。
///
/// 功能：
///
/// - 记录 Observation 的执行时间
/// - 检测慢操作并记录警告日志
/// - 收集性能统计信息
///
/// @author Jobs
/// @since 1.0.0
public class PerformanceObservationHandler implements ObservationHandler<Observation.Context> {

    private static final Logger log = LoggerFactory.getLogger(PerformanceObservationHandler.class);

    /// 使用 Caffeine Cache 存储开始时间，自动过期防止内存泄漏。
    ///
    /// 配置：
    /// - expireAfterWrite(5分钟)：5 分钟后自动清理未完成的 Observation
    /// - maximumSize(10000)：限制最大缓存条目数，防止内存爆炸
    /// - recordStats()：记录缓存统计信息（命中率、驱逐数等）
    private final Cache<Integer, Long> startTimes;
    private final Duration slowThreshold;

    /// 构造函数。
    ///
    /// @param slowThreshold 慢操作阈值
    public PerformanceObservationHandler(Duration slowThreshold) {
        this.slowThreshold = slowThreshold;
        this.startTimes = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))  // 5 分钟后自动过期
            .maximumSize(10_000)                     // 限制最大条目数
            .recordStats()                            // 记录统计信息
            .build();
        log.info("初始化性能观测处理器，慢操作阈值: {}ms", slowThreshold.toMillis());
    }

    /// 判断是否支持该 Context。
    ///
    /// @param context Observation 上下文
    /// @return true 表示支持所有 Context
    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    /// Observation 启动时的处理。
    ///
    /// @param context Observation 上下文
    @Override
    public void onStart(Observation.Context context) {
        int key = getKey(context);
        startTimes.put(key, System.currentTimeMillis());
        log.trace("观测开始: {}", context.getName());
    }

    /// Observation 停止时的处理。
    ///
    /// @param context Observation 上下文
    @Override
    public void onStop(Observation.Context context) {
        int key = getKey(context);
        Long startTime = startTimes.getIfPresent(key);
        if (startTime != null) {
            startTimes.invalidate(key);  // 使用 invalidate 而非 remove
        }

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;

            if (duration > slowThreshold.toMillis()) {
                log.warn("慢操作检测: {} 耗时 {}ms，超过阈值 {}ms",
                    context.getName(), duration, slowThreshold.toMillis());
            } else {
                log.debug("观测完成: {} 耗时 {}ms", context.getName(), duration);
            }
        }
    }

    /// Observation 发生错误时的处理。
    ///
    /// @param context Observation 上下文
    @Override
    public void onError(Observation.Context context) {
        // 清理 startTimes，防止内存泄漏
        int key = getKey(context);
        Long startTime = startTimes.getIfPresent(key);
        if (startTime != null) {
            startTimes.invalidate(key);
        }

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("观测错误: {}, 耗时 {}ms, 错误: {}",
                context.getName(),
                duration,
                context.getError() != null ? context.getError().getMessage() : "Unknown");
        } else {
            log.error("观测错误: {}, 错误: {}",
                context.getName(),
                context.getError() != null ? context.getError().getMessage() : "Unknown");
        }
    }

    /// 生成唯一 Key。
    ///
    /// 使用 Observation Context 的 identityHashCode，确保每个 Observation 实例的唯一性。
    /// 相比 "名称 + 线程 ID" 的方式，identityHashCode 可以正确处理：
    /// - 线程池复用场景（同一线程执行多个同名 Observation）
    /// - 并发场景（多个线程同时执行同名 Observation）
    ///
    /// @param context Observation 上下文
    /// @return 唯一 Key
    private int getKey(Observation.Context context) {
        return System.identityHashCode(context);
    }

    /// 获取活跃 Observation 数量（用于监控）。
    ///
    /// @return 当前缓存中的 Observation 数量
    public long getActiveObservationCount() {
        return startTimes.estimatedSize();
    }

    /// 获取缓存统计信息（用于监控）。
    ///
    /// @return 缓存统计信息（命中率、驱逐数等）
    public com.github.benmanes.caffeine.cache.stats.CacheStats getCacheStats() {
        return startTimes.stats();
    }
}
