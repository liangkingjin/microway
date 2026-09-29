package com.wtlx.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户视频信息类
 *
 * 该类用于存储用户视频的相关信息，包括用户ID、昵称、头像、视频URL、封面URL、描述以及互动数据等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideosVO {
    private Long id; // 用户ID，用于唯一标识一个用户
    private Long videoId;
    private String nickName; // 用户昵称，展示在视频页面
    private String icon; // 用户头像的URL，用于视频页面的头像展示
    private String videoUrl; // 视频资源的URL，用户可以点击播放
    private String coverUrl; // 视频封面图片的URL，在视频未播放时展示
    private String description; // 视频描述，用户可以添加的视频简介或说明
    private Integer likeCount; // 视频点赞数量，反映视频的受欢迎程度
    private Integer commentCount; // 视频评论数量，反映观众的互动程度
    private Integer isLiked;
    private Integer status; // 视频状态，用于表示视频是否可用、审核中、已下架等状态
}
