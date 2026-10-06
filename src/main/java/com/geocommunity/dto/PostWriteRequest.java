package com.geocommunity.dto;

import com.geocommunity.entity.Post;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** 公开写接口白名单，不接收作者、状态、时间与系统计数。 */
@Data
public class PostWriteRequest {
    @NotBlank(message = "标题不能为空") @Size(max = 50, message = "标题最多50字")
    private String title;
    @NotBlank(message = "内容不能为空") @Size(max = 10000, message = "内容最多10000字")
    private String content;
    @NotNull(message = "请选择分类") @Positive
    private Integer categoryId;
    @Size(max = 9, message = "最多9张图片")
    private List<@NotBlank @Size(max = 2048) String> images;
    @DecimalMin("-180") @DecimalMax("180") private Double longitude;
    @DecimalMin("-90") @DecimalMax("90") private Double latitude;
    @AssertTrue(message = "经纬度必须同时提供")
    public boolean isLocationComplete() { return (longitude == null) == (latitude == null); }
    public Post toPost() {
        Post p = new Post();
        p.setTitle(title); p.setContent(content); p.setCategoryId(categoryId);
        p.setImages(images); p.setLongitude(longitude); p.setLatitude(latitude);
        return p;
    }
}
