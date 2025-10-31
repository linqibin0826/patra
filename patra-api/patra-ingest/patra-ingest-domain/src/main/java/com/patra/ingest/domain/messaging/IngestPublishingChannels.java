package com.patra.ingest.domain.messaging;

import com.patra.common.messaging.ChannelKey;
import com.patra.ingest.domain.model.vo.execution.TaskReadyMessage;
import com.patra.ingest.domain.model.vo.relay.LiteratureReadyMessage;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Catalog of message channels published by the ingest domain.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Define all outbound channels via a strongly typed enumeration.
 *   <li>Provide lookup helpers such as {@link #fromChannel(String)}.
 *   <li>Associate each channel with its payload type for validation.
 * </ul>
 *
 * <p><b>Usage</b>:
 *
 * <ul>
 *   <li><b>Internal publishers</b>: call {@code IngestPublishingChannels.TASK_READY.channel()}.
 *   <li><b>External consumers</b>: reference the API contract {@code
 *       IngestPublishedChannels.TASK_READY}.
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum IngestPublishingChannels implements ChannelKey {

  /** Task scheduling ready event. */
  TASK_READY("INGEST", "TASK", "READY", TaskReadyMessage.class),

  /** Literature data ready event targeting catalog ingestion. */
  LITERATURE_DATA_READY("INGEST", "LITERATURE", "DATA_READY", LiteratureReadyMessage.class);

  private final String domain;
  private final String resource;
  private final String event;
  private final Class<?> payloadType;

  IngestPublishingChannels(String domain, String resource, String event, Class<?> payloadType) {
    this.domain = domain;
    this.resource = resource;
    this.event = event;
    this.payloadType = payloadType;
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

  /** Declared payload type for compile-time or runtime validation. */
  public Class<?> payloadType() {
    return payloadType;
  }

  /**
   * Parse the normalized channel string (for example {@code INGEST_TASK_READY}) into an enumeration
   * value.
   *
   * @param channel channel string using uppercase snake case
   * @return matching enum instance, or {@link Optional#empty()} if none matches
   */
  public static Optional<IngestPublishingChannels> fromChannel(String channel) {
    if (channel == null || channel.isBlank()) return Optional.empty();
    String ch = channel.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(it -> it.channel().equals(ch)).findFirst();
  }
}
