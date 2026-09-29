package com.wtlx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wtlx.entity.TbBlogComments;
import com.wtlx.entity.vo.CommentVO;

import java.util.List;

public interface ITbBlogCommentsService extends IService<TbBlogComments> {
    List<CommentVO> listByBlogIdWithUser(Long blogId);

    boolean saveComment(TbBlogComments comment);
}