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
 * 博客评论表
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog_comments")
@ApiModel(value="TbBlogComments对象", description="博客评论表")
public class TbBlogComments implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "评论主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "关联的博客ID")
    private Long blogId;

    @ApiModelProperty(value = "发表评论的用户ID")
    private Long userId;

    @ApiModelProperty(value = "父评论ID (0表示顶级评论, >0表示回复某条评论/回复)")
    private Long parentId;

    @ApiModelProperty(value = "顶级评论ID (方便查询某个顶级评论下的所有回复，顶级评论自身此字段为0)")
    private Long answerId;

    @ApiModelProperty(value = "被回复的用户ID (用于@功能, 顶级评论或直接回复主评论时为NULL)")
    private Long replyToUserId;

    @ApiModelProperty(value = "评论或回复的文本内容")
    private String content;

    @ApiModelProperty(value = "评论或回复的图片URL列表 (多个URL以逗号分隔)")
    private String images;

    @ApiModelProperty(value = "点赞数量 (可选功能)")
    private Integer liked;

    @ApiModelProperty(value = "状态 (0:正常, 1:用户隐藏, 2:管理员隐藏/删除)")
    private Integer status;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;


}
