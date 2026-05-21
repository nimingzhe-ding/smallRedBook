package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * Update note request. Kept separate from create so later edit-only rules
 * can evolve without changing the publish API contract.
 */
@Data
public class NoteUpdateRequest {
    private Long shopId;
    private String title;
    private String images;
    private String videoUrl;
    private String contentType;
    private String tags;
    private String content;
    private List<Long> productIds;
}
