package com.patra.registry.app.event.publisher;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import lombok.RequiredArgsConstructor;

/**
 * 文献数据源事件发布器
 */
@RequiredArgsConstructor
public class LiteratureProvenanceEventPublisher {
    
    /**
     * 发布文献数据源已创建事件
     */
    public void publishCreatedEvent(Long id, String code, String name) {
        // TODO: 实现领域事件发布逻辑
        throw new UnsupportedOperationException("publishCreatedEvent not implemented yet");
    }
    
    /**
     * 发布文献数据源已更新事件
     */
    public void publishUpdatedEvent(Long id, String code, String name, Long version) {
        // TODO: 实现领域事件发布逻辑
        throw new UnsupportedOperationException("publishUpdatedEvent not implemented yet");
    }
    
    /**
     * 发布文献数据源已激活事件
     */
    public void publishActivatedEvent(Long id, String code) {
        // TODO: 实现领域事件发布逻辑
        throw new UnsupportedOperationException("publishActivatedEvent not implemented yet");
    }
    
    /**
     * 发布文献数据源已停用事件
     */
    public void publishDeactivatedEvent(Long id, String code) {
        // TODO: 实现领域事件发布逻辑
        throw new UnsupportedOperationException("publishDeactivatedEvent not implemented yet");
    }
    
    /**
     * 发布文献数据源已删除事件
     */
    public void publishDeletedEvent(Long id, String code) {
        // TODO: 实现领域事件发布逻辑
        throw new UnsupportedOperationException("publishDeletedEvent not implemented yet");
    }
}
