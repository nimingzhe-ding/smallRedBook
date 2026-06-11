package com.xhs.dto;

import com.xhs.entity.MallProduct;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Note detail DTO for the Xiaohongshu-style drawer.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NoteDetailDTO extends NoteDTO {
    private ContentShopDTO shop;
    private List<MallProduct> products;
    private CreatorGrowthDTO creatorGrowth;
    private List<ContentNoteDTO> relatedNotes;
}
