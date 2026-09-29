package com.wtlx.entity.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {
    private String nickName;    // 对应tb_user表的nick_name字段
    private String icon;        // 对应tb_user表的icon字段
    private Integer gender;     // 对应tb_user_info表的gender字段
    private String city;        // 对应tb_user_info表的city字段
    private String birthday;    // 对应tb_user_info表的birthday字段
    private String introduce;   // 对应tb_user_info表的introduce字段
}
