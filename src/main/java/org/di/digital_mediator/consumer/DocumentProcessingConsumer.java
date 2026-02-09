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
        log.info("📥 Received document for processing: {} (ID: {}) from case {} uploaded by {}",
                message.getOriginalFileName(),
                message.getCaseFileId(),
                message.getCaseNumber(),
                message.getUserEmail());

        try {
            // Download file from MinIO
            log.info("📂 Downloading file from MinIO: {}", message.getFileUrl());
            InputStream fileStream = minioService.downloadFile(message.getFileUrl());

            log.info("🤖 Starting AI processing for file: {} (ID: {}) in case {}",
                    message.getOriginalFileName(),
                    message.getCaseFileId(),
                    message.getCaseNumber());

            aiProcessingService.processDocument(
                    fileStream,
                    message.getOriginalFileName(),
                    message.getCaseNumber(),
                    message
            );

            log.info("✅ AI processing initiated successfully for document {} (ID: {}) in case {}",
                    message.getOriginalFileName(),
                    message.getCaseFileId(),
                    message.getCaseNumber());

        } catch (Exception e) {
            log.error("❌ Failed to initiate processing for document {} (ID: {}) in case {}: {}",
                    message.getOriginalFileName(),
                    message.getCaseFileId(),
                    message.getCaseNumber(),
                    e.getMessage(),
                    e);

            aiProcessingService.notifyFailure(message, e.getMessage(), 0);
        }
    }
}