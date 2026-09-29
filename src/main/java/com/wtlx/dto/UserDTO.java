package com.wtlx.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
    private Integer gender;     // 对应tb_user_info表的gender字段
    private String city;        // 对应tb_user_info表的city字段
    private String birthday;    // 对应tb_user_info表的birthday字段
    private String introduce;   // 对应tb_user_info表的introduce字段
}
