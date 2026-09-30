package com.wtlx.domain.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@ApiModel("酒店订单VO")
public class HotelOrderVO {
    @ApiModelProperty("订单ID")
    private Integer id;

    @ApiModelProperty("酒店名称")
    private String hotelName;

    @ApiModelProperty("房型名称")
    private String roomType;

    @ApiModelProperty("订单金额")
    private BigDecimal price;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty("入住日期")
    private LocalDate checkInDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty("离店日期")
    private LocalDate checkOutDate;

    @ApiModelProperty("订单状态")
    private Integer status;

    @ApiModelProperty("状态显示文本")
    private String statusText;
}
