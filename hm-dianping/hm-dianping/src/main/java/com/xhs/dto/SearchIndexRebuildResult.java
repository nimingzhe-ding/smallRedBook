package com.xhs.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SearchIndexRebuildResult {
    private boolean enabled;
    private boolean available;
    private int notes;
    private int products;
    private int shops;
    private int topics;
    private String message;
}
