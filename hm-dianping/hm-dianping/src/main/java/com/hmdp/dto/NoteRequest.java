package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * Frontend note write request. The persistence layer can still reuse tb_blog,
 * but controllers should expose note semantics instead of Blog entities.
 */
@Data
public class NoteRequest {
    private Long shopId;
    private String title;
    private String images;
    private String videoUrl;
    private String contentType;
    private String tags;
    private String content;
    private List<Long> productIds;
}
