package com.xhs.service;

import com.xhs.dto.ContentSearchResult;
import com.xhs.dto.SearchIndexRebuildResult;
import com.xhs.entity.Blog;
import com.xhs.entity.User;
import com.xhs.entity.VideoDanmaku;

public interface SearchIndexService {
    boolean isEnabled();

    boolean isAvailable();

    ContentSearchResult search(String keyword, int pageNo);

    SearchIndexRebuildResult rebuildAll();

    boolean indexBlog(Blog blog);

    boolean deleteBlog(Long blogId);

    boolean indexUser(User user);

    boolean deleteUser(Long userId);

    boolean indexDanmaku(VideoDanmaku danmaku);

    boolean deleteDanmaku(Long danmakuId);
}
