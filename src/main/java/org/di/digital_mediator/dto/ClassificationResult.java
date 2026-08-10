package org.di.digital_mediator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ClassificationResult {
    private String status;

    @JsonProperty("document_type")
    private String documentType;
}