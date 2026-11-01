package dev.patra.analytics.infra.collect.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.analytics.domain.collect.exception.EventPublishUnavailableException;
import dev.patra.analytics.domain.collect.model.vo.OutboundMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaEventPublishUnavailableIT {

  @Test
  void publish_fails_fast_when_broker_unreachable() {
    DefaultKafkaProducerFactory<String, String> producerFactory =
        new DefaultKafkaProducerFactory<>(KafkaProducerSettings.forBootstrap("127.0.0.1:1"));
    KafkaEventPublishAdapter adapter =
        new KafkaEventPublishAdapter(
            new KafkaTemplate<>(producerFactory),
            new KafkaPublishProperties("events.raw", "events.dlq", Duration.ofSeconds(5)),
            Clock.systemUTC(),
            new SimpleMeterRegistry());
    List<OutboundMessage> batch =
        IntStream.range(0, 500).mapToObj(i -> OutboundMessage.raw("d" + i, "{}")).toList();

    long started = System.nanoTime();
    assertThatThrownBy(() -> adapter.publish(batch))
        .isInstanceOf(EventPublishUnavailableException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(6));
    producerFactory.destroy();
  }
}
