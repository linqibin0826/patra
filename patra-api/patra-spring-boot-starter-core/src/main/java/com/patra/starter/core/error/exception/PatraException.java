package com.patra.starter.core.error.exception;

import com.patra.common.error.core.ErrorCode;
import com.patra.common.error.core.ErrorDef;
import com.patra.common.error.core.PlatformError;
import com.patra.starter.core.error.runtime.PlatformErrorFactory;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

/**
 * 平台自定义异常：承载 PlatformError（与 RFC7807 对齐）。
 * 使用方式：
 * throw new PatraException(REGErrors.MISSING_PROVENANCE_ID, Map.of("param","provenanceId"));
 * 或者：
 * throw new PatraException(REGErrors.REGISTRY_NOT_FOUND).withDetail("id=123 not found");
 * <p>
 * Starter-Web 的 @ControllerAdvice 捕获后：
 * -> 反序列化为 Problem JSON 输出（含 code/status/title/...）
 */
public class PatraException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final PlatformError error;

    /* ===================== 构造器（核心载体是 PlatformError） ===================== */

    /**
     * 直接用已构造好的 PlatformError 包装（最底层构造）
     */
    public PatraException(PlatformError error) {
        super(buildMessage(Objects.requireNonNull(error, "error must not be null")));
        this.error = error;
    }

    /**
     * 直接用 PlatformError + cause 包装
     */
    public PatraException(PlatformError error, Throwable cause) {
        super(buildMessage(Objects.requireNonNull(error, "error must not be null")), cause);
        this.error = error;
    }

    /**
     * 用强类型错误定义（枚举）构造，自动从 codebook 补全 title/http
     */
    public PatraException(ErrorDef def) {
        this(PlatformErrorFactory.of(Objects.requireNonNull(def, "def")).build());
    }

    /**
     * 用强类型错误定义 + 详情文本
     */
    public PatraException(ErrorDef def, String detail) {
        this(PlatformErrorFactory.of(Objects.requireNonNull(def, "def")).detail(detail).build());
    }

    /**
     * 用强类型错误定义 + 扩展上下文（extras）
     */
    public PatraException(ErrorDef def, Map<String, ?> extras) {
        this(PlatformErrorFactory.of(Objects.requireNonNull(def, "def")).params(extras).build());
    }

    /**
     * 用强类型错误定义 + 详情 + cause
     */
    public PatraException(ErrorDef def, String detail, Throwable cause) {
        this(PlatformErrorFactory.of(Objects.requireNonNull(def, "def")).detail(detail).build(), cause);
    }

    /**
     * 用强类型错误定义 + 详情 + extras + cause
     */
    public PatraException(ErrorDef def, String detail, Map<String, ?> extras, Throwable cause) {
        this(PlatformErrorFactory.of(Objects.requireNonNull(def, "def")).detail(detail).params(extras).build(), cause);
    }

    /**
     * 用 ErrorCode 构造（不建议业务侧直接用，保留给过渡/测试）
     */
    public PatraException(ErrorCode code) {
        this(PlatformErrorFactory.of(Objects.requireNonNull(code, "code")).build());
    }

    /**
     * 用 ErrorCode + 详情 + extras
     */
    public PatraException(ErrorCode code, String detail, Map<String, ?> extras) {
        this(PlatformErrorFactory.of(Objects.requireNonNull(code, "code")).detail(detail).params(extras).build());
    }

    /* ===================== 读取器 & 便捷修改 ===================== */

    public PlatformError error() {
        return error;
    }

    /**
     * 平台错误码（强类型）
     */
    public ErrorCode code() {
        return error.code();
    }

    /**
     * 建议的 HTTP 状态码（0 表示未设置；Starter-Web 输出时应兜底）
     */
    public int status() {
        return error.status();
    }

    public String title() {
        return error.title();
    }

    public String traceId() {
        return error.traceId();
    }

    public String service() {
        return error.service();
    }

    /**
     * 基于当前异常复制一个“仅修改 detail”后的新异常（不改变栈轨迹和 cause）。
     * 便于在抓到后补充更多上下文说明。
     */
    public PatraException withDetail(String newDetail) {
        PlatformError updated = error.withDetail(newDetail);
        return (getCause() == null) ? new PatraException(updated) : new PatraException(updated, getCause());
    }

    /**
     * 基于当前异常合并更多 extras，返回新异常
     */
    public PatraException withExtras(Map<String, ?> extras) {
        PlatformError updated = error.withExtras(extras);
        return (getCause() == null) ? new PatraException(updated) : new PatraException(updated, getCause());
    }

    /* ===================== 内部：生成异常消息 ===================== */

    private static String buildMessage(PlatformError e) {
        // 尽量在日志/控制台中可快速识别：CODE [status] title
        String code = e.code() == null ? "UNKNOWN" : e.code().toString();
        String title = (e.title() == null || e.title().isBlank()) ? "" : e.title();
        String status = e.status() > 0 ? String.valueOf(e.status()) : "-";
        return "%s [%s]%s".formatted(code, status, title.isEmpty() ? "" : " " + title);
    }
}
