package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.vo.IdempotentKey;
import com.patra.ingest.domain.model.vo.RunStats;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;

/**
 * 运行批次实体
 * 属于Run聚合的内部实体
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
@With
public class RunBatch {
    
    /**
     * 批次ID
     */
    Long id;
    
    /**
     * 所属运行ID
     */
    Long runId;
    
    /**
     * 批次序号（1起，连续）
     */
    Integer batchNo;
    
    /**
     * 页码（offset/limit分页，从1开始；token分页为空）
     */
    Integer pageNo;
    
    /**
     * 页大小
     */
    Integer pageSize;
    
    /**
     * 本批开始令牌/位置
     */
    String beforeToken;
    
    /**
     * 本批结束令牌/下一位置
     */
    String afterToken;
    
    /**
     * 批次幂等键
     */
    IdempotentKey idempotentKey;
    
    /**
     * 本批处理记录数
     */
    Integer recordCount;
    
    /**
     * 批次状态
     */
    BatchStatus status;
    
    /**
     * 批次提交时间
     */
    LocalDateTime committedAt;
    
    /**
     * 失败原因
     */
    String error;
    
    /**
     * 扩展统计
     */
    RunStats stats;
    
    /**
     * 创建时间
     */
    LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    LocalDateTime updatedAt;
    
    /**
     * 验证批次的业务规则
     */
    public void validate() {
        if (runId == null) {
            throw new IllegalArgumentException("运行ID不能为空");
        }
        if (batchNo == null || batchNo < 1) {
            throw new IllegalArgumentException("批次序号必须从1开始");
        }
        if (idempotentKey == null) {
            throw new IllegalArgumentException("幂等键不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("批次状态不能为空");
        }
        if (recordCount != null && recordCount < 0) {
            throw new IllegalArgumentException("记录数不能为负数");
        }
        
        // 验证分页参数
        if (pageNo != null) {
            if (pageNo < 1) {
                throw new IllegalArgumentException("页码必须从1开始");
            }
            if (pageSize == null || pageSize < 1) {
                throw new IllegalArgumentException("页大小必须大于0");
            }
        }
    }
    
    /**
     * 开始处理批次
     */
    public RunBatch start() {
        if (status != BatchStatus.RUNNING) {
            // 如果不是运行中状态，设置为运行中
            return this.withStatus(BatchStatus.RUNNING);
        }
        return this;
    }
    
    /**
     * 成功完成批次
     */
    public RunBatch succeed(Integer recordCount, RunStats stats) {
        if (status != BatchStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的批次才能标记为成功");
        }
        if (recordCount == null || recordCount < 0) {
            throw new IllegalArgumentException("记录数必须非负");
        }
        
        return this.withStatus(BatchStatus.SUCCEEDED)
                   .withRecordCount(recordCount)
                   .withStats(stats != null ? stats : RunStats.empty())
                   .withCommittedAt(LocalDateTime.now());
    }
    
    /**
     * 批次处理失败
     */
    public RunBatch fail(String error) {
        if (status != BatchStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的批次才能标记为失败");
        }
        if (StrUtil.isBlank(error)) {
            throw new IllegalArgumentException("失败原因不能为空");
        }
        
        return this.withStatus(BatchStatus.FAILED)
                   .withError(error)
                   .withCommittedAt(LocalDateTime.now());
    }
    
    /**
     * 跳过批次
     */
    public RunBatch skip(String reason) {
        return this.withStatus(BatchStatus.SKIPPED)
                   .withError(reason)
                   .withCommittedAt(LocalDateTime.now());
    }
    
    /**
     * 判断批次是否已完成
     */
    public boolean isFinished() {
        return status == BatchStatus.SUCCEEDED || 
               status == BatchStatus.FAILED || 
               status == BatchStatus.SKIPPED;
    }
    
    /**
     * 判断批次是否成功
     */
    public boolean isSuccessful() {
        return status == BatchStatus.SUCCEEDED;
    }
    
    /**
     * 判断批次是否使用页码分页
     */
    public boolean isPageBased() {
        return pageNo != null && pageSize != null;
    }
    
    /**
     * 判断批次是否使用令牌分页
     */
    public boolean isTokenBased() {
        return !StrUtil.isBlank(beforeToken) || !StrUtil.isBlank(afterToken);
    }
}
