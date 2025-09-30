package com.patra.common.messaging;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedChannelTest {

    @PublishedChannel(
            description = "采集任务准备就绪事件",
            payloadType = String.class,
            deprecated = true,
            deprecationNote = "请迁移至 ingest.task.ready.v2"
    )
    private static final class AnnotatedChannel {
    }

    @PublishedChannel
    private static final String DEFAULT_CHANNEL = "ingest.task.ready";

    @Test
    void annotation_onType_shouldExposeCustomValues() {
        PublishedChannel annotation = AnnotatedChannel.class.getAnnotation(PublishedChannel.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.description()).isEqualTo("采集任务准备就绪事件");
        assertThat(annotation.payloadType()).isSameAs(String.class);
        assertThat(annotation.deprecated()).isTrue();
        assertThat(annotation.deprecationNote()).isEqualTo("请迁移至 ingest.task.ready.v2");
    }

    @Test
    void annotation_onField_shouldProvideDefaults() throws NoSuchFieldException {
        Field field = PublishedChannelTest.class.getDeclaredField("DEFAULT_CHANNEL");
        PublishedChannel annotation = field.getAnnotation(PublishedChannel.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.description()).isEmpty();
        assertThat(annotation.payloadType()).isSameAs(Void.class);
        assertThat(annotation.deprecated()).isFalse();
        assertThat(annotation.deprecationNote()).isEmpty();
    }
}
