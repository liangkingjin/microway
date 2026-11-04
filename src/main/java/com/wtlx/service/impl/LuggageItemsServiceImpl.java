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
        if (luggageItems == null || luggageItems.getPlanId() == null) {
            return Result.fail("参数错误");
        }
        if (!save(luggageItems)) {
            return Result.fail("添加失败");
        }
        return Result.ok();
    }

    /**
     * 修改行李：仅覆盖本次提交的字段，未提交的字段沿用库中原值
     * @param id
     * @param luggageItems
     * @return
     */
    @Override
    public Result updateLuggageItems(Integer id, LuggageItems luggageItems) {
        if (id == null || luggageItems == null) {
            return Result.fail("参数错误");
        }
        LuggageItems old = getById(id);
        if (old == null) {
            return Result.fail("物品不存在");
        }
        // 归属计划不允许通过编辑变更
        luggageItems.setId(id).setPlanId(old.getPlanId());
        if (luggageItems.getName() == null) {
            luggageItems.setName(old.getName());
        }
        if (luggageItems.getQuantity() == null) {
            luggageItems.setQuantity(old.getQuantity());
        }
        if (luggageItems.getIsPacked() == null) {
            luggageItems.setIsPacked(old.getIsPacked());
        }
        if (!updateById(luggageItems)) {
            return Result.fail("修改失败");
        }
        return Result.ok();
    }

    /**
     * 删除行李
     * @param id
     * @return
     */
    @Override
    public Result deleteLuggageItems(Integer id) {
        if (id == null) {
            return Result.fail("参数错误");
        }
        if (getById(id) == null) {
            return Result.fail("物品不存在");
        }
        if (!removeById(id)) {
            return Result.fail("删除失败");
        }
        return Result.ok();
    }
}
