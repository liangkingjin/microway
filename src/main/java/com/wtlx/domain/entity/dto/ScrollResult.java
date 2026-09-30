package com.wtlx.domain.entity.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
/**
 * 分页查询收件箱
 */
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
