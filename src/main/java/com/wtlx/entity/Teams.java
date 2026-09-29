package com.wtlx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDate;
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
 * 队伍信息表
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("teams")
@ApiModel(value="Teams对象", description="队伍信息表")
public class Teams implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "队伍ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty(value = "队伍名称")
    private String name;

    @ApiModelProperty(value = "创建者ID")
    private Integer creatorId;

    @ApiModelProperty(value = "创建者姓名")
    private String creatorName;

    @ApiModelProperty(value = "创建者手机号")
    private String creatorPhone;

    @ApiModelProperty(value = "创建者QQ号")
    private String creatorQq;

    @ApiModelProperty(value = "创建者头像路径")
    private String creatorAvatar;

    @ApiModelProperty(value = "目的地")
    private String destination;

    @ApiModelProperty(value = "队伍描述")
    private String description;

    @ApiModelProperty(value = "最大成员数")
    private Integer maxMembers;

    @ApiModelProperty(value = "当前成员数")
    private Integer currentMembers;

    @ApiModelProperty(value = "出发日期")
    private LocalDate departureDate;

    @ApiModelProperty(value = "队伍头像路径")
    private String avatar;

    @ApiModelProperty(value = "状态：1-正常，0-已解散")
    private Integer status;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;


}
