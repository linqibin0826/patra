package com.patra.starter.rocketmq.channels;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.rocketmq.config.PatraChannelProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 收集并校验允许使用的 channel（统一注册表）。
 *
 * 数据来源：
 * - Spring Bean 实现 {@link ChannelCatalog}
 * - 带有 {@link PatraMessagingChannels} 注解的任意 Bean 类
 */
@Slf4j
public class ChannelRegistry implements BeanPostProcessor {

    private static final Pattern CHANNEL_PATTERN = Pattern.compile("^[a-z0-9]+(\\.[a-z0-9]+)+$");

    private final Set<String> channels = Collections.synchronizedSet(new HashSet<>());
    private final ObjectProvider<Collection<ChannelCatalog>> catalogsProvider;
    private final ObjectProvider<HttpStdErrors.Group> httpErrorsProvider;
    private final PatraChannelProperties properties;

    public ChannelRegistry(ObjectProvider<Collection<ChannelCatalog>> catalogsProvider,
                           ObjectProvider<HttpStdErrors.Group> httpErrorsProvider,
                           PatraChannelProperties properties) {
        this.catalogsProvider = catalogsProvider;
        this.httpErrorsProvider = httpErrorsProvider;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        Collection<ChannelCatalog> catalogs = catalogsProvider.getIfAvailable(Collections::emptyList);
        if (catalogs != null) {
            for (ChannelCatalog c : catalogs) {
                registerAll(c.channels());
            }
        }
        log.info("ChannelRegistry 初始化完成，已注册 {} 个 channel", channels.size());
    }

    private void registerAll(Collection<String> chs) {
        if (chs == null) return;
        for (String ch : chs) {
            if (!StringUtils.hasText(ch)) continue;
            channels.add(ch.trim().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        PatraMessagingChannels ann = bean.getClass().getAnnotation(PatraMessagingChannels.class);
        if (ann != null && ann.value().length > 0) {
            registerAll(Set.of(ann.value()));
            if (log.isDebugEnabled()) {
                log.debug("从 @PatraMessagingChannels 注册 {} 个 channel：bean={}", ann.value().length, beanName);
            }
        }
        return bean;
    }

    public void validate(String channel) {
        String ch = channel == null ? null : channel.trim();
        if (!StringUtils.hasText(ch)) {
            throw app422("channel must not be blank");
        }
        if (!CHANNEL_PATTERN.matcher(ch).matches()) {
            throw app422("channel must match pattern '^[a-z0-9]+(\\.[a-z0-9]+)+$': " + channel);
        }
        if (StringUtils.hasText(properties.getDomain())) {
            String first = ch.substring(0, ch.indexOf('.'));
            if (!first.equals(properties.getDomain().toLowerCase(Locale.ROOT))) {
                throw app422("channel must start with domain '" + properties.getDomain() + "': " + channel);
            }
        }
        if (properties.isEnforce()) {
            if (channels.isEmpty()) {
                log.warn("enforce=true 但未注册任何 channel，跳过白名单校验：{}", channel);
                return;
            }
            if (!channels.contains(ch)) {
                throw app422("channel not registered: " + channel);
            }
        }
    }

    private ApplicationException app422(String msg) {
        HttpStdErrors.Group grp = httpErrorsProvider != null ? httpErrorsProvider.getIfAvailable() : null;
        if (grp == null) grp = HttpStdErrors.of("UNKNOWN");
        return new ApplicationException(grp.UNPROCESSABLE(), msg);
    }
}

