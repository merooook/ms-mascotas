package com.duoc.ms_mascotas.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publica un evento cada vez que se reporta una mascota con ubicación;
 * ms-alertas lo consume para crear la Alerta geográfica correspondiente.
 * Mismo exchange compartido ("sanos.eventos") que usa ms-alertas para el
 * correo de contacto, para no tener un broker lógico distinto por evento.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "sanos.eventos";
    public static final String COLA_MASCOTA_REPORTADA = "cola.mascota.reportada";
    public static final String ROUTING_KEY_MASCOTA_REPORTADA = "mascota.reportada";

    private static final String DLX = "sanos.eventos.dlx";
    private static final String COLA_FALLIDOS = "cola.mascota.reportada.fallidos";

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    TopicExchange eventosExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    TopicExchange eventosDlx() {
        return new TopicExchange(DLX);
    }

    @Bean
    Queue colaMascotaReportada() {
        return QueueBuilder.durable(COLA_MASCOTA_REPORTADA)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_MASCOTA_REPORTADA)
                .build();
    }

    @Bean
    Queue colaMascotaReportadaFallidos() {
        return QueueBuilder.durable(COLA_FALLIDOS).build();
    }

    @Bean
    Binding bindingMascotaReportada(Queue colaMascotaReportada, TopicExchange eventosExchange) {
        return BindingBuilder.bind(colaMascotaReportada).to(eventosExchange).with(ROUTING_KEY_MASCOTA_REPORTADA);
    }

    @Bean
    Binding bindingMascotaReportadaFallidos(Queue colaMascotaReportadaFallidos, TopicExchange eventosDlx) {
        return BindingBuilder.bind(colaMascotaReportadaFallidos).to(eventosDlx)
                .with(ROUTING_KEY_MASCOTA_REPORTADA);
    }
}
