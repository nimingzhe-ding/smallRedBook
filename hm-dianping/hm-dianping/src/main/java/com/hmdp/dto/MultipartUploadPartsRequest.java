package com.hmdp.dto;

import lombok.Data;

@Data
public class MultipartUploadPartsRequest {
    private String objectName;
    private String uploadId;
}
