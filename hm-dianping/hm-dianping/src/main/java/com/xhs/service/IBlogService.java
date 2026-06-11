package com.xhs.service;

import com.xhs.dto.Result;
import com.xhs.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
    /**
     * 查询热门博文
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 根据id查询博文
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 博文点赞
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查询博文点赞名单
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存博文
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 作者编辑自己的笔记。
     */
    Result updateOwnBlog(Long id, Blog blog);

    /**
     * 作者删除自己的笔记。
     */
    Result deleteOwnBlog(Long id);

    /**
     * 查询关注博文
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
