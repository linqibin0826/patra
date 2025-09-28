package com.patra.ingest.api.error;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Ingest 模块平台错误码目录。
 *
 * <p>统一采用 ING-NNNN 形式（ING 为上下文前缀），对齐平台错误规范。
 * 0 段用于 HTTP 对齐错误，1 段用于领域业务错误。</p>
 *
 * @author linqibin
 * @since 0.2.0
 */
public enum IngestErrorCode implements ErrorCodeLike {

    // 注意：0xxx（HTTP 对齐段）请统一使用 HttpStdErrors.of("ING").* 工厂方法，不在本枚举维护。

    /** Registry 配置未注册或缺失。 */
    ING_1201("ING-1201", 404),
    /** Registry 返回非法配置数据。 */
    ING_1202("ING-1202", 422),
    /** Registry 服务不可用导致配置降级。 */
    ING_1203("ING-1203", 503),
    /** Outbox 写入失败。 */
    ING_1301("ING-1301", 500),
    /** Outbox 状态更新失败。 */
    ING_1302("ING-1302", 500),
    /** Outbox dead-letter 标记失败。 */
    ING_1303("ING-1303", 500),
    /** 调度任务参数解析失败。 */
    ING_1401("ING-1401", 422),
    /** 调度任务执行失败。 */
    ING_1402("ING-1402", 500),
    /** 计划装配前置验证失败。 */
    ING_1403("ING-1403", 422),
    /** 检查点解析失败。 */
    ING_1501("ING-1501", 422),
    /** 检查点序列化失败。 */
    ING_1502("ING-1502", 422),
    /** 计划及任务持久化失败。 */
    ING_1503("ING-1503", 500),
    /** 计划装配失败（未生成切片/任务等）。 */
    ING_1601("ING-1601", 500);

    private final String code;
    private final int httpStatus;

    IngestErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String toString() {
        return code;
    }
}
