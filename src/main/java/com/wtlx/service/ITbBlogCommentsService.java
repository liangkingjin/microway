package com.wtlx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wtlx.domain.entity.TbBlogComments;
import com.wtlx.domain.entity.vo.CommentVO;

import java.util.List;

public interface ITbBlogCommentsService extends IService<TbBlogComments> {
    List<CommentVO> listByBlogIdWithUser(Long blogId);

    boolean saveComment(TbBlogComments comment);
}