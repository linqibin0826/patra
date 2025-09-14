package com.patra.ingest.adapter.in.job;

import lombok.Data;

/**
 * XXL-Job 可传 JSON 参数；若留空，将采用默认“全量条件 + 前一日窗口”策略。
 */
@Data
public class XxlJobPayload {
    /**
     * 业务表达式原型（JSON），可空：为空视作 constTrue() 由 app 侧补齐
     */
    private String exprProtoJson;

    /**
     * 可选：覆盖来源代码；默认就是 PUBMED
     */
    private String provenanceCode;

    /**
     * 可选：operation，默认 UPDATE
     */
    private String operation;
}
