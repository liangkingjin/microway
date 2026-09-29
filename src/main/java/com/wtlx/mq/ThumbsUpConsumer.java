package com.wtlx.mq;

import com.wtlx.service.IThumbsUpRecordService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class ThumbsUpConsumer {

    @Resource
    private IThumbsUpRecordService thumbsUpRecordService;

    @RabbitListener(queues = "thumbs.up.queue")
    public void handleThumbsUp(ThumbsUpMessage message) {
        thumbsUpRecordService.asyncUpdateLikeCount(message.getType(), message.getBizId(), message.getIsLike());
    }
}
