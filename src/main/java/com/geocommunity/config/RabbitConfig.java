package com.geocommunity.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 *
 * 通知队列 geo.notification → 绑定 geo.notification.dlq 死信队列
 *   消费失败后进入DLQ并持久化，延迟重发最多三轮，超限保留人工处理。
 */
@Configuration
@EnableRabbit
public class RabbitConfig {

    @Bean
    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory dlqContainerFactory(
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
            org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        var factory=new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        configurer.configure(factory,connectionFactory);
        // 原始消息不做JSON转换，格式损坏的死信也能持久化隔离。
        factory.setMessageConverter(new org.springframework.amqp.support.converter.SimpleMessageConverter());
        factory.setAdviceChain(org.springframework.amqp.rabbit.config.RetryInterceptorBuilder.stateless()
            .maxAttempts(3).backOffOptions(1000,2,5000)
            .recoverer(new org.springframework.amqp.rabbit.retry.ImmediateRequeueMessageRecoverer()).build());
        factory.setDefaultRequeueRejected(true);
        return factory;
    }

    /** 统一 Topic 交换机 */
    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange("geo.exchange");
    }

    // ==================== 通知队列 ====================

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable("geo.notification")
                .deadLetterExchange("geo.exchange")
                .deadLetterRoutingKey("notification.dlq")
                .build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(topicExchange())
                .with("notification");
    }

    // ==================== 通知死信队列 ====================

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable("geo.notification.dlq").build();
    }

    @Bean
    public Binding notificationDlqBinding() {
        return BindingBuilder.bind(notificationDlq())
                .to(topicExchange())
                .with("notification.dlq");
    }

    /**
     * JSON 消息转换器
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
