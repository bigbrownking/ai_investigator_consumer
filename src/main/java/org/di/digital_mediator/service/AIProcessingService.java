package org.di.digital_mediator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital_mediator.config.RabbitMQConfig;
import org.di.digital_mediator.dto.DocumentProcessingMessage;
import org.di.digital_mediator.dto.ProcessingResultMessage;
import org.di.digital_mediator.dto.ProcessingStatus;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIProcessingService {

    private final RabbitTemplate rabbitTemplate;
    private final WebClient webClient;

    @Value("${ai.model.url}")
    private String aiModelUrl;

    @Value("${index.control.port}")
    private String port;

    @Value("${spring.rabbitmq.result.processing.routing-key}")
    public String RESULT_PROCESSING_ROUTING_KEY;
    @Value("${spring.rabbitmq.result.pending.routing-key}")
    public String RESULT_PENDING_ROUTING_KEY;
    @Value("${spring.rabbitmq.result.success.routing-key}")
    public String RESULT_SUCCESS_ROUTING_KEY;
    @Value("${spring.rabbitmq.result.failure.routing-key}")
    public String RESULT_FAILURE_ROUTING_KEY;

    @Value("${spring.rabbitmq.result.exchange}")
    public String RESULT_EXCHANGE = "document.result.exchange";
    public void processDocument(InputStream fileStream, String fileName,
                                String caseNumber, DocumentProcessingMessage originalMessage) {
        long startTime = System.currentTimeMillis();

        notifyProcessing(originalMessage);

        try {
            byte[] fileBytes = fileStream.readAllBytes();

            log.info("AI processing started for file {} (ID: {}) in case {}",
                    fileName, originalMessage.getCaseFileId(), caseNumber);

            String result = webClient.post()
                    .uri(aiModelUrl + ":" + port + "/workspaces/" + caseNumber + "/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(createMultipartBody(fileBytes, fileName))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("AI processing completed for file {} (ID: {}) in case {} after {}s",
                    fileName, originalMessage.getCaseFileId(), caseNumber, duration);

            notifyCompletion(originalMessage, result, duration);

        } catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.error("AI processing failed for file {} (ID: {}) in case {} after {}s: {}",
                    fileName, originalMessage.getCaseFileId(), caseNumber, duration, e.getMessage());

            notifyFailure(originalMessage, e.getMessage(), duration);
        }
    }

    private MultiValueMap<String, Object> createMultipartBody(byte[] fileBytes, String fileName) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        body.add("files", fileResource);
        return body;
    }

    public void notifyProcessing(DocumentProcessingMessage originalMessage) {
        sendNotification(ProcessingResultMessage.builder()
                .caseFileId(originalMessage.getCaseFileId())
                .caseNumber(originalMessage.getCaseNumber())
                .fileName(originalMessage.getOriginalFileName())
                .userEmail(originalMessage.getUserEmail())
                .status(ProcessingStatus.PROCESSING)
                .result(null)
                .errorMessage(null)
                .timestamp(LocalDateTime.now())
                .build());

        log.info("Sent PROCESSING notification for file {} (ID: {}) in case {} from user {}",
                originalMessage.getOriginalFileName(),
                originalMessage.getCaseFileId(),
                originalMessage.getCaseNumber(),
                originalMessage.getUserEmail());
    }

    public void notifyCompletion(DocumentProcessingMessage originalMessage, String result, long durationSeconds) {
        sendNotification(ProcessingResultMessage.builder()
                .caseFileId(originalMessage.getCaseFileId())
                .caseNumber(originalMessage.getCaseNumber())
                .fileName(originalMessage.getOriginalFileName())
                .userEmail(originalMessage.getUserEmail())
                .status(ProcessingStatus.COMPLETED)
                .result(result)
                .errorMessage(null)
                .timestamp(LocalDateTime.now())
                .processingDurationSeconds(durationSeconds)
                .build());

        log.info("Sent COMPLETED notification for file {} (ID: {}) in case {} from user {} ({}s)",
                originalMessage.getOriginalFileName(),
                originalMessage.getCaseFileId(),
                originalMessage.getCaseNumber(),
                originalMessage.getUserEmail(),
                durationSeconds);
    }

    public void notifyFailure(DocumentProcessingMessage originalMessage, String errorMessage, long durationSeconds) {
        sendNotification(ProcessingResultMessage.builder()
                .caseFileId(originalMessage.getCaseFileId())
                .caseNumber(originalMessage.getCaseNumber())
                .fileName(originalMessage.getOriginalFileName())
                .userEmail(originalMessage.getUserEmail())
                .status(ProcessingStatus.FAILED)
                .result(null)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .processingDurationSeconds(durationSeconds)
                .build());

        log.error("Sent FAILED notification for file {} (ID: {}) in case {} from user {} ({}s): {}",
                originalMessage.getOriginalFileName(),
                originalMessage.getCaseFileId(),
                originalMessage.getCaseNumber(),
                originalMessage.getUserEmail(),
                durationSeconds,
                errorMessage);
    }

    public void notifyPending(DocumentProcessingMessage originalMessage) {
        sendNotification(ProcessingResultMessage.builder()
                .caseFileId(originalMessage.getCaseFileId())
                .caseNumber(originalMessage.getCaseNumber())
                .fileName(originalMessage.getOriginalFileName())
                .userEmail(originalMessage.getUserEmail())
                .status(ProcessingStatus.PENDING)
                .result(null)
                .errorMessage(null)
                .timestamp(LocalDateTime.now())
                .build());

        log.info("Sent PENDING notification for file {} (ID: {}) in case {} from user {}",
                originalMessage.getOriginalFileName(),
                originalMessage.getCaseFileId(),
                originalMessage.getCaseNumber(),
                originalMessage.getUserEmail());
    }

    private void sendNotification(ProcessingResultMessage message) {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                rabbitTemplate.convertAndSend(
                        RESULT_EXCHANGE,
                        getRoutingKey(message.getStatus()),
                        message
                );

                log.debug("Successfully sent {} notification to exchange {} with routing key {}",
                        message.getStatus(),
                        RESULT_EXCHANGE,
                        getRoutingKey(message.getStatus()));

                return;

            } catch (Exception e) {
                retryCount++;
                log.error("Failed to send {} notification for file {} in case {} (attempt {}/{}): {}",
                        message.getStatus(),
                        message.getCaseFileId(),
                        message.getCaseNumber(),
                        retryCount,
                        maxRetries,
                        e.getMessage());

                if (retryCount >= maxRetries) {
                    log.error("Failed to send notification after {} attempts. Message will be lost: case={}, fileId={}, status={}",
                            maxRetries,
                            message.getCaseNumber(),
                            message.getCaseFileId(),
                            message.getStatus());
                } else {
                    try {
                        Thread.sleep(1000L * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry sleep interrupted for file {} in case {}",
                                message.getCaseFileId(),
                                message.getCaseNumber());
                    }
                }
            }
        }
    }

    private String getRoutingKey(ProcessingStatus status) {
        return switch (status) {
            case PENDING -> RESULT_PENDING_ROUTING_KEY;
            case PROCESSING -> RESULT_PROCESSING_ROUTING_KEY;
            case COMPLETED -> RESULT_SUCCESS_ROUTING_KEY;
            case FAILED -> RESULT_FAILURE_ROUTING_KEY;
        };
    }
}