package com.wtlx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wtlx.entity.ThumbsUpRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 通用点赞记录表 Mapper 接口
 * </p>
 *
 * @author 金小梁
 */
@Mapper
public interface ThumbsUpRecordMapper extends BaseMapper<ThumbsUpRecord> {

    @Select("SELECT COUNT(*) FROM thumbs_up_record WHERE type = #{type} AND biz_id = #{bizId} AND status = 1")
    Long countByTypeAndBizId(@Param("type") String type, @Param("bizId") Long bizId);
}
