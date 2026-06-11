package com.xhs.dto;

import lombok.Data;

import java.util.List;

@Data
public class MultipartUploadCompleteRequest {
    private String objectName;
    private String uploadId;
    private String contentType;
    private Long size;
    private List<MultipartUploadedPart> parts;
}
