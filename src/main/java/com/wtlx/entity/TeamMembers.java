package com.wtlx.entity;

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
 * 队伍成员表
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("team_members")
@ApiModel(value="TeamMembers对象", description="队伍成员表")
public class TeamMembers implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "记录ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty(value = "队伍ID")
    private Integer teamId;

    @ApiModelProperty(value = "用户ID")
    private Integer userId;

    @ApiModelProperty(value = "成员姓名")
    private String name;

    @ApiModelProperty(value = "手机号码")
    private String phone;

    @ApiModelProperty(value = "QQ号码")
    private String qq;

    @ApiModelProperty(value = "成员头像路径")
    private String avatar;

    @ApiModelProperty(value = "是否队长：1-是，0-否")
    private Integer isLeader;

    @ApiModelProperty(value = "加入时间")
    private LocalDateTime joinTime;

    @ApiModelProperty(value = "状态：1-正常，0-已退出")
    private Integer status;

    @ApiModelProperty(value = "备注信息")
    private String remark;


}
