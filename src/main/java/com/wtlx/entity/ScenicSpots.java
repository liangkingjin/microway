package com.wtlx.entity;

import java.math.BigDecimal;
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
 * 景点信息表
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("scenic_spots")
@ApiModel(value="ScenicSpots对象", description="景点信息表")
public class ScenicSpots implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "景点ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty(value = "景点名称")
    private String name;

    @ApiModelProperty(value = "景点图片URL")
    private String imageUrl;

    @ApiModelProperty(value = "景点等级，如5A、4A等")
    private String level;

    @ApiModelProperty(value = "景点评分")
    private BigDecimal rating;

    @ApiModelProperty(value = "评价人数")
    private Integer visitCount;

    @ApiModelProperty(value = "距离，单位：km")
    private BigDecimal distance;

    @ApiModelProperty(value = "门票起始价格")
    private BigDecimal price;

    @ApiModelProperty(value = "详细地址")
    private String address;

    @ApiModelProperty(value = "开放时间，如08:30-16:30")
    private String openingTime;

    @ApiModelProperty(value = "营业状态：0关闭，1开放中")
    private Integer status;

    @ApiModelProperty(value = "入园公告")
    private String notice;

    @ApiModelProperty(value = "门票详细信息，JSON格式，包含票型、价格、说明等")
    private String ticketInfo;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updatedAt;


}
