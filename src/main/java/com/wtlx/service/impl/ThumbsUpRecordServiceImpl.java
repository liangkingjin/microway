package com.wtlx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.domain.entity.Blog;
import com.wtlx.domain.entity.ThumbsUpRecord;
import com.wtlx.domain.entity.TbBlogComments;
import com.wtlx.repository.mapper.ThumbsUpRecordMapper;
import com.wtlx.repository.mapper.BlogMapper;
import com.wtlx.service.IThumbsUpRecordService;
import com.wtlx.service.ITbBlogCommentsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 通用点赞记录服务实现类
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class ThumbsUpRecordServiceImpl extends ServiceImpl<ThumbsUpRecordMapper, ThumbsUpRecord> implements IThumbsUpRecordService {

    @Resource
    private ThumbsUpRecordMapper thumbsUpRecordMapper;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private ITbBlogCommentsService commentsService;

    /**
     * 更新业务表点赞数
     *
     * @param type   点赞类型：blog-博客，comment-评论
     * @param bizId  业务ID
     * @param isLike true-点赞，false-取消点赞
     */
    @Override
    public void asyncUpdateLikeCount(String type, Long bizId, boolean isLike) {
        try {
            if ("blog".equals(type)) {
                updateBlogLikeCount(bizId, isLike);
            } else if ("comment".equals(type)) {
                updateCommentLikeCount(bizId, isLike);
            }
            log.debug("异步更新{}点赞数，bizId: {}, isLike: {}", type, bizId, isLike);
        } catch (Exception e) {
            log.error("异步更新点赞数失败，type: {}, bizId: {}", type, bizId, e);
        }
    }

    /**
     * 更新博客点赞数
     */
    private void updateBlogLikeCount(Long blogId, boolean isLike) {
        Blog blog = blogMapper.selectById(blogId);
        if (blog != null) {
            int liked = blog.getLiked() != null ? blog.getLiked() : 0;
            liked = isLike ? liked + 1 : Math.max(0, liked - 1);
            blog.setLiked(liked);
            blogMapper.updateById(blog);
        }
    }

    /**
     * 更新评论点赞数
     */
    private void updateCommentLikeCount(Long commentId, boolean isLike) {
        TbBlogComments comment = commentsService.getById(commentId);
        if (comment != null) {
            int liked = comment.getLiked() != null ? comment.getLiked() : 0;
            liked = isLike ? liked + 1 : Math.max(0, liked - 1);
            comment.setLiked(liked);
            commentsService.updateById(comment);
        }
    }

    /**
     * 保存或更新点赞记录（用于定时任务持久化）
     *
     * @param type   点赞类型
     * @param bizId  业务ID
     * @param userId 用户ID
     * @param status 状态：1-点赞，0-取消点赞
     */
    @Override
    public void saveOrUpdateLikeRecord(String type, Long bizId, Long userId, Integer status) {
        QueryWrapper<ThumbsUpRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", type)
                .eq("biz_id", bizId)
                .eq("user_id", userId);

        ThumbsUpRecord existing = thumbsUpRecordMapper.selectOne(queryWrapper);

        if (existing != null) {
            existing.setStatus(status);
            existing.setUpdateTime(LocalDateTime.now());
            thumbsUpRecordMapper.updateById(existing);
        } else if (status == 1) {
            ThumbsUpRecord record = new ThumbsUpRecord();
            record.setType(type);
            record.setBizId(bizId);
            record.setUserId(userId);
            record.setStatus(status);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            thumbsUpRecordMapper.insert(record);
        }
    }
}
