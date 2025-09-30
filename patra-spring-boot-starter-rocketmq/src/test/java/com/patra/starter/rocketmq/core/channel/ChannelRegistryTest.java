package com.patra.starter.rocketmq.core.channel;

import com.patra.common.messaging.ChannelKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelRegistryTest {

    private enum DemoChannel implements ChannelKey {
        TASK_READY("ingest", "task", "ready"),
        TASK_FAILED("ingest", "task", "failed");

        private final String domain;
        private final String resource;
        private final String event;

        DemoChannel(String domain, String resource, String event) {
            this.domain = domain;
            this.resource = resource;
            this.event = event;
        }

        @Override
        public String domain() {
            return domain;
        }

        @Override
        public String resource() {
            return resource;
        }

        @Override
        public String event() {
            return event;
        }
    }

    @Test
    void init_shouldRegisterChannelsFromEnum() {
        ChannelRegistry registry = new ChannelRegistry(List.of(DemoChannel.class), true);
        registry.init();
        assertThat(registry.getRegisteredChannels()).containsExactlyInAnyOrder(
                "ingest.task.ready", "ingest.task.failed"
        );
    }

    @Test
    void validate_shouldRespectWhitelistFlag() {
        ChannelRegistry registry = new ChannelRegistry(List.of(DemoChannel.class), true);
        registry.init();

        registry.validate(Channel.of("ingest.task.ready"));
        assertThatThrownBy(() -> registry.validate(Channel.of("ingest.plan.created")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未注册");
    }

    @Test
    void validate_shouldSkipWhenWhitelistDisabled() {
        ChannelRegistry registry = new ChannelRegistry(List.of(), false);
        registry.init();
        registry.validate(Channel.of("ingest.plan.created"));
    }
}
