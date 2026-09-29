package com.wtlx.dto;

import lombok.Data;

@Data
public class ScenicSpotQuery {
    private Integer page = 1;
    private Integer pageSize = 10;
    private String keyword;
}