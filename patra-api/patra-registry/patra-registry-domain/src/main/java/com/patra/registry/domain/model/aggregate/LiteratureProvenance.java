package com.patra.registry.domain.model.aggregate;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.vo.ApiParamMapping;
import com.patra.registry.domain.model.vo.QueryRenderRule;
import com.patra.registry.domain.model.vo.RecordRemark;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 文献数据源聚合根
 */
@Value
@Builder
public class LiteratureProvenance {

    /**
     * 聚合根ID（技术键）
     */
    Long id;

    /**
     * 数据源名称;pubmed/epmc/openalex/crossref
     */
    String name;

    /**
     * 数据源代码;简短标识符（业务键）
     */
    ProvenanceCode code;

    /**
     * 记录备注
     */
    List<RecordRemark> recordRemarks;

    /**
     * 乐观锁版本号
     */
    Long version;

    /**
     * 数据源配置（1:1关系）
     */
    LiteratureProvenanceConfig config;

    /**
     * 查询能力（1:1关系）
     */
    QueryCapability queryCapability;

    /**
     * API参数映射集合（1:N关系，值对象集合）
     */
    List<ApiParamMapping> apiParamMappings;

    /**
     * 查询渲染规则集合（1:N关系，值对象集合）
     */
    List<QueryRenderRule> queryRenderRules;

    // TODO: 实现聚合业务逻辑：状态流转、配置验证、规则管理等
}
