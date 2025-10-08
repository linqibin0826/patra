package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Ingest 配置异常。
 *
 * <p>触发条件：从 Registry / 配置中心加载来源(Provenance) 或 操作(Operation) 元数据时缺失、格式不符合约束或引用未解。
 * 与 {@link PlanValidationException} 区别：本异常侧重 <strong>平台配置层</strong> 缺陷，而非运行期参数。</p>
 * <p>处理建议：
 * <ul>
 *   <li>如果是暂时性拉取失败（网络/超时），应用层可限次重试。</li>
 *   <li>配置缺失：记录 ERROR 并触发告警，提示补齐配置。</li>
 *   <li>配置格式不合规：阻断执行链，返回明确字段路径与期望格式。</li>
 * </ul>
 * </p>
 * <p>可观测性：建议在日志中附带 provenanceCode/operationCode 以支持快速聚合统计。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class IngestConfigurationException extends IngestException implements HasErrorTraits {

    /** 来源代码（上游数据来源标识）。 */
    private final String provenanceCode;
    /** 操作代码（业务操作/任务类型）。 */
    private final String operationCode;

    /**
     * 构造配置异常（无底层异常包装）。
     * <p>适用于直接检测到缺失或不合规立即抛出。</p>
     *
     * @param provenanceCode 来源代码
     * @param operationCode  操作代码
     * @param message        异常消息（应包含缺失字段/校验路径）
     */
    public IngestConfigurationException(String provenanceCode, String operationCode, String message) {
        super(message);
        this.provenanceCode = provenanceCode;
        this.operationCode = operationCode;
    }

    /**
     * 构造配置异常（带底层异常）。
     * <p>用于包装：远程调用失败 / JSON 解析错误 / 映射转换异常等。</p>
     *
     * @param provenanceCode 来源代码
     * @param operationCode  操作代码
     * @param message        异常消息
     * @param cause          底层原因
     */
    public IngestConfigurationException(String provenanceCode, String operationCode, String message, Throwable cause) {
        super(message, cause);
        this.provenanceCode = provenanceCode;
        this.operationCode = operationCode;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.RULE_VIOLATION);
    }

    /**
     * 获取来源代码。
     * @return 来源代码
     */
    public String getProvenanceCode() {
        return provenanceCode;
    }

    /**
     * 获取操作代码。
     * @return 操作代码
     */
    public String getOperationCode() {
        return operationCode;
    }
}