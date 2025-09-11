///**
// * docref:/docs/adapter/mq/consumer/ERROR-README.md
// * docref:/docs/api/events/ERROR-README.md
// * docref:/docs/domain/aggregates.discovery.md
// */
//package com.patra.registry.adapter.mq.consumer;
//
//import com.patra.registry.api.events.PlatformFieldDictIntegrationEvents;
//import com.patra.registry.app.service.PlatformFieldDictAppService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.cloud.stream.annotation.StreamListener;
//import org.springframework.messaging.handler.annotation.Payload;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class PlatformFieldDictEventConsumer {
//
//    private final PlatformFieldDictAppService appService;
//
//    @StreamListener("platformFieldDictExternal-in-0")
//    public void handleExternalDictEvent(@Payload PlatformFieldDictIntegrationEvents.DictCreated event) {
//        log.info("Received external PlatformFieldDict event for code: {}", event.getCode());
//        // TODO: 处理外部系统的字典事件
//        // 可能需要同步外部字典变更到本地
//    }
//
//    @StreamListener("platformFieldDictValidation-in-0")
//    public void handleDictValidationRequest(@Payload PlatformFieldDictIntegrationEvents.DictUpdated event) {
//        log.info("Received PlatformFieldDict validation request for code: {}", event.getCode());
//        // TODO: 执行字典验证逻辑
//        // 验证字典配置的有效性
//    }
//
//    @StreamListener("platformFieldDictSync-in-0")
//    public void handleDictSyncRequest(@Payload PlatformFieldDictIntegrationEvents.DictActivated event) {
//        log.info("Received PlatformFieldDict sync request for code: {}", event.getCode());
//        // TODO: 执行字典同步逻辑
//        // 同步字典到其他服务或缓存
//    }
//}
