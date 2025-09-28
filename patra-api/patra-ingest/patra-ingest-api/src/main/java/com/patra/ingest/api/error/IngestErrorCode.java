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

    /** 错误请求（HTTP 400）。 */
    ING_0400("ING-0400"),
    /** 未认证（HTTP 401）。 */
    ING_0401("ING-0401"),
    /** 禁止访问（HTTP 403）。 */
    ING_0403("ING-0403"),
    /** 资源未找到（HTTP 404）。 */
    ING_0404("ING-0404"),
    /** 请求冲突（HTTP 409）。 */
    ING_0409("ING-0409"),
    /** 语义错误（HTTP 422）。 */
    ING_0422("ING-0422"),
    /** 请求过多（HTTP 429）。 */
    ING_0429("ING-0429"),
    /** 服务器内部错误（HTTP 500）。 */
    ING_0500("ING-0500"),
    /** 服务不可用（HTTP 503）。 */
    ING_0503("ING-0503"),
    /** 网关超时（HTTP 504）。 */
    ING_0504("ING-0504"),

    /** Registry 配置未注册或缺失。 */
    ING_1201("ING-1201"),
    /** Registry 返回非法配置数据。 */
    ING_1202("ING-1202"),
    /** Registry 服务不可用导致配置降级。 */
    ING_1203("ING-1203"),
    /** Outbox 写入失败。 */
    ING_1301("ING-1301"),
    /** Outbox 状态更新失败。 */
    ING_1302("ING-1302"),
    /** Outbox dead-letter 标记失败。 */
    ING_1303("ING-1303"),
    /** 调度任务参数解析失败。 */
    ING_1401("ING-1401"),
    /** 调度任务执行失败。 */
    ING_1402("ING-1402"),
    /** 计划装配前置验证失败。 */
    ING_1403("ING-1403"),
    /** 任务运行快照持久化失败。 */
    ING_1501("ING-1501"),
    /** 任务恢复时检查点解析失败。 */
    ING_1502("ING-1502"),
    /** 计划及任务持久化失败。 */
    ING_1503("ING-1503"),
    /** 计划装配失败（未生成切片/任务等）。 */
    ING_1601("ING-1601");

    private final String code;

    IngestErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
}
