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
 * 通用点赞记录表
 * </p>
 *
 * @author 金小梁
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("thumbs_up_record")
@ApiModel(value="ThumbsUpRecord对象", description="通用点赞记录表")
public class ThumbsUpRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "业务类型：blog-博客，comment-评论，travel-游记，video-视频等")
    private String type;

    @ApiModelProperty(value = "业务ID")
    private Long bizId;

    @ApiModelProperty(value = "用户ID")
    private Long userId;

    @ApiModelProperty(value = "点赞状态：1-点赞，0-取消点赞")
    private Integer status;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;
}
