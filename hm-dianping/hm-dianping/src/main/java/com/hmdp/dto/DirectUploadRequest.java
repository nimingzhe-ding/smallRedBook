package com.hmdp.dto;

import lombok.Data;

@Data
public class DirectUploadRequest {
    private String fileName;
    private String contentType;
    private Long size;
}
