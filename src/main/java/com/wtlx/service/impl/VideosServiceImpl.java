package com.wtlx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wtlx.entity.dto.Result;
import com.wtlx.entity.User;
import com.wtlx.entity.VideoLikes;
import com.wtlx.entity.Videos;
import com.wtlx.mapper.VideoLikesMapper;
import com.wtlx.mapper.VideosMapper;
import com.wtlx.service.IUserService;
import com.wtlx.service.IVideosService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.utils.UserHolder;
import com.wtlx.entity.vo.VideosVO;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 视频表 服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-05
 */
@Service
public class VideosServiceImpl extends ServiceImpl<VideosMapper, Videos> implements IVideosService {
    @Resource
    private IUserService userService;
    @Resource
    private VideoLikesMapper videoLikesMapper;

    /**
     * 获取所有视频
     * @return
     */
    @Override
    public Result getVideos() {
        // 1.获取所有视频
        List<Videos> videosList = list();
        List<VideosVO> videosVOList = new ArrayList<>();
        VideosVO videosVO = new VideosVO();
        //2.查询视频的发布者信息
        for (Videos videos : videosList) {
            //2.1获取用户id
            Long userId = videos.getUserId();
            //2.2根据用户id查询用户信息
            User user = userService.query().eq("id", userId).one();
            //2.3根据videoId和用户id查询点赞状态
            VideoLikes videoLikes = videoLikesMapper.selectOne(new QueryWrapper<VideoLikes>()
                    .eq("video_id", videos.getId())
                    .eq("user_id", userId));
            int isLiked = videoLikes != null? 1 : 0;
            //2.4封装视频vo
            videosVO = videosVO.builder()
                    .id(userId)
                    .videoId(videos.getId())
                    .nickName(user.getNickName())
                    .icon(user.getIcon())
                    .videoUrl(videos.getVideoUrl())
                    .coverUrl(videos.getCoverUrl())
                    .description(videos.getDescription())
                    .likeCount(videos.getLikesCount())
                    .commentCount(videos.getCommentsCount())
                    .status(videos.getStatus())
                    .isLiked(isLiked)
                    .build();
            videosVOList.add(videosVO);
        }
        //随机视频
        Collections.shuffle(videosVOList);
        //3.返回结果
        return Result.ok(videosVOList);
    }

    /**
     * 发布视频
     * @param videos 视频对象
     * @return 结果
     */
    @Override
    public Result publishVideo(Videos videos) {
        videos.setUserId(UserHolder.getUser().getId());
        // 校验必填字段
        if (videos.getUserId() == null || videos.getVideoUrl() == null || videos.getVideoUrl().trim().isEmpty()) {
            return Result.fail("用户ID和视频地址不能为空");
        }
        // 设置默认值
        videos.setLikesCount(0);
        videos.setCommentsCount(0);
        videos.setStatus(1);
        // 插入数据库
        boolean save = this.save(videos);
        if (save) {
            return Result.ok("发布成功");
        } else {
            return Result.fail("发布失败");
        }
    }
}
