package com.Edify.Backend.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UploadDocumentRequest {

    @NotBlank
    private String title;
}
