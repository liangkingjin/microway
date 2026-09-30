package com.wtlx.domain.entity;

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
 * 酒店房间表
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_hotel_room")
@ApiModel(value="TbHotelRoom对象", description="酒店房间表")
public class TbHotelRoom implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "房间ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "所属酒店ID")
    private Long shopId;

    @ApiModelProperty(value = "房间类型，如")
    private String roomType;

    @ApiModelProperty(value = "房间标题")
    private String title;

    @ApiModelProperty(value = "房间大小")
    private String size;

    @ApiModelProperty(value = "床型信息")
    private String bedInfo;

    @ApiModelProperty(value = "容纳人数")
    private String capacity;

    @ApiModelProperty(value = "餐食信息")
    private String mealInfo;

    @ApiModelProperty(value = "取消政策")
    private String cancelInfo;

    @ApiModelProperty(value = "楼层信息")
    private String floor;

    @ApiModelProperty(value = "房间设施，JSON格式")
    private String facilities;

    @ApiModelProperty(value = "房间图片，多个图片用逗号分隔")
    private String images;

    @ApiModelProperty(value = "原价")
    private BigDecimal originalPrice;

    @ApiModelProperty(value = "现价")
    private BigDecimal currentPrice;

    @ApiModelProperty(value = "优惠金额")
    private String discount;

    @ApiModelProperty(value = "剩余房间数量")
    private Integer stock;

    @ApiModelProperty(value = "促销信息，JSON格式")
    private String promotions;

    @ApiModelProperty(value = "状态，0:下架，1:上架")
    private Integer status;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;


}
