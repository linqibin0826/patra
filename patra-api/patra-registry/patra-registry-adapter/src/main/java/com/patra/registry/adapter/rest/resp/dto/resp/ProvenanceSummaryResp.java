package com.patra.registry.adapter.rest.resp.dto.resp;

public record ProvenanceSummaryResp(
        String name,
        String code // 对外用字符串/基础类型，避免泄露领域枚举
) {
}
