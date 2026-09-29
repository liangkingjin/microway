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
 * 
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("luggage_items")
@ApiModel(value="LuggageItems对象", description="")
public class LuggageItems implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty(value = "关联的旅行计划ID")
    private Integer planId;

    @ApiModelProperty(value = "物品名称")
    private String name;

    @ApiModelProperty(value = "数量")
    private Integer quantity;

    @ApiModelProperty(value = "是否已打包")
    private Boolean isPacked;

    private LocalDateTime createdAt;


}
