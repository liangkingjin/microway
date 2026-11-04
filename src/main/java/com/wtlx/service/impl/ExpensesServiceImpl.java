package com.wtlx.service.impl;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.Expenses;
import com.wtlx.repository.mapper.ExpensesMapper;
import com.wtlx.service.IExpensesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.service.ITravelPlansService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
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
public class ExpensesServiceImpl extends ServiceImpl<ExpensesMapper, Expenses> implements IExpensesService {
    @Resource
    private ITravelPlansService travelPlansService;

    /**
     * 根据id查询消费记录
     * @param id
     * @return
     */
    @Override
    public Result queryExpenses(Integer id) {
        List<Expenses> expensesList = query().eq("plan_id", id).list();
        return Result.ok(expensesList);
    }

    /**
     * 添加消费
     * @param expenses
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result addExpenses(Expenses expenses) {
        if (expenses == null || expenses.getPlanId() == null) {
            return Result.fail("参数错误");
        }
        if (!save(expenses)) {
            return Result.fail("添加失败");
        }
        // 新增即累加总支出
        adjustTotalAmount(expenses.getPlanId(), expenses.getAmount());
        return Result.ok();
    }

    /**
     * 修改消费：只更新账单自身字段，并按新旧金额差额同步计划总支出
     * @param id
     * @param expenses
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateExpenses(Integer id, Expenses expenses) {
        if (id == null || expenses == null) {
            return Result.fail("参数错误");
        }
        Expenses old = getById(id);
        if (old == null) {
            return Result.fail("账单不存在");
        }
        // 归属计划不随编辑变更，否则两个计划的总支出会各自失真
        expenses.setId(id).setPlanId(old.getPlanId());
        if (!updateById(expenses)) {
            return Result.fail("修改失败");
        }
        BigDecimal oldAmount = old.getAmount() == null ? BigDecimal.ZERO : old.getAmount();
        BigDecimal newAmount = expenses.getAmount() == null ? oldAmount : expenses.getAmount();
        adjustTotalAmount(old.getPlanId(), newAmount.subtract(oldAmount));
        return Result.ok();
    }

    /**
     * 删除消费：删除成功后从计划总支出中扣回该笔金额
     * @param id
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteExpenses(Integer id) {
        if (id == null) {
            return Result.fail("参数错误");
        }
        Expenses old = getById(id);
        if (old == null) {
            return Result.fail("账单不存在");
        }
        if (!removeById(id)) {
            return Result.fail("删除失败");
        }
        BigDecimal amount = old.getAmount() == null ? BigDecimal.ZERO : old.getAmount();
        adjustTotalAmount(old.getPlanId(), amount.negate());
        return Result.ok();
    }

    /**
     * 维护计划总支出：新增传正数、删除传负数、编辑传差额，三种操作共用同一入口
     * @param planId 关联计划id
     * @param delta 增量，0 或 null 时跳过
     */
    private void adjustTotalAmount(Integer planId, BigDecimal delta) {
        if (planId == null || delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        travelPlansService.update()
                .setSql("total_amount = GREATEST(COALESCE(total_amount, 0) + {0}, 0)", delta)
                .eq("id", planId)
                .update();
    }

}
