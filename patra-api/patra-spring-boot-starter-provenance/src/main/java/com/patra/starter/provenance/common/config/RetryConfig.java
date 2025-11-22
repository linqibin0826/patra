package com.patra.starter.provenance.common.config;

/// 重试策略配置记录
/// 
/// 定义访问 Provenance 数据源时的重试行为,包括最大重试次数和初始退避延迟。 该配置会被委托给底层网关层进行实际的重试控制。
/// 
/// @param maxRetryTimes 委托给网关的最大重试次数
/// @param initialDelayMillis 初始退避延迟时间(毫秒)
/// @author linqibin
/// @since 0.1.0
public record RetryConfig(Integer maxRetryTimes, Integer initialDelayMillis) {}
