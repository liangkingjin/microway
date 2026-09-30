package com.wtlx.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wtlx.domain.entity.RagSourceIndex;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 来源索引 Mapper（MySQL 主库）
 *
 * @author 金小梁
 */
@Mapper
public interface RagSourceIndexMapper extends BaseMapper<RagSourceIndex> {
}
