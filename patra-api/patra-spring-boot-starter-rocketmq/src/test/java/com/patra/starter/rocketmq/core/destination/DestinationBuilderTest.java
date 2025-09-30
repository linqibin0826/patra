package com.patra.starter.rocketmq.core.destination;

import com.patra.starter.rocketmq.core.channel.Channel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationBuilderTest {

    @Test
    void build_shouldUppercaseSegmentsAndApplyNamespace() {
        DestinationBuilder builder = new DestinationBuilder("dev");
        Destination destination = builder.build(Channel.of("ingest.task.ready"));
        assertThat(destination.topic()).isEqualTo("DEV.INGEST.TASK");
        assertThat(destination.tag()).isEqualTo("READY");
    }

    @Test
    void build_shouldHandleEmptyNamespace() {
        DestinationBuilder builder = new DestinationBuilder(null);
        Destination destination = builder.build(Channel.of("registry.snapshot.created"));
        assertThat(destination.topic()).isEqualTo("REGISTRY.SNAPSHOT");
        assertThat(destination.tag()).isEqualTo("CREATED");
    }
}
