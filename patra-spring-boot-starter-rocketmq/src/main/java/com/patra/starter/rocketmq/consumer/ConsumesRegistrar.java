package com.patra.starter.rocketmq.consumer;

import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import com.patra.starter.rocketmq.support.EnvNamespaceResolver;
import com.patra.starter.rocketmq.support.GroupNameValidator;
import com.patra.starter.rocketmq.support.TopicNameValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 扫描 {@link Consumes} 注解并在启动期注册 RocketMQ 监听容器。
 */
@Slf4j
public class ConsumesRegistrar implements BeanPostProcessor, SmartInitializingSingleton, ApplicationContextAware {

    private final Environment environment;
    private final PatraRocketMQProperties properties;
    private ApplicationContext applicationContext;

    public ConsumesRegistrar(Environment environment, ObjectProvider<PatraRocketMQProperties> propertiesProvider) {
        this.environment = environment;
        this.properties = propertiesProvider.getIfAvailable(PatraRocketMQProperties::new);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        DefaultListableBeanFactory factory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        String nameServer = environment.getProperty("rocketmq.name-server");
        String serviceName = environment.getProperty("spring.application.name", "unknown");
        String namespace = EnvNamespaceResolver.resolve(environment, properties.getNaming());

        // 1) 处理通用注解 @Consumes
        String[] beanNames = applicationContext.getBeanNamesForAnnotation(Consumes.class);
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Consumes ann = applicationContext.findAnnotationOnBean(beanName, Consumes.class);
            if (!(bean instanceof PatraMessageHandler<?> handler)) {
                throw new IllegalStateException("@Consumes 仅能标注在 PatraMessageHandler<T> 实现类上: " + bean.getClass().getName());
            }
            String domain;
            String resource;
            String event;
            if (StringUtils.hasText(ann.channel())) {
                String ch = ann.channel().trim().toLowerCase(Locale.ROOT);
                String[] parts = ch.split("\\.");
                if (parts.length < 3) {
                    throw new IllegalArgumentException("@Consumes.channel 必须至少三段：domain.resource.event，实际为 " + ann.channel());
                }
                domain = parts[0];
                resource = parts[1];
                event = String.join(".", java.util.Arrays.copyOfRange(parts, 2, parts.length));
            } else {
                // 解析 ChannelKey（通过反射调用 domain()/resource()/event()）
                Enum<?> channelKey = resolveChannelKey(ann.channelEnum(), ann.channelName());
                domain = invokeNoArg(channelKey, "domain");
                resource = invokeNoArg(channelKey, "resource");
                event = invokeNoArg(channelKey, "event");
            }
            if (!StringUtils.hasText(domain) || !StringUtils.hasText(resource) || !StringUtils.hasText(event)) {
                throw new IllegalArgumentException("ChannelKey 缺少必要的 domain/resource/event 信息");
            }
            String topicBase = (domain + "." + resource).toUpperCase(Locale.ROOT);
            String topic = namespace.toUpperCase(Locale.ROOT) + "." + topicBase;
            String selector = StringUtils.hasText(ann.selector())
                    ? ann.selector().trim().toUpperCase(Locale.ROOT)
                    : event.trim().toUpperCase(Locale.ROOT);
            String consumerRole = ann.consumer().trim().toLowerCase(Locale.ROOT);
            String group = ("svc-" + serviceName + '-' + consumerRole + "-cg").toLowerCase(Locale.ROOT);

            // 命名校验
            PatraRocketMQProperties.Naming naming = properties.getNaming();
            TopicNameValidator.validate(topic, naming);
            GroupNameValidator.validate(group, naming);
            validateSelector(selector);

            // 注册容器
            String containerBeanName = ("rocketmqListenerContainer-" + group + '-' + topicBase + '-' + selector).toLowerCase(Locale.ROOT);
            if (factory.containsBean(containerBeanName)) {
                log.warn("重复的消费者容器定义被忽略: bean={}", containerBeanName);
                continue;
            }
            DefaultRocketMQListenerContainer container = new DefaultRocketMQListenerContainer();
            container.setNameServer(nameServer);
            container.setTopic(topic);
            container.setConsumerGroup(group);
            // 消费模式：若容器提供 setConsumeMode（RocketMQ 枚举），再做映射设置；否则忽略（由容器默认处理）。
            try {
                Class<?> rmqConsumeMode = Class.forName("org.apache.rocketmq.spring.annotation.ConsumeMode");
                Method setter = DefaultRocketMQListenerContainer.class.getMethod("setConsumeMode", rmqConsumeMode);
                Object modeEnum = Enum.valueOf((Class) rmqConsumeMode, ann.mode().name());
                setter.invoke(container, modeEnum);
            } catch (Exception ignored) { /* 兼容不同版本，不强依赖 */ }
            container.setSelectorExpression(selector);
            // 尝试设置并发度（部分版本提供该 setter；为兼容，这里用反射）
            try {
                Method m = DefaultRocketMQListenerContainer.class.getMethod("setConsumeThreadMax", int.class);
                m.invoke(container, Math.max(1, ann.concurrency()));
            } catch (Exception ignored) { /* no-op for compatibility */ }

            // 组装 RocketMQListener 适配器（仅记录日志，不做实际处理）
            RocketMQListener<Object> listener = message -> {
                long t0 = System.currentTimeMillis();
                try {
                    // 泛型擦除下做一次安全转型
                    @SuppressWarnings("unchecked")
                    var msg = (com.patra.starter.rocketmq.model.PatraMessage<Object>) message;
                    log.info("[CONSUME][START] channel={} group={} eventId={} traceId={}",
                            (domain + '.' + resource + '.' + event), group, msg.getEventId(), msg.getTraceId());
                    // 暂不处理数据，仅日志
                    // handler.handle(msg);
                    long cost = System.currentTimeMillis() - t0;
                    log.info("[CONSUME][SUCCESS] channel={} group={} costMs={} eventId={}",
                            (domain + '.' + resource + '.' + event), group, cost, msg.getEventId());
                } catch (Exception ex) {
                    long cost = System.currentTimeMillis() - t0;
                    log.error("[CONSUME][FAIL] channel={} group={} costMs={} err={}",
                            (domain + '.' + resource + '.' + event), group, cost, ex.getMessage(), ex);
                    throw ex;
                }
            };
            container.setRocketMQListener(listener);

            factory.registerSingleton(containerBeanName, container);
            try {
                container.afterPropertiesSet();
                container.start();
                log.info("已注册并启动消费者: bean={} group={} topic={} selector={} mode={}",
                        containerBeanName, group, topic, selector, ann.mode());
            } catch (Exception e) {
                throw new IllegalStateException("启动 RocketMQ 监听容器失败: bean=" + containerBeanName + ", err=" + e.getMessage(), e);
            }
        }

