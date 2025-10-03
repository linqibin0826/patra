package com.patra.starter.rocketmq.consumer;

import com.patra.starter.rocketmq.autoconfigure.RocketMQProperties;
import com.patra.starter.rocketmq.core.message.Message;
import com.patra.starter.rocketmq.naming.NamespaceResolver;
import com.patra.starter.rocketmq.validation.GroupValidator;
import com.patra.starter.rocketmq.validation.TopicValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 消费者启动器：扫描 @MessageListener 注解并注册监听容器。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class ConsumerBootstrap implements BeanPostProcessor, SmartInitializingSingleton, ApplicationContextAware {

    private final Environment environment;
    private final RocketMQProperties properties;
    private final TopicValidator topicValidator;
    private final GroupValidator groupValidator;
    private ApplicationContext applicationContext;

    public ConsumerBootstrap(Environment environment,
                             RocketMQProperties properties,
                             TopicValidator topicValidator,
                             GroupValidator groupValidator) {
        this.environment = environment;
        this.properties = properties;
        this.topicValidator = topicValidator;
        this.groupValidator = groupValidator;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        DefaultListableBeanFactory factory = (DefaultListableBeanFactory)
                applicationContext.getAutowireCapableBeanFactory();

        String nameServer = environment.getProperty("rocketmq.name-server");
        String serviceName = environment.getProperty("spring.application.name", "unknown");
        String namespace = NamespaceResolver.resolve(environment, properties.getNaming().getNamespace());

        // 扫描所有 @MessageListener 注解的 Bean
        String[] beanNames = applicationContext.getBeanNamesForAnnotation(MessageListener.class);
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            MessageListener annotation = applicationContext.findAnnotationOnBean(beanName, MessageListener.class);

            if (!(bean instanceof MessageHandler<?> handler)) {
                throw new IllegalStateException(
                        "@MessageListener 只能标注在 MessageHandler<T> 实现类上: " + bean.getClass().getName()
                );
            }

            registerConsumer(factory, handler, annotation, nameServer, serviceName, namespace);
        }
    }

    /**
     * 注册单个消费者。
     */
    private void registerConsumer(DefaultListableBeanFactory factory,
                                   MessageHandler<?> handler,
                                   MessageListener annotation,
                                   String nameServer,
                                   String serviceName,
                                   String namespace) {
        try {
            // 直接从 channel 字符串解析
            String channelStr = annotation.channel();
            if (channelStr == null || channelStr.isBlank()) {
                throw new IllegalArgumentException("channel 不能为空");
            }

            String[] parts = channelStr.trim().split("_");
            if (parts.length < 3) {
                throw new IllegalArgumentException(
                        "channel 格式错误，应为 domain_resource_event，实际: " + channelStr
                );
            }

            String domain = parts[0];
            String resource = parts[1];
            String event = parts[2];

            // 构建 Topic 和 Tag
            String topicBase = (domain + "_" + resource).toUpperCase(Locale.ROOT);
            String topic = namespace.toUpperCase(Locale.ROOT) + "_" + topicBase;
            String selector = annotation.selector().isBlank()
                    ? event.toUpperCase(Locale.ROOT)
                    : annotation.selector().toUpperCase(Locale.ROOT);

            // 构建消费组
            String consumerRole = annotation.consumer().toLowerCase(Locale.ROOT);
            String group = ("svc-" + serviceName + "-" + consumerRole + "-cg").toLowerCase(Locale.ROOT);

            // 校验
            topicValidator.validate(topic);
            groupValidator.validate(group);
            validateSelector(selector);

            // 创建容器 Bean 名称
            String containerBeanName = ("rocketmq-listener-" + group + "-" + topicBase + "-" + selector)
                    .toLowerCase(Locale.ROOT);

            if (factory.containsBean(containerBeanName)) {
                log.warn("跳过重复的消费者容器: {}", containerBeanName);
                return;
            }

            // 创建监听容器
            DefaultRocketMQListenerContainer container = new DefaultRocketMQListenerContainer();
            // 注入 Spring 上下文，保证容器内部初始化可获取 Environment/BeanFactory
            container.setApplicationContext(applicationContext);
            container.setNameServer(nameServer);
            container.setTopic(topic);
            container.setConsumerGroup(group);
            container.setSelectorExpression(selector);

            // 设置消费模式
            setConsumeMode(container, annotation.mode());

            // 设置并发度
            setConcurrency(container, Math.max(1, annotation.concurrency()));

            // 创建监听器适配器
            RocketMQListener<Object> listener = message -> {
                long startTime = System.currentTimeMillis();
                try {
                    @SuppressWarnings("unchecked")
                    var msg = (Message<Object>) message;

                    log.info("[CONSUME][START] channel={}, group={}, eventId={}, traceId={}",
                            channelStr, group, msg.getEventId(), msg.getTraceId());

                    @SuppressWarnings("unchecked")
                    MessageHandler<Object> h = (MessageHandler<Object>) handler;
                    h.handle(msg);

                    long cost = System.currentTimeMillis() - startTime;
                    log.info("[CONSUME][SUCCESS] channel={}, group={}, costMs={}, eventId={}",
                            channelStr, group, cost, msg.getEventId());
                } catch (Exception ex) {
                    long cost = System.currentTimeMillis() - startTime;
                    log.error("[CONSUME][FAIL] channel={}, group={}, costMs={}, error={}",
                            channelStr, group, cost, ex.getMessage(), ex);
                    throw new RuntimeException(ex);
                }
            };

            container.setRocketMQListener(listener);

            // 兼容 RocketMQ 默认容器实现：需要注入 RocketMQMessageListener 注解元数据
            // 通过匿名实现提供必须的配置，避免容器在 init 时访问空注解导致 NPE
            RocketMQMessageListener rocketCfg =
                    new org.apache.rocketmq.spring.annotation.RocketMQMessageListener() {
                        @Override
                        public Class<? extends java.lang.annotation.Annotation> annotationType() {
                            return RocketMQMessageListener.class;
                        }
                        @Override
                        public String consumerGroup() { return group; }
                        @Override
                        public String topic() { return topic; }
                        @Override
                        public org.apache.rocketmq.spring.annotation.SelectorType selectorType() {
                            return org.apache.rocketmq.spring.annotation.SelectorType.TAG;
                        }
                        @Override
                        public String selectorExpression() { return selector; }
                        @Override
                        public org.apache.rocketmq.spring.annotation.ConsumeMode consumeMode() {
                            // 我们的自定义枚举为 CONCURRENT/ORDERLY；RocketMQ 注解为 CONCURRENTLY/ORDERLY
                            return annotation.mode() == ConsumeMode.ORDERLY
                                    ? org.apache.rocketmq.spring.annotation.ConsumeMode.ORDERLY
                                    : org.apache.rocketmq.spring.annotation.ConsumeMode.CONCURRENTLY;
                        }
                        @Override
                        public org.apache.rocketmq.spring.annotation.MessageModel messageModel() {
                            return org.apache.rocketmq.spring.annotation.MessageModel.CLUSTERING;
                        }
                        @Override
                        public int consumeThreadMax() { return Math.max(1, annotation.concurrency()); }
                        @Override
                        public int consumeThreadNumber() { return Math.max(1, annotation.concurrency()); }
                        @Override
                        public int maxReconsumeTimes() { return -1; }
                        @Override
                        public long consumeTimeout() { return 15L; }
                        @Override
                        public int replyTimeout() { return 3000; }
                        @Override
                        public String accessKey() { return ""; }
                        @Override
                        public String secretKey() { return ""; }
                        @Override
                        public boolean enableMsgTrace() { return false; }
                        @Override
                        public String customizedTraceTopic() { return ""; }
                        @Override
                        public String nameServer() { return nameServer == null ? "" : nameServer; }
                        @Override
                        public String accessChannel() { return ""; }
                        @Override
                        public String tlsEnable() { return ""; }
                        @Override
                        public String namespace() { return namespace == null ? "" : namespace; }
                        @Override
                        public int delayLevelWhenNextConsume() { return 0; }
                        @Override
                        public int suspendCurrentQueueTimeMillis() { return 1000; }
                        @Override
                        public int awaitTerminationMillisWhenShutdown() { return 0; }
                        @Override
                        public String instanceName() { return ""; }
                    };
            container.setRocketMQMessageListener(rocketCfg);

            // 注册并启动容器
            factory.registerSingleton(containerBeanName, container);
            container.afterPropertiesSet();
            container.start();

            log.info("消费者启动成功: bean={}, group={}, topic={}, selector={}, mode={}",
                    containerBeanName, group, topic, selector, annotation.mode());

        } catch (Exception e) {
            log.error("注册消费者失败: handler={}, error={}", handler.getClass().getName(), e.getMessage(), e);
            throw new IllegalStateException("无法注册消费者: " + handler.getClass().getName(), e);
        }
    }

    /**
     * 设置消费模式（兼容不同版本）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setConsumeMode(DefaultRocketMQListenerContainer container, ConsumeMode mode) {
        try {
            Class<?> rocketMQConsumeMode = Class.forName("org.apache.rocketmq.spring.annotation.ConsumeMode");
            Method setter = DefaultRocketMQListenerContainer.class.getMethod("setConsumeMode", rocketMQConsumeMode);
            Object modeEnum = Enum.valueOf((Class) rocketMQConsumeMode, mode.name());
            setter.invoke(container, modeEnum);
        } catch (Exception e) {
            log.debug("无法设置消费模式（可能版本不支持）: {}", e.getMessage());
        }
    }

    /**
     * 设置并发度（兼容不同版本）。
     */
    private void setConcurrency(DefaultRocketMQListenerContainer container, int concurrency) {
        try {
            Method method = DefaultRocketMQListenerContainer.class.getMethod("setConsumeThreadMax", int.class);
            method.invoke(container, concurrency);
        } catch (Exception e) {
            log.debug("无法设置并发度（可能版本不支持）: {}", e.getMessage());
        }
    }

    /**
     * 校验选择表达式。
     */
    private void validateSelector(String selector) {
        if (selector == null || selector.isBlank() || "*".equals(selector)) {
            return;
        }
        if (selector.contains("||") || selector.contains("|")) {
            throw new IllegalArgumentException("选择表达式不支持 OR 操作: " + selector);
        }
        if (!selector.matches("^[A-Z0-9]+(_[A-Z0-9]+)*$")) {
            throw new IllegalArgumentException("选择表达式应为大写下划线分段: " + selector);
        }
    }

}
