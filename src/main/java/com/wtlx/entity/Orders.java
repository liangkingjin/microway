package com.wtlx.entity;

import java.math.BigDecimal;
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
 * 订单表
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("orders")
@ApiModel(value="Orders对象", description="订单表")
public class Orders implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "订单ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "订单编号")
    private String orderNo;

    @ApiModelProperty(value = "景点ID")
    private Long scenicId;

    @ApiModelProperty(value = "景点名称")
    private String scenicName;

    @ApiModelProperty(value = "游玩日期")
    private String visitDate;

    @ApiModelProperty(value = "门票信息")
    private String tickets;

    @ApiModelProperty(value = "订单总价")
    private BigDecimal totalPrice;

    @ApiModelProperty(value = "总票数")
    private Integer totalQuantity;

    @ApiModelProperty(value = "订单状态：0-待支付 1-已支付 2-已取消")
    private Integer orderStatus;

    @ApiModelProperty(value = "下单时间")
    private LocalDateTime orderTime;

    @ApiModelProperty(value = "用户ID")
    private Long userId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


}
