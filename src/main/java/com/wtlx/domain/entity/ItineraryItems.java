package com.wtlx.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDate;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("itinerary_items")
@ApiModel(value="ItineraryItems对象", description="")
public class ItineraryItems implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty(value = "关联的旅行计划ID")
    private Integer planId;

    @ApiModelProperty(value = "行程日期")
    private LocalDate date;

    @ApiModelProperty(value = "行程时间")
    private LocalTime time;

    @ApiModelProperty(value = "行程内容")
    private String content;

    @ApiModelProperty(value = "关联景点名：有值时前端把该景点渲染成可点入口，进入景点详情")
    private String spotName;

    private LocalDateTime createdAt;


}
