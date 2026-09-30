package com.wtlx.infrastructure.mq.message;

import lombok.Data;
import java.io.Serializable;

@Data
public class ThumbsUpMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;
    private Long bizId;
    private Boolean isLike;

    public ThumbsUpMessage() {
    }

    public ThumbsUpMessage(String type, Long bizId, Boolean isLike) {
        this.type = type;
        this.bizId = bizId;
        this.isLike = isLike;
    }
}
