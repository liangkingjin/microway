package com.wtlx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.entity.TbBlogComments;
import com.wtlx.entity.User;
import com.wtlx.mapper.TbBlogCommentsMapper;
import com.wtlx.mapper.UserMapper;
import com.wtlx.service.ITbBlogCommentsService;
import com.wtlx.vo.CommentVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TbBlogCommentsServiceImpl extends ServiceImpl<TbBlogCommentsMapper, TbBlogComments> implements ITbBlogCommentsService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public List<CommentVO> listByBlogIdWithUser(Long blogId) {
        QueryWrapper<TbBlogComments> wrapper = new QueryWrapper<>();
        wrapper.eq("blog_id", blogId)
                .orderByAsc("create_time");
        List<TbBlogComments> comments = this.list(wrapper);

        List<CommentVO> voList = new ArrayList<>();
        for (TbBlogComments comment : comments) {
            CommentVO vo = new CommentVO();
            BeanUtils.copyProperties(comment, vo);
            User user = userMapper.selectById(comment.getUserId());
            if (user != null) {
                vo.setUserName(user.getNickName());
                vo.setUserIcon(user.getIcon());
                vo.setUserLevel(2);
            }
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public boolean saveComment(TbBlogComments comment) {
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        comment.setStatus(0);
        if (comment.getParentId() == null) comment.setParentId(0L);
        if (comment.getAnswerId() == null) comment.setAnswerId(0L);
        return this.save(comment);
    }
}