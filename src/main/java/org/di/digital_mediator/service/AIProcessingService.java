package org.di.digital_mediator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital_mediator.config.RabbitMQConfig;
import org.di.digital_mediator.dto.DocumentProcessingMessage;
import org.di.digital_mediator.dto.ProcessingResultMessage;
import org.di.digital_mediator.dto.ProcessingStatus;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIProcessingService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${ai.model.url}")
    private String aiModelUrl;

    public String processDocument(InputStream fileStream, String fileName, Long caseFileId) {
        try {
            log.info("Processing file {} with AI model...", fileName);

            // TODO: Замени на реальный вызов твоей AI модели
            Thread.sleep(60000); // Симуляция 60 секунд обработки
            return "Processing completed successfully";

        } catch (Exception e) {
            log.error("AI processing error for file {}: {}", fileName, e.getMessage());
            throw new RuntimeException("AI processing failed", e);
        }
    }

    /**
     * Уведомить что началась обработка
     */
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

        log.info("Sent PROCESSING notification for file {} from user {}",
                originalMessage.getCaseFileId(), originalMessage.getUserEmail());
    }

    /**
     * Уведомить об успешной обработке
     */
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

        log.info("Sent COMPLETED notification for file {} from user {} ({}s)",
                originalMessage.getCaseFileId(), originalMessage.getUserEmail(), durationSeconds);
    }

    /**
     * Уведомить об ошибке
     */
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

        log.error("Sent FAILED notification for file {} from user {} ({}s): {}",
                originalMessage.getCaseFileId(), originalMessage.getUserEmail(), durationSeconds, errorMessage);
    }

    /**
     * Единая точка отправки уведомлений с retry логикой
     */
    private void sendNotification(ProcessingResultMessage message) {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.RESULT_EXCHANGE,
                        getRoutingKey(message.getStatus()),
                        message
                );
                return;

            } catch (Exception e) {
                retryCount++;
                log.error("Failed to send notification (attempt {}/{}): {}",
                        retryCount, maxRetries, e.getMessage());

                if (retryCount >= maxRetries) {
                    log.error("Failed to send notification after {} attempts. Message will be lost: {}",
                            maxRetries, message);
                    // TODO: Можно сохранить в БД для повторной отправки позже
                } else {
                    try {
                        Thread.sleep(1000 * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private String getRoutingKey(ProcessingStatus status) {
        return switch (status) {
            case PROCESSING -> RabbitMQConfig.RESULT_PROCESSING_ROUTING_KEY;
            case COMPLETED -> RabbitMQConfig.RESULT_SUCCESS_ROUTING_KEY;
            case FAILED -> RabbitMQConfig.RESULT_FAILURE_ROUTING_KEY;
        };
    }
}