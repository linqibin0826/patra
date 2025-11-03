package com.patra.registry.api.dto.provenance;

/**
 * 核心数据源元数据响应 DTO,暴露给下游服务。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>id - 数据源行的内部标识符
 *   <li>code - 唯一标识数据源的稳定业务代码
 *   <li>name - 人类可读的数据源名称
 *   <li>baseUrlDefault - 当不存在端点覆盖时使用的默认基础 URL
 *   <li>timezoneDefault - 解释调度时应用的默认时区
 *   <li>docsUrl - 数据源的文档或参考 URL
 *   <li>active - 数据源当前是否激活
 *   <li>lifecycleStatusCode - 生命周期状态标识符(PLANNING/ACTIVE 等)
 * </ol>
 *
 * @param id 内部标识符
 * @param code 业务代码
 * @param name 数据源名称
 * @param baseUrlDefault 默认基础 URL
 * @param timezoneDefault 默认时区
 * @param docsUrl 文档 URL
 * @param active 是否激活
 * @param lifecycleStatusCode 生命周期状态代码
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceResp(
    Long id,
    String code,
    String name,
    String baseUrlDefault,
    String timezoneDefault,
    String docsUrl,
    boolean active,
    String lifecycleStatusCode) {}
