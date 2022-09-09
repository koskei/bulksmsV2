package com.bettersms.bulksmsV2;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@SpringBootApplication
@EnableRetry
public class BulksmsV2Application {

    static final String TOPIC_EXCHANGE_NAME = "spring-boot-exchange";
    @Value("${spring.rabbitmq.que.name}")
    public String bulksSmsQueName;

    private Environment environment;

    public BulksmsV2Application(Environment environment) {
        this.environment = environment;
    }


    @Bean
    Queue queue() {
        return new Queue(bulksSmsQueName, true);
    }

    @Bean
    TopicExchange exchange() {
        return new TopicExchange(TOPIC_EXCHANGE_NAME);
    }

    @Bean
    Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("foo.bar.#");
    }

    @Bean
    protected ConnectionFactory connectionFactory() {
        final ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(environment.getProperty("executors.connection.core-size", Integer.class, 5));
        taskExecutor.setMaxPoolSize(environment.getProperty("executors.connection.max-pool-size", Integer.class, 100));
        taskExecutor.setThreadGroupName("rabbit-conn");
        taskExecutor.setThreadNamePrefix("conn-rabbit-");

        return new CachingConnectionFactory(createConnectionFactory(taskExecutor));
    }

    public com.rabbitmq.client.ConnectionFactory createConnectionFactory(ThreadPoolTaskExecutor taskExecutor) {
        com.rabbitmq.client.ConnectionFactory connectionFactory = new com.rabbitmq.client.ConnectionFactory();
        //Declare this properties in the application.properties file config to use custom values
        connectionFactory.setHost(environment.getProperty("spring.rabbitmq.host", "localhost"));
        connectionFactory.setPort(environment.getProperty("spring.rabbitmq.port", Integer.class, 5672));
        connectionFactory.setUsername(environment.getProperty("spring.rabbitmq.username", "guest"));
        connectionFactory.setPassword(environment.getProperty("spring.rabbitmq.password", "guest"));

//    connectionFactory.setThreadFactory(taskExecutor);
        return connectionFactory;
    }

    @Bean
    DirectMessageListenerContainer directMessageListenerContainer(ConnectionFactory connectionFactory,
                                                                  SmsSender receiver) {
        DirectMessageListenerContainer listenerContainer = new DirectMessageListenerContainer(connectionFactory);
        listenerContainer.setQueueNames(bulksSmsQueName);
        listenerContainer.setTaskExecutor(taskExecutor());
        listenerContainer.setAcknowledgeMode(AcknowledgeMode.AUTO);
        listenerContainer.setConsumersPerQueue(environment.getProperty("spring.consumer.per.que", Integer.class, 200));
        listenerContainer.setPrefetchCount(50);
        listenerContainer.setMessageListener(receiver);

        return listenerContainer;
    }

    @Qualifier("consumer-workers")
    @Bean
    public TaskExecutor taskExecutor() {
        final ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(environment.getProperty("executors.listener.core-size", Integer.class, 20));
        taskExecutor.setMaxPoolSize(environment.getProperty("executors.listener.max-pool-size", Integer.class, 200));
        taskExecutor.setThreadGroupName("rabbit-listeners");
        taskExecutor.setThreadNamePrefix("rabbit-mq-");
        taskExecutor.setAllowCoreThreadTimeOut(true);
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        taskExecutor.setQueueCapacity(environment.getProperty("executors.listener.queue-capacity", Integer.class, 1000));
        return taskExecutor;
    }


    public static void main(String[] args) {
        SpringApplication.run(BulksmsV2Application.class, args);
    }

}
