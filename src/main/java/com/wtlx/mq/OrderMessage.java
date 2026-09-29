package com.wtlx.mq;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long scenicId;
    private String scenicName;
    private String visitDate;
    private String tickets;
    private Integer quantity;
    private BigDecimal totalPrice;
    private Long userId;
    private String realName;
    private String idCard;
    private String phone;
}
