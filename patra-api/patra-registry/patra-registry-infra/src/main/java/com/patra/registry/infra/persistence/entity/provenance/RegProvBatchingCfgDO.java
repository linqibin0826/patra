package com.patra.registry.infra.persistence.entity.provenance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * 数据表 {@code reg_prov_batching_cfg} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_batching_cfg")
public class RegProvBatchingCfgDO extends BaseDO {

    @TableField("provenance_id")
    private Long provenanceId;

    @TableField("scope_code")
    private String scopeCode;

    @TableField("task_type")
    private String taskType;

    @TableField("effective_from")
    private Instant effectiveFrom;

    @TableField("effective_to")
    private Instant effectiveTo;

    @TableField("detail_fetch_batch_size")
    private Integer detailFetchBatchSize;

    @TableField("endpoint_id")
    private Long endpointId;

    @TableField("credential_name")
    private String credentialName;

    @TableField("ids_param_name")
    private String idsParamName;

    @TableField("ids_join_delimiter")
    private String idsJoinDelimiter;

    @TableField("max_ids_per_request")
    private Integer maxIdsPerRequest;

    @TableField("prefer_compact_payload")
    private Boolean preferCompactPayload;

    @TableField("payload_compress_strategy_code")
    private String payloadCompressStrategyCode;

    @TableField("app_parallelism_degree")
    private Integer appParallelismDegree;

    @TableField("per_host_concurrency_limit")
    private Integer perHostConcurrencyLimit;

    @TableField("http_conn_pool_size")
    private Integer httpConnPoolSize;

    @TableField("backpressure_strategy_code")
    private String backpressureStrategyCode;

    @TableField("request_template_json")
    private String requestTemplateJson;

    @TableField("task_type_key")
    private String taskTypeKey;

    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
