package com.wtlx.dto;

import lombok.Data;

@Data
public class TeamJoinRequest {
    private Integer teamId;
    private String name;
    private String phone;
    private String qq;
    private String remark;
}
