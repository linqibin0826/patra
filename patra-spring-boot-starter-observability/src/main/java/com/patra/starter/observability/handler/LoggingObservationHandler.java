package com.patra.starter.observability.handler;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 日志观测处理器。
///
/// 功能：
///
/// - 记录 Observation 的生命周期事件到日志
/// - 支持可配置的日志级别（DEBUG、INFO、WARN、ERROR）
/// - 用于开发环境调试和生产环境审计
///
/// 使用场景：
///
/// - 开发环境：启用 DEBUG 级别，记录所有观测事件
/// - 生产环境：建议禁用或使用 INFO 级别，减少日志量
///
/// @author Jobs
/// @since 1.0.0
public class LoggingObservationHandler implements ObservationHandler<Observation.Context> {

    private static final Logger log = LoggerFactory.getLogger(LoggingObservationHandler.class);

    private final LogLevel logLevel;

    /// 构造函数。
    ///
    /// @param logLevelStr 日志级别字符串（DEBUG、INFO、WARN、ERROR）
    public LoggingObservationHandler(String logLevelStr) {
        this.logLevel = LogLevel.fromString(logLevelStr);
        log.info("初始化日志观测处理器，日志级别: {}", this.logLevel);
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
        logLevel.log(log, "Observation started: name={}, contextualName={}",
            context.getName(),
            context.getContextualName() != null ? context.getContextualName() : "N/A");
    }

    /// Observation 停止时的处理。
    ///
    /// @param context Observation 上下文
    @Override
    public void onStop(Observation.Context context) {
        logLevel.log(log, "Observation completed: name={}, lowCardinalityKeys={}, highCardinalityKeys={}",
            context.getName(),
            context.getLowCardinalityKeyValues().stream().count(),
            context.getHighCardinalityKeyValues().stream().count());
    }

    /// Observation 发生错误时的处理。
    ///
    /// @param context Observation 上下文
    @Override
    public void onError(Observation.Context context) {
        Throwable error = context.getError();
        if (error != null) {
            log.error("Observation failed: name={}, error={}",
                context.getName(),
                error.getMessage(),
                error);
        } else {
            log.error("Observation failed: name={}, error=Unknown", context.getName());
        }
    }

    /// 日志级别枚举。
    private enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR;

        /// 从字符串解析日志级别。
        ///
        /// @param level 日志级别字符串
        /// @return LogLevel 枚举
        static LogLevel fromString(String level) {
            if (level == null || level.isEmpty()) {
                return DEBUG;
            }
            try {
                return LogLevel.valueOf(level.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("无效的日志级别: {}，使用默认值 DEBUG", level);
                return DEBUG;
            }
        }

        /// 根据日志级别记录日志。
        ///
        /// @param logger 日志记录器
        /// @param message 日志消息
        /// @param args 日志参数
        void log(Logger logger, String message, Object... args) {
            switch (this) {
                case DEBUG:
                    if (logger.isDebugEnabled()) {
                        logger.debug(message, args);
                    }
                    break;
                case INFO:
                    if (logger.isInfoEnabled()) {
                        logger.info(message, args);
                    }
                    break;
                case WARN:
                    if (logger.isWarnEnabled()) {
                        logger.warn(message, args);
                    }
                    break;
                case ERROR:
                    if (logger.isErrorEnabled()) {
                        logger.error(message, args);
                    }
                    break;
            }
        }
    }
}
