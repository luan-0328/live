package com.geocommunity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.dto.PostVO;
import com.geocommunity.entity.Post;

public interface PostService {

    Page<PostVO> listPosts(Integer categoryId, String sort, int page, int size, Long authorId);

    Page<PostVO> nearbyPosts(Double longitude, Double latitude, Integer radius,
                             Integer categoryId, int page, int size);

    Page<PostVO> searchPosts(String keyword, Integer categoryId, int page, int size);

    Post getDetail(Long id);

    Post createPost(Post post, Long userId);

    void updatePost(Long id, Post post, Long userId);

    void deletePost(Long id, Long userId);

    void likePost(Long postId, Long userId);

    void unlikePost(Long postId, Long userId);

    void favoritePost(Long postId, Long userId);

    void unfavoritePost(Long postId, Long userId);

    boolean isLiked(Long postId, Long userId);
    boolean isFavorited(Long postId, Long userId);
    /** 管理员和举报共用的删除入口，返回是否发生状态转换。 */
    boolean removePost(Long postId);

    /** 清除帖子列表缓存（用户更新头像/昵称等影响作者信息时调用） */
    void evictPostListCache();

    /** 失效该作者所有帖子的详情缓存（详情缓存内嵌作者昵称/头像，TTL 30~40min，需主动清） */
    void evictPostDetailByAuthor(Long authorId);

    /** 失效该分类下所有帖子的详情缓存（详情缓存内嵌分类名，TTL 30~40min，需主动清） */
    void evictPostDetailByCategory(Integer categoryId);

    /** 帖子删除后的完整清理：热榜/详情/点赞收藏 Redis 集合 + DB 级联删除 + 列表缓存 */
    void cleanupPostRelations(Long postId);
}
