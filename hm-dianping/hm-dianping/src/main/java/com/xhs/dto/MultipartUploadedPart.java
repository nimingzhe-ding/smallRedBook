package com.xhs.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultipartUploadedPart {
    private Integer partNumber;
    @JsonProperty("eTag")
    @JsonAlias({"etag", "ETag"})
    private String eTag;
    private Long size;
}
