package com.xhs.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * Frontend comment request for a note detail page.
 */
@Data
public class NoteCommentRequest {
    @JsonAlias("blogId")
    private Long noteId;
    private String content;
    private Long parentId;
    private Long answerId;
}
