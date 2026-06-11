package com.hmdp.dto;

import lombok.Data;

@Data
public class MultipartUploadAbortRequest {
    private String objectName;
    private String uploadId;
}
