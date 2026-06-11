package com.xhs.dto;

import lombok.Data;

@Data
public class CompleteUploadRequest {
    private String objectName;
    private String contentType;
    private Long size;
}
