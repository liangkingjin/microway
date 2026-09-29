package com.wtlx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wtlx.dto.Result;
import com.wtlx.entity.VideoLikes;
import com.wtlx.entity.Videos;
import com.wtlx.mapper.VideoLikesMapper;
import com.wtlx.mapper.VideosMapper;
import com.wtlx.service.IVideoLikesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 视频点赞记录表 服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-05
 */
@Service
public class VideoLikesServiceImpl extends ServiceImpl<VideoLikesMapper, VideoLikes> implements IVideoLikesService {
    @Resource
    private VideoLikesMapper videoLikesMapper;
    @Resource
    private VideosMapper videosMapper;

    /**
     * 添加点赞记录
     * @param id
     * @return
     */
    @Override
    @Transactional
    public Result addVideoLikes(Long id) {
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否点赞该视频
        VideoLikes likes = query().eq("video_id", id).eq("user_id", userId).one();
        //3.如果未点赞，则点赞，并保存到数据库中，count++
        if (likes==null){
            VideoLikes videoLikes = new VideoLikes();
            videoLikes.setVideoId(id);
            videoLikes.setUserId(userId);
            videoLikes.setCreateTime(LocalDateTime.now());
            videoLikesMapper.insert(videoLikes);
            //更新视频点赞数
            videosMapper.updateLikes(id);
        }else{
            //4.如果已点赞，则取消点赞，count--
            videoLikesMapper.delete(new LambdaQueryWrapper<VideoLikes>()
                    .eq(VideoLikes::getVideoId, id)
                    .eq(VideoLikes::getUserId, userId));
            //更新视频点赞数
            videosMapper.updateLikes2(id);
        }

        return Result.ok();
    }
}
