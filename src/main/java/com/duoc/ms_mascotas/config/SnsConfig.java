package com.duoc.ms_mascotas.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
public class SnsConfig {

    @Bean(destroyMethod = "close")
    SnsClient snsClient(
            @Value("${aws.region}") String region,
            @Value("${aws.endpoint-override:}") String endpointOverride) {
        var builder = SnsClient.builder().region(Region.of(region));

        // Solo se usa en Docker local, para apuntar a LocalStack en vez de AWS
        // real (así no hace falta sesión del lab para probar la mensajería).
        // Credenciales propias (no DefaultCredentialsProvider): S3 sigue
        // necesitando las credenciales reales del lab en las mismas
        // variables de entorno, y no deben mezclarse con las falsas de
        // LocalStack.
        if (!endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
