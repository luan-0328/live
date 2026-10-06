package com.geocommunity.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostVO {
    private Long postId;
    private String title;
    private String content;
    private String categoryName;
    private AuthorVO author;
    private List<String> images;    // JSON array → TypeHandler 自动转换
    private Integer likeCount;
    private Integer commentCount;
    private Integer viewCount;
    private Double distance;        // 附近接口返回距离(km)
    private LocalDateTime createTime;
    private Boolean isLiked;
    private Boolean isFavorited;
}
