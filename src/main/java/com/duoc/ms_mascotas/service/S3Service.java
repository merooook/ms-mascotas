package com.duoc.ms_mascotas.service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final Duration URL_DURATION = Duration.ofMinutes(5);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Pattern FILE_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}\\.(jpg|jpeg|png|webp)$");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private final S3Presigner s3Presigner;

    public Map<String, String> generarUrlFirmada(String fileName, String contentType, long fileSize) {
        String normalizedFileName = validarArchivo(fileName, contentType, fileSize);
        String key = "mascotas/" + UUID.randomUUID() + "-" + normalizedFileName;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength(fileSize)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(request -> request
                .signatureDuration(URL_DURATION)
                .putObjectRequest(objectRequest));

        return Map.of("url", presignedRequest.url().toString(), "key", key);
    }

    private String validarArchivo(String fileName, String contentType, long fileSize) {
        if (fileName == null || contentType == null) {
            throw new IllegalArgumentException("El nombre y tipo del archivo son requeridos");
        }
        if (fileSize <= 0 || fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("La imagen debe pesar entre 1 byte y 5 MB");
        }

        String normalizedFileName = fileName.trim();
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        if (!FILE_NAME.matcher(normalizedFileName).matches()
                || !ALLOWED_CONTENT_TYPES.contains(normalizedContentType)
                || !contentTypeMatchesExtension(normalizedFileName, normalizedContentType)) {
            throw new IllegalArgumentException("Solo se permiten imágenes JPG, PNG o WEBP");
        }
        return normalizedFileName;
    }

    private boolean contentTypeMatchesExtension(String fileName, String contentType) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ((extension.equals("jpg") || extension.equals("jpeg")) && contentType.equals("image/jpeg"))
                || (extension.equals("png") && contentType.equals("image/png"))
                || (extension.equals("webp") && contentType.equals("image/webp"));
    }
}
