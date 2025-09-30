package com.patra.starter.rocketmq.publisher;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.rocketmq.core.channel.Channel;
import com.patra.starter.rocketmq.core.channel.ChannelRegistry;
import com.patra.starter.rocketmq.core.destination.Destination;
import com.patra.starter.rocketmq.core.destination.DestinationBuilder;
import com.patra.starter.rocketmq.core.message.Message;
import com.patra.starter.rocketmq.core.message.MessageHeaders;
import com.patra.starter.rocketmq.validation.TopicValidator;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultMessagePublisherTest {

    private RecordingRocketMQTemplate template;
    private DestinationBuilder destinationBuilder;
    private TestChannelRegistry registry;
    private TestTopicValidator topicValidator;
    private ObjectProvider<HttpStdErrors.Group> httpErrors;
    private DefaultMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        template = new RecordingRocketMQTemplate();
        destinationBuilder = new DestinationBuilder("dev");
        registry = new TestChannelRegistry(false);
        topicValidator = new TestTopicValidator(false);
        httpErrors = new StaticGroupProvider(HttpStdErrors.of("ING"));
        publisher = new DefaultMessagePublisher(template, destinationBuilder, registry, topicValidator, httpErrors);
    }

    @Test
    void sendByChannel_shouldValidateAndDelegateToTemplate() {
        registry.allow();
        Channel channel = Channel.of("ingest.task.ready");
        Message<String> message = Message.<String>builder()
                .eventId("evt-1")
                .traceId("trace-1")
                .occurredAt(Instant.parse("2024-05-01T12:00:00Z"))
                .payload("payload")
                .build();

        publisher.sendByChannel(channel, message);

        assertThat(registry.lastValidated).isEqualTo(channel);
        assertThat(template.lastOperation).isEqualTo("convert");
        assertThat(template.lastDestination).isEqualTo("DEV.INGEST.TASK:READY");
        org.springframework.messaging.MessageHeaders headers = template.lastMessage.getHeaders();
        assertThat(headers.get(MessageHeaders.EVENT_ID)).isEqualTo("evt-1");
        assertThat(headers.get(MessageHeaders.TRACE_ID)).isEqualTo("trace-1");
        assertThat(headers.get(MessageHeaders.OCCURRED_AT)).isEqualTo(Instant.parse("2024-05-01T12:00:00Z"));
    }

    @Test
    void sendDelayed_shouldInvokeSyncSendWithDelay() {
        Message<String> message = Message.of("payload");
        publisher.sendDelayed(new Destination("DEV.INGEST.TASK", "READY"), message, 5);
        assertThat(template.lastOperation).isEqualTo("delay");
        assertThat(template.lastDestination).isEqualTo("DEV.INGEST.TASK:READY");
        assertThat(template.lastDelayLevel).isEqualTo(5);
    }

    @Test
    void sendDelayed_shouldRejectInvalidDelayLevel() {
        Message<String> message = Message.of("payload");
        assertThatThrownBy(() -> publisher.sendDelayed(new Destination("DEV.INGEST.TASK", "READY"), message, 0))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("延迟级别必须在 1-18 之间");
    }

    @Test
    void sendByChannel_shouldWrapValidationException() {
        registry.disallow();
        Message<String> message = Message.of("payload");
        Channel channel = Channel.of("ingest.task.ready");
        assertThatThrownBy(() -> publisher.sendByChannel(channel, message))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("发送失败");
    }

    @Test
    void send_shouldWrapTopicValidationException() {
        topicValidator.failOnValidate();
        Message<String> message = Message.of("payload");
        assertThatThrownBy(() -> publisher.send(new Destination("DEV.INGEST.TASK", "READY"), message))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("消息发送失败");
    }

    private static final class RecordingRocketMQTemplate extends RocketMQTemplate {
        private String lastOperation;
        private String lastDestination;
        private org.springframework.messaging.Message<?> lastMessage;
        private String lastHashKey;
        private int lastDelayLevel;

        @Override
        public void convertAndSend(String destination, Object payload) {
            lastOperation = "convert";
            lastDestination = destination;
            lastMessage = (org.springframework.messaging.Message<?>) payload;
        }

        @Override
        public SendResult syncSend(String destination, org.springframework.messaging.Message<?> message) {
            lastOperation = "sync";
            lastDestination = destination;
            lastMessage = message;
            return null;
        }

        @Override
        public SendResult syncSend(String destination, org.springframework.messaging.Message<?> message, long timeout, int delayLevel) {
            lastOperation = "delay";
            lastDestination = destination;
            lastMessage = message;
            lastDelayLevel = delayLevel;
            return null;
        }

        @Override
        public SendResult syncSendOrderly(String destination, org.springframework.messaging.Message<?> message, String hashKey) {
            lastOperation = "orderly";
            lastDestination = destination;
            lastMessage = message;
            lastHashKey = hashKey;
            return null;
        }
    }

    private static final class TestChannelRegistry extends ChannelRegistry {
        private Channel lastValidated;
        private boolean throwError;

        TestChannelRegistry(boolean throwError) {
            super(Collections.emptyList(), false);
            this.throwError = throwError;
        }

        void allow() {
            this.throwError = false;
        }

        void disallow() {
            this.throwError = true;
        }

        @Override
        public void validate(Channel channel) {
            this.lastValidated = channel;
            if (throwError) {
                throw new IllegalArgumentException("not allowed");
            }
        }
    }

    private static final class TestTopicValidator extends TopicValidator {
        private boolean fail;

        TestTopicValidator(boolean fail) {
            super("[A-Z.]+", "DEV");
            this.fail = fail;
        }

        void failOnValidate() {
            this.fail = true;
        }

        @Override
        public void validate(String topic) {
            if (fail) {
                throw new IllegalArgumentException("bad topic");
            }
            super.validate(topic);
        }
    }

    private record StaticGroupProvider(HttpStdErrors.Group group) implements ObjectProvider<HttpStdErrors.Group> {
        @Override
        public HttpStdErrors.Group getObject(Object... args) {
            return group;
        }

        @Override
        public HttpStdErrors.Group getObject() {
            return group;
        }

        @Override
        public HttpStdErrors.Group getIfAvailable() {
            return group;
        }

        @Override
        public HttpStdErrors.Group getIfAvailable(Supplier<HttpStdErrors.Group> defaultSupplier) {
            return group;
        }

        @Override
        public HttpStdErrors.Group getIfUnique() {
            return group;
        }

        @Override
        public HttpStdErrors.Group getIfUnique(Supplier<HttpStdErrors.Group> defaultSupplier) {
            return group;
        }

        @Override
        public Iterator<HttpStdErrors.Group> iterator() {
            return List.of(group).iterator();
        }

        @Override
        public void forEach(Consumer<? super HttpStdErrors.Group> action) {
            action.accept(group);
        }

        @Override
        public Stream<HttpStdErrors.Group> stream() {
            return Stream.of(group);
        }

        @Override
        public Stream<HttpStdErrors.Group> orderedStream() {
            return Stream.of(group);
        }
    }
}
