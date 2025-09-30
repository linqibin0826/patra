package com.patra.starter.rocketmq.core.channel;

import com.patra.common.messaging.ChannelKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelTest {

    @Test
    void of_shouldParseSegments() {
        Channel channel = Channel.of("ingest.task.ready");
        assertThat(channel.domain()).isEqualTo("ingest");
        assertThat(channel.resource()).isEqualTo("task");
        assertThat(channel.event()).isEqualTo("ready");
    }

    @Test
    void of_shouldWrapChannelKey() {
        ChannelKey key = new ChannelKey() {
            @Override
            public String domain() { return "relay"; }

            @Override
            public String resource() { return "plan"; }

            @Override
            public String event() { return "completed"; }
        };
        Channel channel = Channel.of(key);
        assertThat(channel.value()).isEqualTo("relay.plan.completed");
    }

    @Test
    void constructor_shouldValidateFormat() {
        assertThatThrownBy(() -> Channel.of("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channel 必须符合格式");
    }
}
