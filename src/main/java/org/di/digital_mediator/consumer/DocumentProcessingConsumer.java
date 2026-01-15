package org.di.digital_mediator.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital_mediator.config.RabbitMQConfig;
import org.di.digital_mediator.dto.DocumentProcessingMessage;
import org.di.digital_mediator.service.AIProcessingService;
import org.di.digital_mediator.service.MinioService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessingConsumer {

    private final MinioService minioService;
    private final AIProcessingService aiProcessingService;

    @RabbitListener(queues = RabbitMQConfig.DOCUMENT_QUEUE)
    public void processDocument(DocumentProcessingMessage message) {
        log.info("Received document for processing: {} from case {}",
                message.getOriginalFileName(), message.getCaseNumber());

        long startTime = System.currentTimeMillis();

        try {
            log.info("Downloading file from Minio: {}", message.getFileUrl());
            InputStream fileStream = minioService.downloadFile(message.getFileUrl());

            aiProcessingService.notifyProcessing(message);

            // 2. Отправить в AI модель (займет 3-4 минуты)
            log.info("Sending file to AI model for processing...");
            String result = aiProcessingService.processDocument(
                    fileStream,
                    message.getOriginalFileName(),
                    message.getCaseFileId()
            );

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("Successfully processed document {} in {} seconds. Result: {}",
                    message.getOriginalFileName(), duration, result);

            aiProcessingService.notifyCompletion(message, result, duration);

        } catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.error("Failed to process document {} after {} seconds: {}",
                    message.getOriginalFileName(), duration, e.getMessage(), e);

            aiProcessingService.notifyFailure(message, e.getMessage(), duration);
            throw new RuntimeException("Document processing failed", e);
        }
    }
}