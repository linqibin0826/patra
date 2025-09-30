package com.patra.common.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelKeyTest {

    private final ChannelKey normalKey = new ChannelKey() {
        @Override
        public String domain() {
            return "ingest";
        }

        @Override
        public String resource() {
            return "task";
        }

        @Override
        public String event() {
            return "ready";
        }
    };

    @Test
    void channel_shouldJoinSegmentsWithDot() {
        assertThat(normalKey.channel()).isEqualTo("ingest.task.ready");
    }

    @Test
    void channel_shouldRespectOriginalCasing() {
        ChannelKey mixed = new ChannelKey() {
            @Override
            public String domain() {
                return "Registry";
            }

            @Override
            public String resource() {
                return "Config";
            }

            @Override
            public String event() {
                return "Updated";
            }
        };
        assertThat(mixed.channel()).isEqualTo("Registry.Config.Updated");
    }
}
