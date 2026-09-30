package com.wtlx.domain.entity.vo;

import com.wtlx.domain.entity.PhotoSpots;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceRecommendationVO {

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

    @ApiModelProperty(value = "点位信息")
    private List<PhotoSpots> photoSpots;
}
