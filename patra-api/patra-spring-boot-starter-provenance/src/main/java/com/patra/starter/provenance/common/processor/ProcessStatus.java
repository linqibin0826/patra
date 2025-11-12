package com.patra.starter.provenance.common.processor;

/**
 * 处理状态枚举
 *
 * <p>定义Processor处理数据时的所有可能状态。
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
public enum ProcessStatus {

    /**
     * 成功 - 所有数据处理成功
     */
    SUCCESS,

    /**
     * 部分成功 - 部分数据处理成功，部分失败
     */
    PARTIAL_SUCCESS,

    /**
     * 失败 - 处理失败（如网络错误、API错误等）
     */
    FAILED,

    /**
     * 验证错误 - 数据验证失败
     */
    VALIDATION_ERROR
}
