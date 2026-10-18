package com.duoc.ms_mascotas.messaging;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * Publica un evento en el topic SNS compartido ("sanos-eventos"), con el
 * tipo de evento como atributo del mensaje — es lo que usan las
 * suscripciones SQS para filtrar y que cada cola solo reciba lo suyo.
 */
@Component
public class SnsEventPublisher {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String topicArn;

    public SnsEventPublisher(SnsClient snsClient, ObjectMapper objectMapper,
            @Value("${aws.sns.topic-arn}") String topicArn) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.topicArn = topicArn;
    }

    public void publicar(String tipoEvento, Object payload) {
        String mensaje;
        try {
            mensaje = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + tipoEvento, e);
        }

        snsClient.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(mensaje)
                .messageAttributes(Map.of("tipoEvento", MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(tipoEvento)
                        .build()))
                .build());
    }
}
