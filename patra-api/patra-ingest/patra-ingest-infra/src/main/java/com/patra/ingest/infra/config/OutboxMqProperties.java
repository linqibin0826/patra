package com.patra.ingest.infra.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * Outbox MQ publishing properties.
 * <p>Constrain publisher implementation wiring and perform fail-fast validation at startup.</p>
 */
@ConfigurationProperties(prefix = "papertrace.ingest.outbox")
@Validated
public class OutboxMqProperties {

    private static final String EXPECTED_PUBLISHER = "rocketmq";

    private String publisher = EXPECTED_PUBLISHER;

    private boolean strictChannelWhitelist;

    private Set<String> allowedChannels = new LinkedHashSet<>();

    @PostConstruct
    public void validate() {
        if (!EXPECTED_PUBLISHER.equalsIgnoreCase(publisher)) {
            throw new IllegalStateException("papertrace.ingest.outbox.publisher must be 'rocketmq', but was '" + publisher + "'");
        }
        if (strictChannelWhitelist && isEmpty(allowedChannels)) {
            throw new IllegalStateException("strict-channel-whitelist=true requires at least one allowed channel");
        }
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = Objects.requireNonNullElse(publisher, EXPECTED_PUBLISHER);
    }

    public boolean isStrictChannelWhitelist() {
        return strictChannelWhitelist;
    }

    public void setStrictChannelWhitelist(boolean strictChannelWhitelist) {
        this.strictChannelWhitelist = strictChannelWhitelist;
    }

    public Set<String> getAllowedChannels() {
        return Collections.unmodifiableSet(allowedChannels);
    }

    public void setAllowedChannels(Set<String> allowedChannels) {
        if (allowedChannels == null) {
            this.allowedChannels = new LinkedHashSet<>();
            return;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String channel : allowedChannels) {
            if (channel == null) {
                continue;
            }
            String value = channel.trim();
            if (!value.isEmpty()) {
                normalized.add(value.toUpperCase(Locale.ROOT));
            }
        }
        this.allowedChannels = normalized;
    }

    public boolean isChannelAllowed(String channel) {
        if (!strictChannelWhitelist) {
            return true;
        }
        if (channel == null) {
            return false;
        }
        String normalized = channel.trim().toUpperCase(Locale.ROOT);
        return allowedChannels.contains(normalized);
    }
}
