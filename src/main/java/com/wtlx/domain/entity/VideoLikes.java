package com.wtlx.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 视频点赞记录表
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-05
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("video_likes")
@ApiModel(value="VideoLikes对象", description="视频点赞记录表")
public class VideoLikes implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "视频ID")
    private Long videoId;

    @ApiModelProperty(value = "用户ID")
    private Long userId;

    @ApiModelProperty(value = "点赞时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;


}
