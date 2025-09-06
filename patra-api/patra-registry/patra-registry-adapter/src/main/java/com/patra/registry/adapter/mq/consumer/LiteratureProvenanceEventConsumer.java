package com.patra.registry.adapter.mq.consumer;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 文献数据源事件消费者
 */
@Component
@RequiredArgsConstructor
public class LiteratureProvenanceEventConsumer {
    
    /**
     * 处理外部系统的文献数据源同步请求
     */
    public void handleSyncRequest(String message) {
        // TODO: 实现外部同步逻辑
        throw new UnsupportedOperationException("handleSyncRequest not implemented yet");
    }
    
    /**
     * 处理配置变更通知
     */
    public void handleConfigurationChange(String message) {
        // TODO: 实现配置变更处理逻辑
        throw new UnsupportedOperationException("handleConfigurationChange not implemented yet");
    }
}
