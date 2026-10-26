package io.zhijun.ratelimit.rabbit;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/** Builds `rabbit:queue#listenerId` selectors from {@link RabbitListener} metadata. */
final class RabbitListenerSelectors {
    private RabbitListenerSelectors() {}

    static List<String> selectors(RabbitListener listener, Environment environment) {
        List<String> queues = new ArrayList<>();
        for (String queue : listener.queues()) {
            if (StringUtils.hasText(queue)) {
                queues.add(queue);
            }
        }
        for (org.springframework.amqp.rabbit.annotation.Queue declared : listener.queuesToDeclare()) {
            String queue = declared.name();
            if (!StringUtils.hasText(queue)) {
                queue = declared.value();
            }
            if (StringUtils.hasText(queue)) {
                queues.add(queue);
            }
        }
        if (queues.isEmpty()) {
            return List.of();
        }
        String listenerId = listener.id();
        if (!StringUtils.hasText(listenerId)) {
            listenerId = listener.group();
        }
        if (!StringUtils.hasText(listenerId)) {
            listenerId = environment.getProperty("spring.application.name", "application");
        }
        List<String> selectors = new ArrayList<>();
        for (String queue : queues) {
            selectors.add("rabbit:" + queue + "#" + listenerId);
        }
        return selectors;
    }
}
