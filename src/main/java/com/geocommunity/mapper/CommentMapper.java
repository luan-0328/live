package com.geocommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geocommunity.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
    // 保留有存活回复的删除根评论，展示占位，避免整楼回复消失。
    @org.apache.ibatis.annotations.Select("SELECT c.* FROM comment c WHERE c.post_id=#{postId} AND c.parent_id IS NULL "
        + "AND (c.status=1 OR EXISTS(SELECT 1 FROM comment r WHERE r.parent_id=c.id AND r.status=1)) "
        + "ORDER BY c.created_at,c.id")
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Comment> selectRoots(
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Comment> page,
        @org.apache.ibatis.annotations.Param("postId") Long postId);

}
