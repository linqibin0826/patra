package com.patra.registry.api.dto.provenance;

/// 核心数据源元数据响应 DTO,暴露给下游服务。
///
/// 字段说明:
///
/// @param id 内部标识符
/// @param code 业务代码
/// @param name 数据源名称
/// @param baseUrlDefault 默认基础 URL
/// @param timezoneDefault 默认时区
/// @param docsUrl 文档 URL
/// @param active 是否激活
/// @param lifecycleStatusCode 生命周期状态代码
/// @author linqibin
/// @since 0.1.0
public record ProvenanceResp(
    Long id,
    String code,
    String name,
    String baseUrlDefault,
    String timezoneDefault,
    String docsUrl,
    boolean active,
    String lifecycleStatusCode) {}
