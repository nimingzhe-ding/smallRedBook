package com.xhs.dto;

import lombok.Data;

@Data
public class MultipartUploadPartsRequest {
    private String objectName;
    private String uploadId;
}
