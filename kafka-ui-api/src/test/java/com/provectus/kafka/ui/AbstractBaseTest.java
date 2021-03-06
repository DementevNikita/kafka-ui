package com.provectus.kafka.ui;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractBaseTest {

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.2.1"))
                .withNetwork(Network.SHARED);

        public final SchemaRegistryContainer schemaRegistry = new SchemaRegistryContainer("5.2.1")
                .withKafka(kafka)
                .dependsOn(kafka);

        public final KafkaConnectContainer kafkaConnect = new KafkaConnectContainer("5.2.1")
                .withKafka(kafka)
                .waitingFor(
                        Wait.forLogMessage(".*Finished starting connectors and tasks.*", 1)
                )
                .dependsOn(kafka)
                .dependsOn(schemaRegistry)
                .withStartupTimeout(Duration.ofMinutes(15));

        @Override
        public void initialize(@NotNull ConfigurableApplicationContext context) {
            kafka.start();
            schemaRegistry.start();
            kafkaConnect.start();

            System.setProperty("kafka.clusters.0.name", "local");
            System.setProperty("kafka.clusters.0.bootstrapServers", kafka.getBootstrapServers());
            System.setProperty("kafka.clusters.0.schemaRegistry", schemaRegistry.getTarget());
            System.setProperty("kafka.clusters.0.kafkaConnect.0.name", "kafka-connect");
            System.setProperty("kafka.clusters.0.kafkaConnect.0.address", kafkaConnect.getTarget());

            context.addApplicationListener((ApplicationListener<ContextClosedEvent>) event -> {
                kafkaConnect.close();
                schemaRegistry.close();
                kafka.close();
            });
        }
    }
}
