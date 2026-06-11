package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultipartUploadInitResult {
    private String objectName;
    private String uploadId;
    private String url;
    private String contentType;
    private Long size;
    private Long partSize;
    private Integer partCount;
    private Instant expireAt;
    private List<MultipartUploadedPart> uploadedParts;
}
