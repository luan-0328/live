package com.geocommunity.dto;

import lombok.Data;

@Data
public class RankVO {
    private int rank;
    private Long postId;
    private String title;
    private Double score;
    private String authorNickname;
}
