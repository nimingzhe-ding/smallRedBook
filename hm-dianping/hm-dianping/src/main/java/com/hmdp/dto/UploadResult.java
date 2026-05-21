package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult {
    private String objectName;
    private String url;
    private String contentType;
    private Long size;
}
