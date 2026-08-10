package org.di.digital_mediator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadResponse {
    private String status;
    private String message;

    @JsonProperty("track_id")
    private String trackId;

    @JsonProperty("document_id")
    private String documentId;

    private ClassificationResult classification;
    private AssessmentResult assessment;
}