package com.invoide.invoices3.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    // Nombres de la cola e intercambio principal (hardcodeados como en tu ejemplo)
    public static final String MAIN_QUEUE = "invoice.upload.queue";
    public static final String MAIN_EXCHANGE = "invoice.upload.exchange";

    // Nombres de la cola e intercambio de carta muerta (DLQ/DLX)
    public static final String DEAD_LETTER_QUEUE = "invoice.upload.dead-letter-queue";
    public static final String DEAD_LETTER_EXCHANGE = "invoice.upload.dead-letter-exchange";

    // Bean para el convertidor de mensajes JSON
    @Bean
    Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Bean para la ConnectionFactory (hardcodeado como en tu ejemplo)
    @Bean
    CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost("35.172.7.15"); // Hardcodeado como en el ejemplo
        factory.setPort(5672);       // Hardcodeado como en el ejemplo
        factory.setUsername("guest"); // Hardcodeado como en el ejemplo
        factory.setPassword("guest"); // Hardcodeado como en el ejemplo
        return factory;
    }

    // --- Configuración de la Cola Principal y su Intercambio ---

    // Bean para el Intercambio Directo Principal
    @Bean
    DirectExchange mainExchange() {
        return new DirectExchange(MAIN_EXCHANGE);
    }

    // Bean para la Cola Principal (configurada para DLQ)
    @Bean
    public Queue invoiceUploadQueue() {
        Map<String, Object> args = new HashMap<>();
        // Argumentos para configurar el Dead Letter Exchange (DLX) y la Dead Letter Routing Key
        args.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
        args.put("x-dead-letter-routing-key", DEAD_LETTER_QUEUE); // Routing key de la DLQ
        // Cola duradera: true, exclusiva: false, auto-eliminar: false
        return new Queue(MAIN_QUEUE, true, false, false, args);
    }

    // Bean para el Binding entre la Cola Principal y el Intercambio Principal
    @Bean
    Binding mainBinding(Queue invoiceUploadQueue, DirectExchange mainExchange) {
        // Enlaza la cola principal al intercambio principal con una routing key vacía, como en tu ejemplo
        // Nota: Esto implica que el productor debe enviar mensajes al MAIN_EXCHANGE con una routing key vacía.
        return BindingBuilder.bind(invoiceUploadQueue).to(mainExchange).with("");
    }

    // --- Configuración de la Cola de Carta Muerta (DLQ) y su Intercambio (DLX) ---

    // Bean para el Intercambio Directo de Carta Muerta (DLX)
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    // Bean para la Cola de Carta Muerta (DLQ)
    @Bean
    public Queue deadLetterQueue() {
        // Cola duradera: true, exclusiva: false, auto-eliminar: false
        return new Queue(DEAD_LETTER_QUEUE, true, false, false);
    }

    // Bean para el Binding entre la Cola de Carta Muerta y el Intercambio de Carta Muerta
    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        // La DLQ se enlaza al DLX con el nombre de la DLQ como routing key
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_LETTER_QUEUE);
    }
}