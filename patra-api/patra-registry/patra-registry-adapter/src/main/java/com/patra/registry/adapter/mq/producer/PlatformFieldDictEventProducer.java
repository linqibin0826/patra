///**
// * docref:/docs/adapter/mq/producer/README.md
// * docref:/docs/api/events/README.md
// * docref:/docs/domain/aggregates.discovery.md
// */
//package com.patra.registry.adapter.mq.producer;
//
//import com.patra.registry.api.events.PlatformFieldDictIntegrationEvents;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.cloud.stream.function.StreamBridge;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class PlatformFieldDictEventProducer {
//
//    private final StreamBridge streamBridge;
//
//    public void publishDictCreated(PlatformFieldDictIntegrationEvents.DictCreated event) {
//        log.info("Publishing PlatformFieldDict created event for code: {}", event.getCode());
//        streamBridge.send("platformFieldDictCreated-out-0", event);
//    }
//
//    public void publishDictUpdated(PlatformFieldDictIntegrationEvents.DictUpdated event) {
//        log.info("Publishing PlatformFieldDict updated event for code: {}", event.getCode());
//        streamBridge.send("platformFieldDictUpdated-out-0", event);
//    }
//
//    public void publishDictActivated(PlatformFieldDictIntegrationEvents.DictActivated event) {
//        log.info("Publishing PlatformFieldDict activated event for code: {}", event.getCode());
//        streamBridge.send("platformFieldDictActivated-out-0", event);
//    }
//
//    public void publishDictDeactivated(PlatformFieldDictIntegrationEvents.DictDeactivated event) {
//        log.info("Publishing PlatformFieldDict deactivated event for code: {}", event.getCode());
//        streamBridge.send("platformFieldDictDeactivated-out-0", event);
//    }
//
//    public void publishDictDeleted(PlatformFieldDictIntegrationEvents.DictDeleted event) {
//        log.info("Publishing PlatformFieldDict deleted event for code: {}", event.getCode());
//        streamBridge.send("platformFieldDictDeleted-out-0", event);
//    }
//}
