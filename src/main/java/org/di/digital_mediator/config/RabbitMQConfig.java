package org.di.digital_mediator.config;

import jakarta.validation.Valid;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.document.queue}")
    public String DOCUMENT_QUEUE;
    @Value("${spring.rabbitmq.document.exchange}")
    public String DOCUMENT_EXCHANGE;
    @Value("${spring.rabbitmq.document.routing-key}")
    public String DOCUMENT_ROUTING_KEY;

    @Value("${spring.rabbitmq.dlq.queue}")
    public String DLQ_QUEUE;
    @Value("${spring.rabbitmq.dlq.exchange}")
    public String DLQ_EXCHANGE;
    @Value("${spring.rabbitmq.dlq.routing-key}")
    public String DLQ_ROUTING_KEY;
    @Value("${spring.rabbitmq.result.queue}")
    public String RESULT_QUEUE;
    @Value("${spring.rabbitmq.result.exchange}")
    public String RESULT_EXCHANGE = "document.result.exchange";
    @Value("${spring.rabbitmq.result.processing.routing-key}")
    public String RESULT_PROCESSING_ROUTING_KEY;
    @Value("${spring.rabbitmq.result.pending.routing-key}")
    public String RESULT_PENDING_ROUTING_KEY;
    @Value("${spring.rabbitmq.result.success.routing-key}")
    public String RESULT_SUCCESS_ROUTING_KEY;
    @Value("${spring.rabbitmq.result.failure.routing-key}")
    public String RESULT_FAILURE_ROUTING_KEY;

    @Bean
    public Queue documentQueue() {
        return QueueBuilder.durable(DOCUMENT_QUEUE)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange documentExchange() {
        return new DirectExchange(DOCUMENT_EXCHANGE);
    }

    @Bean
    public Binding documentBinding(Queue documentQueue, DirectExchange documentExchange) {
        return BindingBuilder
                .bind(documentQueue)
                .to(documentExchange)
                .with(DOCUMENT_ROUTING_KEY);
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE);
    }

    @Bean
    public Binding dlqBinding(Queue dlqQueue, DirectExchange dlqExchange) {
        return BindingBuilder
                .bind(dlqQueue)
                .to(dlqExchange)
                .with(DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue resultQueue() {
        return QueueBuilder.durable(RESULT_QUEUE).build();
    }

    @Bean
    public DirectExchange resultExchange() {
        return new DirectExchange(RESULT_EXCHANGE);
    }

    @Bean
    public Binding resultProcessingBinding(Queue resultQueue, DirectExchange resultExchange) {
        return BindingBuilder
                .bind(resultQueue)
                .to(resultExchange)
                .with(RESULT_PROCESSING_ROUTING_KEY);
    }
    @Bean
    public Binding resultPendingBinding(Queue resultQueue, DirectExchange resultExchange) {
        return BindingBuilder
                .bind(resultQueue)
                .to(resultExchange)
                .with(RESULT_PENDING_ROUTING_KEY);
    }

    @Bean
    public Binding resultSuccessBinding(Queue resultQueue, DirectExchange resultExchange) {
        return BindingBuilder
                .bind(resultQueue)
                .to(resultExchange)
                .with(RESULT_SUCCESS_ROUTING_KEY);
    }

    @Bean
    public Binding resultFailureBinding(Queue resultQueue, DirectExchange resultExchange) {
        return BindingBuilder
                .bind(resultQueue)
                .to(resultExchange)
                .with(RESULT_FAILURE_ROUTING_KEY);
    }

    @Bean
    @SuppressWarnings("deprecation")
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        return factory;
    }
}