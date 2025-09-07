package com.patra.ingest.api.error;

/**
 * 数据采集错误码
 *
 * @author linqibin
 * @since 0.1.0
 */
public class IngestErrorCodes {
    
    /**
     * 通用错误码前缀
     */
    public static final String PREFIX = "INGEST";
    
    // 运行相关错误码 (1000-1999)
    public static final String RUN_NOT_FOUND = PREFIX + "_1001";
    public static final String RUN_ALREADY_EXISTS = PREFIX + "_1002";
    public static final String RUN_INVALID_STATUS = PREFIX + "_1003";
    public static final String RUN_VERSION_CONFLICT = PREFIX + "_1004";
    public static final String RUN_CANNOT_UPDATE = PREFIX + "_1005";
    
    // 游标相关错误码 (2000-2999)
    public static final String CURSOR_NOT_FOUND = PREFIX + "_2001";
    public static final String CURSOR_ALREADY_EXISTS = PREFIX + "_2002";
    public static final String CURSOR_INVALID_TYPE = PREFIX + "_2003";
    public static final String CURSOR_VERSION_CONFLICT = PREFIX + "_2004";
    public static final String CURSOR_ALREADY_FINISHED = PREFIX + "_2005";
    
    // 源命中相关错误码 (3000-3999)
    public static final String SOURCE_HIT_NOT_FOUND = PREFIX + "_3001";
    public static final String SOURCE_HIT_ALREADY_EXISTS = PREFIX + "_3002";
    public static final String SOURCE_HIT_INVALID_TYPE = PREFIX + "_3003";
    public static final String SOURCE_HIT_VERSION_CONFLICT = PREFIX + "_3004";
    public static final String SOURCE_HIT_ALREADY_PROCESSED = PREFIX + "_3005";
    
    // 参数验证错误码 (4000-4999)
    public static final String INVALID_PARAMETER = PREFIX + "_4001";
    public static final String MISSING_REQUIRED_PARAMETER = PREFIX + "_4002";
    public static final String PARAMETER_FORMAT_ERROR = PREFIX + "_4003";
    public static final String PARAMETER_RANGE_ERROR = PREFIX + "_4004";
    
    // 业务规则错误码 (5000-5999)
    public static final String BUSINESS_RULE_VIOLATION = PREFIX + "_5001";
    public static final String TIME_WINDOW_INVALID = PREFIX + "_5002";
    public static final String JOB_NOT_ACTIVE = PREFIX + "_5003";
    public static final String CONCURRENT_OPERATION_CONFLICT = PREFIX + "_5004";
    
    // 系统错误码 (9000-9999)
    public static final String INTERNAL_ERROR = PREFIX + "_9001";
    public static final String DATABASE_ERROR = PREFIX + "_9002";
    public static final String NETWORK_ERROR = PREFIX + "_9003";
    public static final String TIMEOUT_ERROR = PREFIX + "_9004";
    
    private IngestErrorCodes() {
        // 工具类，禁止实例化
    }
}
