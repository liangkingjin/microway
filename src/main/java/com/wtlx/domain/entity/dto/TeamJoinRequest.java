package com.wtlx.domain.entity.dto;

import lombok.Data;

@Data
public class TeamJoinRequest {
    private Integer teamId;
    private String name;
    private String phone;
    private String qq;
    private String remark;
}
