package com.wtlx.service.impl;

import com.wtlx.entity.dto.Result;
import com.wtlx.entity.Expenses;
import com.wtlx.entity.TravelPlans;
import com.wtlx.mapper.ExpensesMapper;
import com.wtlx.service.IExpensesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.service.ITravelPlansService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
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
    public Result addExpenses(Expenses expenses) {
        boolean isSuccess = save(expenses);
        //更新总金额
        TravelPlans travelPlans = travelPlansService.query().eq("id", expenses.getPlanId()).one();
        if (travelPlans.getTotalAmount()==null){
            travelPlans.setTotalAmount(expenses.getAmount());
            travelPlansService.update().setSql("total_amount=" + expenses.getAmount())
                    .eq("id", expenses.getPlanId()).update();
        }else{
            travelPlansService.update().setSql("total_amount = total_amount + " + expenses.getAmount())
                    .eq("id", expenses.getPlanId()).update();
        }
        if (isSuccess) {
            return Result.ok();
        } else {
            return Result.fail("添加失败");
        }
    }

}
