package dev.linqibin.patra.catalog.adapter.rest.venue.request;

/// 刊级 Top N 高被引查询请求。
///
/// 参数归一化与边界钳位由
/// {@link dev.linqibin.patra.catalog.app.usecase.publication.query.dto.TopPublicationsQuery#of} 承担
/// （null→5，超出 [1,20] 自动钳位）；本类仅承载原始 query 参数，与项目其他 Request DTO 一致。
///
/// @param limit 返回条数（可空，缺省 5；超出 [1,20] 自动钳位）
/// @param since 发表年下限（可为 null，不过滤）
public record TopPublicationsRequest(Integer limit, Integer since) {}
