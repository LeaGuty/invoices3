package com.invoide.invoide.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Value("${invoide.rabbitmq.queue-name}")
    private String queueName;

    @Value("${invoide.rabbitmq.dead-letter.queue-name}")
    private String deadLetterQueueName;

    @Value("${invoide.rabbitmq.dead-letter.exchange-name}")
    private String deadLetterExchangeName;

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(deadLetterExchangeName);
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(deadLetterQueueName, true); // Cola duradera
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(deadLetterQueueName); // Routing key es el nombre de la cola
    }

    @Bean
    public Queue invoiceUploadQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", deadLetterExchangeName);
        args.put("x-dead-letter-routing-key", deadLetterQueueName);
        return new Queue(queueName, true, false, false, args); // 'true' para duradera, 'args' para DLQ
    }
}