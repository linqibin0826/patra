package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.Set;

/**
 * 采集配置异常。
 *
 * <p>触发场景:当采集配置无效时抛出,具体包括:
 *
 * <ul>
 *   <li>无法从 patra-registry 或配置中心加载 Provenance/Operation 元数据
 *   <li>配置数据违反 Schema 约束(如必填字段缺失、类型不匹配)
 *   <li>JSON 解析失败或映射转换错误
 * </ul>
 *
 * <p>与 {@link PlanValidationException} 的区别:本异常关注平台配置缺陷(如元数据缺失或格式错误),而 {@link
 * PlanValidationException} 关注运行时参数验证(如窗口参数错误)。
 *
 * <p>处理建议:
 *
 * <ul>
 *   <li><b>临时性获取失败(网络/超时)</b>:在应用层限次重试,避免瞬时故障导致任务失败。
 *   <li><b>配置缺失</b>:记录 ERROR 级别日志并触发告警,通知运维团队补充配置数据。
 *   <li><b>配置格式错误</b>:停止执行并在日志中描述错误字段路径和期望格式,便于快速修复。
 * </ul>
 *
 * <p>可观测性提示:日志中应包含 {@code provenanceCode} 和 {@code operationCode},便于按数据源聚合错误统计。
 *
 * @author linqibin
 * @since 0.1.0
 */
public class IngestConfigurationException extends IngestException implements HasErrorTraits {

  /** 标识上游数据源的 Provenance Code。 */
  private final String provenanceCode;

  /** 表示业务操作或任务类型的 Operation Code。 */
  private final String operationCode;

  /**
   * 构造配置异常(无底层原因)。
   *
   * <p>适用场景:立即检测到配置缺陷时使用(如必填字段校验失败)。
   *
   * @param provenanceCode Provenance Code
   * @param operationCode Operation Code
   * @param message 错误消息,应详细说明缺失或无效的字段
   */
  public IngestConfigurationException(String provenanceCode, String operationCode, String message) {
    super(message);
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
  }

  /**
   * 构造配置异常并附带底层原因。
   *
   * <p>适用场景:包装远程调用失败、JSON 解析问题或映射转换错误。
   *
   * @param provenanceCode Provenance Code
   * @param operationCode Operation Code
   * @param message 错误消息
   * @param cause 根本原因
   */
  public IngestConfigurationException(
      String provenanceCode, String operationCode, String message, Throwable cause) {
    super(message, cause);
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
  }

  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return Set.of(ErrorTrait.RULE_VIOLATION);
  }

  /**
   * 获取 Provenance Code。
   *
   * @return Provenance Code
   */
  public String getProvenanceCode() {
    return provenanceCode;
  }

  /**
   * 获取 Operation Code。
   *
   * @return Operation Code
   */
  public String getOperationCode() {
    return operationCode;
  }
}
