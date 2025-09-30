package com.patra.starter.rocketmq.core.channel;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Channel 注册表：自动从领域枚举提取并注册所有 channel。
 *
 * <p>设计原则：
 * <ul>
 *   <li>启动时扫描所有实现 ChannelKey 的枚举</li>
 *   <li>自动提取 channel 并注册到白名单</li>
 *   <li>发布/消费时校验 channel 必须在白名单内</li>
 *   <li>保证 SSOT，避免配置与代码不一致</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class ChannelRegistry {

    private final Set<String> registeredChannels = Collections.synchronizedSet(new HashSet<>());
    private final List<Class<? extends Enum<? extends ChannelKey>>> channelEnumClasses;
    private final boolean enforceWhitelist;

    public ChannelRegistry(List<Class<? extends Enum<? extends ChannelKey>>> channelEnumClasses,
                           boolean enforceWhitelist) {
        this.channelEnumClasses = channelEnumClasses != null ? channelEnumClasses : Collections.emptyList();
        this.enforceWhitelist = enforceWhitelist;
    }

    @PostConstruct
    public void init() {
        for (Class<? extends Enum<? extends ChannelKey>> enumClass : channelEnumClasses) {
            registerEnum(enumClass);
        }
        log.info("ChannelRegistry 初始化完成，已注册 {} 个 channel，白名单强制: {}",
                registeredChannels.size(), enforceWhitelist);
        if (log.isDebugEnabled() && !registeredChannels.isEmpty()) {
            log.debug("已注册的 channels: {}", registeredChannels);
        }
    }

    /**
     * 从枚举类注册所有 channel。
     */
    private void registerEnum(Class<? extends Enum<? extends ChannelKey>> enumClass) {
        try {
            Enum<? extends ChannelKey>[] constants = enumClass.getEnumConstants();
            if (constants == null || constants.length == 0) {
                log.warn("枚举类 {} 没有任何常量", enumClass.getName());
                return;
            }
            for (Enum<? extends ChannelKey> constant : constants) {
                ChannelKey key = (ChannelKey) constant;
                String channel = key.channel();
                registeredChannels.add(channel);
                if (log.isDebugEnabled()) {
                    log.debug("注册 channel: {} <- {}.{}", channel, enumClass.getSimpleName(), constant.name());
                }
            }
        } catch (Exception e) {
            log.error("注册枚举 {} 失败: {}", enumClass.getName(), e.getMessage(), e);
            throw new IllegalStateException("无法注册 ChannelKey 枚举: " + enumClass.getName(), e);
        }
    }

    /**
     * 校验 channel 是否合法。
     */
    public void validate(Channel channel) {
        Objects.requireNonNull(channel, "channel 不能为空");

        if (!enforceWhitelist) {
            return;
        }

        if (registeredChannels.isEmpty()) {
            log.warn("白名单为空但 enforceWhitelist=true，跳过校验: {}", channel.value());
            return;
        }

        if (!registeredChannels.contains(channel.value())) {
            throw new IllegalArgumentException(
                    "channel '" + channel.value() + "' 未注册，已注册: " + 
                    registeredChannels.stream().sorted().collect(Collectors.joining(", "))
            );
        }
    }

    /**
     * 获取所有已注册的 channel。
     */
    public Set<String> getRegisteredChannels() {
        return Collections.unmodifiableSet(registeredChannels);
    }
}
