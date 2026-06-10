package com.hmdp.service;

import com.hmdp.dto.ContentSearchResult;
import com.hmdp.dto.SearchIndexRebuildResult;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.entity.VideoDanmaku;

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
