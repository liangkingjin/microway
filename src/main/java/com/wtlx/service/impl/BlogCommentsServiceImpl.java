package com.wtlx.service.impl;

import com.wtlx.domain.entity.BlogComments;
import com.wtlx.repository.mapper.BlogCommentsMapper;
import com.wtlx.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
