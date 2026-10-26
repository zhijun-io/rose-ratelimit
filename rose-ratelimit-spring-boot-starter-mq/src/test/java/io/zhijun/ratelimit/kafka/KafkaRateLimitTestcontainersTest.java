package io.zhijun.ratelimit.kafka;

import static org.assertj.core.api.Assertions.*;

import io.zhijun.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.kafka.autoconfigure.RateLimitKafkaAutoConfiguration;
import io.zhijun.ratelimit.mq.autoconfigure.RateLimitMqAutoConfiguration;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** End-to-end: real broker + {@link RateLimitKafkaRecordInterceptor} before listener. */
class KafkaRateLimitTestcontainersTest {
    private static KafkaContainer kafka;
    private static String bootstrapServers;

    @BeforeAll
    static void startKafka() throws Exception {
        assumeDockerAvailable();
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
        kafka.start();
        bootstrapServers = kafka.getBootstrapServers();
        try (AdminClient admin = AdminClient.create(
                Map.of(org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            admin.createTopics(List.of(new NewTopic("orders", 1, (short) 1)))
                    .all()
                    .get(30, TimeUnit.SECONDS);
        }
    }

    @AfterAll
    static void stopKafka() {
        if (kafka != null) {
            kafka.stop();
        }
    }

    @Test
    void secondRecordIsNotDeliveredToListenerWhenCapacityIsOne() {
        assumeDockerAvailable();
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        Bucket4jRateLimitAutoConfiguration.class,
                        RateLimitSharedAutoConfiguration.class,
                        RateLimitAutoConfiguration.class,
                        RateLimitMqAutoConfiguration.class,
                        RateLimitKafkaAutoConfiguration.class))
                .withUserConfiguration(KafkaBeans.class)
                .withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "spring.kafka.bootstrap-servers=" + bootstrapServers,
                        "spring.kafka.consumer.group-id=g1",
                        "spring.kafka.consumer.auto-offset-reset=earliest",
                        "rose.ratelimit.rules[0].id=orders",
                        "rose.ratelimit.rules[0].stage=MQ",
                        "rose.ratelimit.rules[0].target=mq:kafka:orders#g1",
                        "rose.ratelimit.rules[0].capacity=1",
                        "rose.ratelimit.rules[0].refill-period=1d");

        runner.run(context -> {
            assertThat(context.getBean(RateLimitRules.class).externalMqRule("kafka:orders#g1"))
                    .isNotNull();
            OrderListener listener = context.getBean(OrderListener.class);
            KafkaTemplate<String, String> template = context.getBean(KafkaTemplate.class);
            @SuppressWarnings("unchecked")
            ConcurrentMessageListenerContainer<String, String> container =
                    context.getBean("ordersListenerContainer", ConcurrentMessageListenerContainer.class);
            container.start();
            try {
                template.send("orders", "first");
                template.send("orders", "second");
                Awaitility.await()
                        .atMost(Duration.ofSeconds(20))
                        .untilAsserted(() -> assertThat(listener.received()).isGreaterThanOrEqualTo(1));
                assertThat(listener.received()).isEqualTo(1);
            } finally {
                container.stop();
            }
        });
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class KafkaBeans {
        @Bean
        OrderListener orderListener() {
            return new OrderListener();
        }

        @Bean
        ProducerFactory<String, String> producerFactory() {
            Map<String, Object> config = Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    bootstrapServers,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                    StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    StringSerializer.class);
            return new DefaultKafkaProducerFactory<>(config);
        }

        @Bean
        KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }

        @Bean
        ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
            Map<String, Object> consumerProps = Map.of(
                    org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    bootstrapServers,
                    org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG,
                    "g1",
                    org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                    "earliest",
                    org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringDeserializer.class,
                    org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringDeserializer.class);
            ConcurrentKafkaListenerContainerFactory<String, String> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerProps));
            return factory;
        }

        @Bean
        ConcurrentMessageListenerContainer<String, String> ordersListenerContainer(
                ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory,
                OrderListener orderListener) {
            ConcurrentMessageListenerContainer<String, String> container =
                    kafkaListenerContainerFactory.createContainer("orders");
            container.getContainerProperties().setGroupId("g1");
            container.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
            container.setupMessageListener((org.springframework.kafka.listener.MessageListener<String, String>)
                    record -> orderListener.onOrder(record.value()));
            container.setAutoStartup(false);
            return container;
        }
    }

    static class OrderListener {
        private int received;

        @KafkaListener(topics = "orders", groupId = "g1")
        void onOrder(String payload) {
            received++;
        }

        int received() {
            return received;
        }
    }

    private static void assumeDockerAvailable() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
    }
}
