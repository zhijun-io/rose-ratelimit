package io.zhijun.ratelimit.kafka;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.util.StringUtils;

/** Builds `kafka:topic#groupId` selectors from {@link KafkaListener} metadata. */
final class KafkaListenerSelectors {
    private KafkaListenerSelectors() {}

    static List<String> selectors(KafkaListener listener, Environment environment) {
        List<String> topics = new ArrayList<>();
        for (String topic : listener.topics()) {
            if (StringUtils.hasText(topic)) {
                topics.add(topic);
            }
        }
        if (topics.isEmpty() && StringUtils.hasText(listener.topicPattern())) {
            return List.of();
        }
        String groupId = listener.groupId();
        if (!StringUtils.hasText(groupId)) {
            groupId = environment.getProperty("spring.kafka.consumer.group-id");
        }
        if (!StringUtils.hasText(groupId)) {
            groupId = listener.id();
        }
        if (!StringUtils.hasText(groupId) || topics.isEmpty()) {
            return List.of();
        }
        List<String> selectors = new ArrayList<>();
        for (String topic : topics) {
            selectors.add("kafka:" + topic + "#" + groupId);
        }
        return selectors;
    }
}
