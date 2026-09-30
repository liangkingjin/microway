package com.wtlx.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 视频表
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-05
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("videos")
@ApiModel(value="Videos对象", description="视频表")
public class Videos implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "视频ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "发布者用户ID")
    private Long userId;

    @ApiModelProperty(value = "视频描述")
    private String description;

    @ApiModelProperty(value = "视频文件地址")
    private String videoUrl;

    @ApiModelProperty(value = "视频封面图地址")
    private String coverUrl;

    @ApiModelProperty(value = "点赞数")
    private Integer likesCount;

    @ApiModelProperty(value = "评论数")
    private Integer commentsCount;

    @ApiModelProperty(value = "状态：1-正常 0-下架")
    private Integer status;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;
}
