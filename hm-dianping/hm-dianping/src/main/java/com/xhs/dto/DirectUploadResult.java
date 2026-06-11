package com.xhs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DirectUploadResult {
    private String objectName;
    private String uploadUrl;
    private String url;
    private String method;
    private String contentType;
    private Long size;
    private Instant expireAt;
    private Map<String, String> headers;
}
