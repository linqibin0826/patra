package com.patra.common.error.core;

/**
 * 强类型错误定义：枚举项实现它即可。
 */
public interface ErrorDef {
    ErrorCode code();          // 平台错误码
}
