package com.geocommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.dto.PostVO;
import com.geocommunity.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface PostMapper extends BaseMapper<Post> {
    @Select("SELECT * FROM post WHERE id = #{id} AND status = 1 FOR UPDATE")
    Post selectActiveForUpdate(@Param("id") Long id);

    Page<Post> selectAdminPosts(Page<Post> page, @Param("authorId") Long authorId,
                               @Param("categoryId") Integer categoryId);

    Page<PostVO> selectPostList(Page<PostVO> page,
                                @Param("categoryId") Integer categoryId,
                                @Param("sort") String sort,
                                @Param("authorId") Long authorId);

    Page<PostVO> selectNearbyPosts(Page<PostVO> page,
                                   @Param("longitude") Double longitude,
                                   @Param("latitude") Double latitude,
                                   @Param("radius") Integer radius,
                                   @Param("categoryId") Integer categoryId);

    Page<PostVO> searchPosts(Page<PostVO> page,
                             @Param("keyword") String keyword,
                             @Param("categoryId") Integer categoryId);

    Page<PostVO> selectFavoritePosts(Page<PostVO> page,
                                     @Param("userId") Long userId);

    Post selectPostDetail(@Param("id") Long id);
}
