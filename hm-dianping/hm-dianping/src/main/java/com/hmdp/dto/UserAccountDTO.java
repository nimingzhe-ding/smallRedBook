package com.hmdp.dto;

import lombok.Data;

@Data
public class UserAccountDTO {
    private Long id;
    private String nickName;
    private String icon;
    private Integer role;
    private String maskedPhone;
}
