package com.wtlx.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {
    private Long id;
    private Long blogId;
    private Long userId;
    private Long parentId;
    private Long answerId;
    private String content;
    private String images;
    private Integer liked;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 用户相关字段
    private String userName;
    private String userIcon;
    private Integer userLevel;
}