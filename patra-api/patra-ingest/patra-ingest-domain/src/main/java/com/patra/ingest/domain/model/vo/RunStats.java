package com.patra.ingest.domain.model.vo;

import lombok.Value;

/**
 * 统计信息值对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class RunStats {
    
    /**
     * 获取的记录数
     */
    Integer fetched;
    
    /**
     * 写入的记录数
     */
    Integer upserted;
    
    /**
     * 失败的记录数
     */
    Integer failed;
    
    /**
     * 处理的页数
     */
    Integer pages;
    
    public RunStats(Integer fetched, Integer upserted, Integer failed, Integer pages) {
        this.fetched = fetched != null && fetched >= 0 ? fetched : 0;
        this.upserted = upserted != null && upserted >= 0 ? upserted : 0;
        this.failed = failed != null && failed >= 0 ? failed : 0;
        this.pages = pages != null && pages >= 0 ? pages : 0;
    }
    
    /**
     * 创建空统计
     */
    public static RunStats empty() {
        return new RunStats(0, 0, 0, 0);
    }
    
    /**
     * 合并统计信息
     */
    public RunStats merge(RunStats other) {
        if (other == null) {
            return this;
        }
        return new RunStats(
            this.fetched + other.fetched,
            this.upserted + other.upserted,
            this.failed + other.failed,
            this.pages + other.pages
        );
    }
    
    /**
     * 计算成功率
     */
    public double getSuccessRate() {
        if (fetched == 0) {
            return 0.0;
        }
        return (double) upserted / fetched;
    }
    
    /**
     * 计算失败率
     */
    public double getFailureRate() {
        if (fetched == 0) {
            return 0.0;
        }
        return (double) failed / fetched;
    }
}
