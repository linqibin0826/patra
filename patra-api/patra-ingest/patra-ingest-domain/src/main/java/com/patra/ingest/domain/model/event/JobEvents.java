package com.patra.ingest.domain.model.event;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 采集任务领域事件
 *
 * @author linqibin
 * @since 0.1.0
 */
public class JobEvents {
    
    /**
     * 任务已调度事件
     */
    @Data
    public static class JobScheduled {
        private final Long jobId;
        private final String jobKey;
        private final LocalDateTime scheduledAt;
        private final LocalDateTime occurredAt;
        
        public JobScheduled(Long jobId, String jobKey, LocalDateTime scheduledAt) {
            this.jobId = jobId;
            this.jobKey = jobKey;
            this.scheduledAt = scheduledAt;
            this.occurredAt = LocalDateTime.now();
        }
    }
    
    /**
     * 任务已开始事件
     */
    @Data
    public static class JobStarted {
        private final Long jobId;
        private final String jobKey;
        private final LocalDateTime startedAt;
        private final LocalDateTime occurredAt;
        
        public JobStarted(Long jobId, String jobKey, LocalDateTime startedAt) {
            this.jobId = jobId;
            this.jobKey = jobKey;
            this.startedAt = startedAt;
            this.occurredAt = LocalDateTime.now();
        }
    }
    
    /**
     * 任务已成功事件
     */
    @Data
    public static class JobSucceeded {
        private final Long jobId;
        private final String jobKey;
        private final LocalDateTime finishedAt;
        private final LocalDateTime occurredAt;
        
        public JobSucceeded(Long jobId, String jobKey, LocalDateTime finishedAt) {
            this.jobId = jobId;
            this.jobKey = jobKey;
            this.finishedAt = finishedAt;
            this.occurredAt = LocalDateTime.now();
        }
    }
    
    /**
     * 任务已失败事件
     */
    @Data
    public static class JobFailed {
        private final Long jobId;
        private final String jobKey;
        private final String error;
        private final LocalDateTime finishedAt;
        private final LocalDateTime occurredAt;
        
        public JobFailed(Long jobId, String jobKey, String error, LocalDateTime finishedAt) {
            this.jobId = jobId;
            this.jobKey = jobKey;
            this.error = error;
            this.finishedAt = finishedAt;
            this.occurredAt = LocalDateTime.now();
        }
    }
    
    /**
     * 任务已取消事件
     */
    @Data
    public static class JobCancelled {
        private final Long jobId;
        private final String jobKey;
        private final LocalDateTime occurredAt;
        
        public JobCancelled(Long jobId, String jobKey) {
            this.jobId = jobId;
            this.jobKey = jobKey;
            this.occurredAt = LocalDateTime.now();
        }
    }
}
