package com.patra.starter.rocketmq.channels;

import java.util.Collection;

/**
 * 代码方式提供允许的 channel 列表（可选实现）。
 */
public interface ChannelCatalog {
    Collection<String> channels();
}

