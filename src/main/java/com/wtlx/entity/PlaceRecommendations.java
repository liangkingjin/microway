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
 * 
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("place_recommendations")
@ApiModel(value="PlaceRecommendations对象", description="")
public class PlaceRecommendations implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty(value = "地点名称")
    private String title;

    @ApiModelProperty(value = "简短描述")
    private String shortDesc;

    @ApiModelProperty(value = "评分")
    private BigDecimal rating;

    @ApiModelProperty(value = "费用类型")
    private String priceType;

    @ApiModelProperty(value = "热门程度")
    private String popularity;

    @ApiModelProperty(value = "AI导览内容")
    private String aiGuide;

    @ApiModelProperty(value = "开放时间")
    private String openingHours;

    @ApiModelProperty(value = "联系电话")
    private String phone;

    @ApiModelProperty(value = "地址")
    private String address;

    @ApiModelProperty(value = "封面图片URL")
    private String coverUrl;

    @ApiModelProperty(value = "图片URL列表，JSON格式存储")
    private String images;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


}