        // 2) 处理强类型服务注解（如 IngestConsumes）：查找所有 PatraMessageHandler Bean，尝试识别带有 value()=Enum 的注解
        String[] handlerBeans = applicationContext.getBeanNamesForType(PatraMessageHandler.class);
        for (String beanName : handlerBeans) {
            // 已使用通用注解注册的跳过
            if (applicationContext.findAnnotationOnBean(beanName, Consumes.class) != null) continue;
            Object bean = applicationContext.getBean(beanName);
            Annotation[] anns = bean.getClass().getAnnotations();
            for (Annotation a : anns) {
                // 识别模式：注解存在 value() 方法，返回 Enum；该 Enum 需提供 domain()/resource()/event() 方法
                try {
                    Method valueMethod = a.annotationType().getMethod("value");
                    Object v = valueMethod.invoke(a);
                    if (!(v instanceof Enum<?> channelKey)) continue;
                    // 读取附加属性
                    String consumerRole = readAttr(a, "consumer", String.class, null);
                    if (!StringUtils.hasText(consumerRole)) {
                        throw new IllegalArgumentException("强类型消费注解缺少 consumer 属性: " + a.annotationType().getName());
                    }
                    String selector = readAttr(a, "selector", String.class, "");
                    Integer concurrency = readAttr(a, "concurrency", Integer.class, 1);
                    Object modeObj = readAttrRaw(a, "mode");
                    String modeName = modeObj != null ? modeObj.toString() : "CONCURRENT";

                    String domain = invokeNoArg(channelKey, "domain");
                    String resource = invokeNoArg(channelKey, "resource");
                    String event = invokeNoArg(channelKey, "event");
                    String topicBase = (domain + "." + resource).toUpperCase(Locale.ROOT);
                    String topic = namespace.toUpperCase(Locale.ROOT) + "." + topicBase;
                    String selectorFinal = StringUtils.hasText(selector) ? selector.trim().toUpperCase(Locale.ROOT) : event.trim().toUpperCase(Locale.ROOT);
                    String group = ("svc-" + serviceName + '-' + consumerRole.trim().toLowerCase(Locale.ROOT) + "-cg").toLowerCase(Locale.ROOT);

                    // 命名校验
                    PatraRocketMQProperties.Naming naming = properties.getNaming();
                    TopicNameValidator.validate(topic, naming);
                    GroupNameValidator.validate(group, naming);
                    validateSelector(selectorFinal);

                    String containerBeanName = ("rocketmqListenerContainer-" + group + '-' + topicBase + '-' + selectorFinal).toLowerCase(Locale.ROOT);
                    if (factory.containsBean(containerBeanName)) continue;

                    DefaultRocketMQListenerContainer container = new DefaultRocketMQListenerContainer();
                    container.setNameServer(nameServer);
                    container.setTopic(topic);
                    container.setConsumerGroup(group);
                    try {
                        Class<?> rmqConsumeMode = Class.forName("org.apache.rocketmq.spring.annotation.ConsumeMode");
                        Method setter = DefaultRocketMQListenerContainer.class.getMethod("setConsumeMode", rmqConsumeMode);
                        Object modeEnum = Enum.valueOf((Class) rmqConsumeMode, modeName);
                        setter.invoke(container, modeEnum);
                    } catch (Exception ignored) { }
                    try {
                        Method m = DefaultRocketMQListenerContainer.class.getMethod("setConsumeThreadMax", int.class);
                        m.invoke(container, Math.max(1, concurrency));
                    } catch (Exception ignored) { }

                    RocketMQListener<Object> listener = message -> {
                        long t0 = System.currentTimeMillis();
                        try {
                            @SuppressWarnings("unchecked")
                            var msg = (com.patra.starter.rocketmq.model.PatraMessage<Object>) message;
                            log.info("[CONSUME][START] channel={} group={} eventId={} traceId={}",
                                    (domain + '.' + resource + '.' + event), group, msg.getEventId(), msg.getTraceId());
                            long cost = System.currentTimeMillis() - t0;
                            log.info("[CONSUME][SUCCESS] channel={} group={} costMs={} eventId={}",
                                    (domain + '.' + resource + '.' + event), group, cost, msg.getEventId());
                        } catch (Exception ex) {
                            long cost = System.currentTimeMillis() - t0;
                            log.error("[CONSUME][FAIL] channel={} group={} costMs={} err={}",
                                    (domain + '.' + resource + '.' + event), group, cost, ex.getMessage(), ex);
                            throw ex;
                        }
                    };
                    container.setRocketMQListener(listener);
                    container.setSelectorExpression(selectorFinal);
                    factory.registerSingleton(containerBeanName, container);
                    try {
                        container.afterPropertiesSet();
                        container.start();
                        log.info("已注册并启动消费者: bean={} group={} topic={} selector={} mode={}",
                                containerBeanName, group, topic, selectorFinal, modeName);
                    } catch (Exception e) {
                        throw new IllegalStateException("启动 RocketMQ 监听容器失败: bean=" + containerBeanName + ", err=" + e.getMessage(), e);
                    }
                } catch (NoSuchMethodException ignored) {
                    // 该注解无 value()，跳过
                } catch (Exception e) {
                    throw new IllegalStateException("解析强类型消费注解失败: bean=" + beanName + ", ann=" + a.annotationType().getName() + ", err=" + e.getMessage(), e);
                }
            }
        }
    }

    private Enum<?> resolveChannelKey(Class<? extends Enum<?>> enumClass, String name) {
        if (enumClass == null || enumClass == PlaceholderChannel.class) {
            throw new IllegalArgumentException("@Consumes.channelEnum 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("@Consumes.channelName 不能为空");
        }
        try {
            return Enum.valueOf((Class) enumClass, name.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("channelName '" + name + "' 不存在于枚举 " + enumClass.getName());
        }
    }

    private String invokeNoArg(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object v = m.invoke(target);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("ChannelKey 缺少方法: " + method + "()", e);
        }
    }

    private void validateSelector(String selector) {
        if (!StringUtils.hasText(selector)) return;
        if (selector.contains("||") || selector.contains("|")) {
            throw new IllegalArgumentException("selectorExpression 暂不支持 OR 表达式: " + selector);
        }
        if (!selector.matches("^[A-Z0-9]+(\\.[A-Z0-9]+)*$")) {
            throw new IllegalArgumentException("selectorExpression 应为大写点分段: " + selector);
        }
    }

    private <T> T readAttr(Annotation ann, String name, Class<T> type, T def) {
        try {
            Object v = ann.annotationType().getMethod(name).invoke(ann);
            return type.cast(v);
        } catch (NoSuchMethodException e) {
            return def;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取注解属性失败: " + ann.annotationType().getName() + '.' + name, e);
        }
    }

    private Object readAttrRaw(Annotation ann, String name) {
        try {
            return ann.annotationType().getMethod(name).invoke(ann);
        } catch (Exception e) {
            return null;
        }
    }
}
