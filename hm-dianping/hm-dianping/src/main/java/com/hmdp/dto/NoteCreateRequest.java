package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * Create note request from the publishing dialog.
 */
@Data
public class NoteCreateRequest {
    private Long shopId;
    private String title;
    private String images;
    private String videoUrl;
    private String contentType;
    private String tags;
    private String content;
    private List<Long> productIds;
}
