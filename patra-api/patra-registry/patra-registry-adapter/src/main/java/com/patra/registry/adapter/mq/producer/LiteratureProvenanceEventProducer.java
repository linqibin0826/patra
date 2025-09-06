package com.patra.registry.adapter.mq.producer;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文献数据源事件生产者
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LiteratureProvenanceEventProducer {
    
    /**
     * 发送文献数据源已创建事件
     */
    public void sendCreatedEvent(Long id, String code, String name) {
        // TODO: 实现事件发送逻辑
        log.info("Sending LiteratureProvenanceCreated event for id: {}, code: {}", id, code);
    }
    
    /**
     * 发送文献数据源已更新事件
     */
    public void sendUpdatedEvent(Long id, String code, String name, Long version) {
        // TODO: 实现事件发送逻辑
        log.info("Sending LiteratureProvenanceUpdated event for id: {}, code: {}", id, code);
    }
    
    /**
     * 发送文献数据源已激活事件
     */
    public void sendActivatedEvent(Long id, String code) {
        // TODO: 实现事件发送逻辑
        log.info("Sending LiteratureProvenanceActivated event for id: {}, code: {}", id, code);
    }
    
    /**
     * 发送文献数据源已停用事件
     */
    public void sendDeactivatedEvent(Long id, String code) {
        // TODO: 实现事件发送逻辑
        log.info("Sending LiteratureProvenanceDeactivated event for id: {}, code: {}", id, code);
    }
    
    /**
     * 发送文献数据源已删除事件
     */
    public void sendDeletedEvent(Long id, String code) {
        // TODO: 实现事件发送逻辑
        log.info("Sending LiteratureProvenanceDeleted event for id: {}, code: {}", id, code);
    }
}
