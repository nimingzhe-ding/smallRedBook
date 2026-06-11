package com.xhs.dto;

import lombok.Data;

@Data
public class MultipartUploadPartSignRequest {
    private String objectName;
    private String uploadId;
    private Integer partNumber;
    private String contentType;
    private Long partSize;
}
