package com.kset.mq.event;

import org.apache.rocketmq.client.autoconfigure.RocketMQProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

final class RocketMqEventDestinationResolver {

    private final RocketMQProperties rocketMqProperties;
    private final Environment environment;

    RocketMqEventDestinationResolver(RocketMQProperties rocketMqProperties, Environment environment) {
        this.rocketMqProperties = rocketMqProperties;
        this.environment = environment;
    }

    String destination(Object event) {
        KsetMqEvent annotation = event.getClass().getAnnotation(KsetMqEvent.class);
        String topic = annotation != null && StringUtils.hasText(annotation.topic())
                ? annotation.topic()
                : defaultTopic();
        return destination(topic, annotation != null ? annotation.tag() : null);
    }

    String tag(Object event) {
        KsetMqEvent annotation = event.getClass().getAnnotation(KsetMqEvent.class);
        return annotation != null ? annotation.tag() : null;
    }

    String destination(String topic, String tag) {
        String resolvedTopic = StringUtils.hasText(topic) ? topic : defaultTopic();
        if (StringUtils.hasText(tag) && !"*".equals(tag)) {
            return resolvedTopic + ":" + tag;
        }
        return resolvedTopic;
    }

    private String defaultTopic() {
        if (rocketMqProperties.getProducer() != null
                && StringUtils.hasText(rocketMqProperties.getProducer().getTopic())) {
            return rocketMqProperties.getProducer().getTopic();
        }
        String applicationName = environment.getProperty("spring.application.name");
        String name = StringUtils.hasText(applicationName) ? applicationName : "kset";
        return name + "-event";
    }
}
