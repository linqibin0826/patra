package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 指标类型枚举
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum MetricType implements CodeEnum<String> {
    
    CITED_BY_CROSSREF("cited_by_crossref", "Crossref引用"),
    CITED_BY_OPENCITATIONS("cited_by_opencitations", "OpenCitations引用"),
    CITED_BY_SCOPUS("cited_by_scopus", "Scopus引用"),
    PMC_VIEWS("pmc_views", "PMC浏览量"),
    PMC_DOWNLOADS("pmc_downloads", "PMC下载量"),
    MENDELEY_READERS("mendeley_readers", "Mendeley读者"),
    TWITTER("twitter", "Twitter提及"),
    NEWS("news", "新闻提及"),
    POLICY_MENTIONS("policy_mentions", "政策提及"),
    BLOGS("blogs", "博客提及");
    
    private final String code;
    @Getter
    private final String description;
    
    MetricType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @Override
    public String getCode() {
        return code;
    }
}
