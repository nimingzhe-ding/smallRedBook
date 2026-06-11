package com.xhs.dto;

import lombok.Data;

@Data
public class MultipartUploadInitRequest {
    private String fileName;
    private String contentType;
    private Long size;
    private Long partSize;
}
