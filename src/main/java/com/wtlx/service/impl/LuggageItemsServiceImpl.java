package com.wtlx.service.impl;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.LuggageItems;
import com.wtlx.repository.mapper.LuggageItemsMapper;
import com.wtlx.service.ILuggageItemsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-07
 */
@Service
public class LuggageItemsServiceImpl extends ServiceImpl<LuggageItemsMapper, LuggageItems> implements ILuggageItemsService {

    /**
     * 展示所有的行李
     * @param id
     * @return
     */
    @Override
    public Result listAllLuggageItems(Integer id) {
        List<LuggageItems> luggageItemsList = query().eq("plan_id", id).list();
        return Result.ok(luggageItemsList);
    }

    /**
     * 添加行李
     * @param luggageItems
     * @return
     */
    @Override
    public Result addLuggageItems(LuggageItems luggageItems) {
        save(luggageItems);
        return Result.ok();
    }
}
