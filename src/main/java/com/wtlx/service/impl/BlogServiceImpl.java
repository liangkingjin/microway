package com.wtlx.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.dto.ScrollResult;
import com.wtlx.domain.entity.dto.UserDTO;
import com.wtlx.domain.entity.Blog;
import com.wtlx.domain.entity.Follow;
import com.wtlx.domain.entity.TbBlogComments;
import com.wtlx.domain.entity.User;
import com.wtlx.repository.mapper.BlogMapper;
import com.wtlx.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.service.ILikedService;
import com.wtlx.service.IFollowService;
import com.wtlx.service.ITbBlogCommentsService;
import com.wtlx.service.IUserService;
import com.wtlx.utils.RedisConstants;
import com.wtlx.utils.SystemConstants;
import com.wtlx.utils.UserHolder;
import com.wtlx.infrastructure.mq.message.RagDocIndexMessage;
import com.wtlx.infrastructure.mq.producer.RagDocIndexProducer;
import com.wtlx.infrastructure.rag.RagDocType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ITbBlogCommentsService commentsService;
    @Resource
    private ILikedService likedService;
    @Resource
    private RagDocIndexProducer ragDocIndexProducer;
    /**
     * 根据id查询博客
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        //查询博客
        Blog blog = getById(id);
        if (blog == null){
            return Result.fail("笔记不存在");
        }
        //查询Blog的作者信息
        queryBlogUser(blog);
        //查询该博客是否被用户点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 点赞博客
     * 使用Redis ZSet存储点赞用户ID，通过定时任务同步到数据库
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();

        // 2.调用点赞服务执行点赞/取消点赞操作
        // 使用Redis ZSet存储，点赞数存储在Redis中
        boolean isLike = likedService.like(ILikedService.TYPE_BLOG, id, userId);

        // 3.返回点赞结果
        return Result.ok(isLike);
    }

    /**
     * 查询所有博客
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 点赞列表查询
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        // 使用Set查询点赞用户列表
        String key = RedisConstants.BLOG_LIKED_USER_KEY + id;
        // 查询所有点赞用户
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 解析出其中的用户id
        List<Long> ids = members.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 根据用户id查询用户
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("order by field(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 返回UserDTO集合
        return Result.ok(userDTOS);
    }

    /**
     * 发表博客并将数据推送到粉丝的redis中去
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        //2.保存探店笔记
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败|");
        }
        //3.查询该用户的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        //4.推送笔记id给所有的粉丝
        for (Follow follow : follows) {
            //4.1获取粉丝的id
            Long followUserId = follow.getUserId();
            //4.2推送到redis，并添加时间戳
            String key=RedisConstants.FEED_KEY+followUserId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        //5.返回id
        // 5. 返回 id（RAG 向量索引改由 MQ 异步处理，不阻塞发布流程）
        ragDocIndexProducer.sendAfterCommit(new RagDocIndexMessage("PUBLISHED", RagDocType.BLOG, blog.getId()));
        return Result.ok(blog.getId());
    }

    /**
     * 分页查询收件箱
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogFollow(Long max, Integer offset) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询收件箱
        String key=RedisConstants.FEED_KEY+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 5);
        //3.非空判断
        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        //4.解析数据
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime=0;
        int os=1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //4.1获取id
            ids.add(Long.valueOf(tuple.getValue()));
            //4.2获取分数
            long time = tuple.getScore().longValue();
            if(time==minTime){
                // 当前时间等于最小时间，偏移量+1
                os++;
            }else {
                // 当前时间不等于最小时间，更新覆盖最小时间，重置偏移量为1
                minTime=time;
                os=1;
            }
        }
        //5.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            //5.1查询blog有关的用户
            queryBlogUser(blog);
            //5.2查询blog是否被点赞
            isBlogLiked(blog);
        }

        //6.封装并返回
        ScrollResult scrollResult = ScrollResult.builder()
                .list(blogs)
                .offset(os)
                .minTime(minTime)
                .build();
        return Result.ok(scrollResult);
    }

    /**
     * 删除博客
     * @param id 博客ID
     * @return 删除结果
     */
    @Override
    public Result deleteBlog(Long id) {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 查询博客
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        // 只能删除自己的博客
        if (!blog.getUserId().equals(userId)) {
            return Result.fail("无权限删除该博客");
        }

        // 1. 删除博客相关评论
        commentsService.remove(new QueryWrapper<TbBlogComments>().eq("blog_id", id));

        // 2. 删除博客相关点赞记录（Redis）
        // 2.1 删除用户Set
        String likeUserKey = RedisConstants.BLOG_LIKED_USER_KEY + id;
        stringRedisTemplate.delete(likeUserKey);
        // 2.2 从聚合ZSet中移除该博客的点赞数
        stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_COUNT_KEY, id.toString());

        // 3. 删除博客
        boolean removed = removeById(id);
        if (removed) {
            // 同步清理该游记在向量库中的切片，避免已删除内容仍被检索到
        ragDocIndexProducer.sendAfterCommit(new RagDocIndexMessage("DELETED", RagDocType.BLOG, id));
            return Result.ok("删除成功");
        } else {
            return Result.fail("删除失败");
        }
    }

    /**
     * 封装博客作者信息到blog中
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    /**
     * 查询博客是否被点赞
     * 使用LikedService从Redis中查询点赞状态
     * @param blog
     */
    private void isBlogLiked(Blog blog){
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        if(user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();

        // 2. 使用LikedService查询用户是否点赞过
        boolean isLiked = likedService.isLiked(ILikedService.TYPE_BLOG, blog.getId(), userId);
        blog.setIsLike(isLiked);
    }
}
