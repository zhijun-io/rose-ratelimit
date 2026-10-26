package io.zhijun.ratelimit.rabbit;

import java.util.HashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Maps consumer queues to listener ids discovered at startup. */
public final class RabbitMqListenerRegistry {
    private final Map<String, String> queueToListenerId = new HashMap<>();

    void register(String queue, String listenerId) {
        if (!StringUtils.hasText(queue) || !StringUtils.hasText(listenerId)) {
            return;
        }
        queueToListenerId.merge(queue, listenerId, (existing, incoming) -> {
            if (existing.equals(incoming)) {
                return existing;
            }
            throw new IllegalStateException(
                    "Multiple @RabbitListener ids for queue '" + queue + "': " + existing + " vs " + incoming);
        });
    }

    public String selectorForQueue(String queue) {
        if (!StringUtils.hasText(queue)) {
            return null;
        }
        String listenerId = queueToListenerId.get(queue);
        if (!StringUtils.hasText(listenerId)) {
            return null;
        }
        return "rabbit:" + queue + "#" + listenerId;
    }
}
