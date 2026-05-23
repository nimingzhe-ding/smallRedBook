package com.hmdp.service;

import com.hmdp.dto.ContentSearchResult;
import com.hmdp.dto.SearchIndexRebuildResult;
import com.hmdp.entity.Blog;

public interface SearchIndexService {
    boolean isEnabled();

    boolean isAvailable();

    ContentSearchResult search(String keyword, int pageNo);

    SearchIndexRebuildResult rebuildAll();

    void indexBlog(Blog blog);

    void deleteBlog(Long blogId);
}
