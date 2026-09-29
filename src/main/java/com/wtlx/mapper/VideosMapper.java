package com.wtlx.mapper;

import com.wtlx.entity.Videos;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 视频表 Mapper 接口
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-05
 */
public interface VideosMapper extends BaseMapper<Videos> {

    /**
     * 更新点赞数
     * @param id
     */
    @Update("update videos set likes_count = likes_count + 1 where id = #{id}")
    void updateLikes(Long id);

    /**
     * 更新点赞数
     * @param id
     */
    @Update("update videos set likes_count = likes_count - 1 where id = #{id}")
    void updateLikes2(Long id);
}
