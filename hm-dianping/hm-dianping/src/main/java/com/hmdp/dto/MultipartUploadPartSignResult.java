package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultipartUploadPartSignResult {
    private String objectName;
    private String uploadId;
    private Integer partNumber;
    private String uploadUrl;
    private String method;
    private Instant expireAt;
    private Map<String, String> headers;
}
