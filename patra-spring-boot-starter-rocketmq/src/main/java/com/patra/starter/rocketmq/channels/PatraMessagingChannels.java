package com.patra.starter.rocketmq.channels;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明本服务允许发送/接收的 channel 列表（小写点分段）。
 * <p>将该注解标记在任意 Spring Bean 类上（如 @Component/@Configuration），
 * Starter 会在启动时收集并用于运行期的 channel 校验。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PatraMessagingChannels {
    String[] value();
}

