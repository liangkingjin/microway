package com.wtlx.entity.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@ApiModel("酒店订单详情VO")
public class HotelOrderDetailVO {
    @ApiModelProperty("订单ID")
    private Integer id;

    @ApiModelProperty("酒店ID")
    private Long shopId;

    @ApiModelProperty("房间ID")
    private Long roomId;

    @ApiModelProperty("房型")
    private String roomType;

    @ApiModelProperty("房间标题")
    private String title;

    @ApiModelProperty("房间大小")
    private String size;

    @ApiModelProperty("床型信息")
    private String bedInfo;

    @ApiModelProperty("容纳人数")
    private String capacity;

    @ApiModelProperty("餐食信息")
    private String mealInfo;

    @ApiModelProperty("取消政策")
    private String cancelInfo;

    @ApiModelProperty("楼层信息")
    private String floor;

    @ApiModelProperty("房间设施")
    private String facilities;

    @ApiModelProperty("订单金额")
    private BigDecimal price;

    @ApiModelProperty("订单状态")
    private Integer status;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}
