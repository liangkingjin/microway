package com.wtlx.service;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.Expenses;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IExpensesService extends IService<Expenses> {

    /**
     * 查询所有费用
     * @param id
     * @return
     */
    Result queryExpenses(Integer id);

    /**
     * 添加费用
     * @param expenses
     * @return
     */
    Result addExpenses(Expenses expenses);

    /**
     * 修改费用（按金额差额同步计划总支出）
     * @param id 账单id
     * @param expenses 待修改内容
     * @return
     */
    Result updateExpenses(Integer id, Expenses expenses);

    /**
     * 删除费用（从计划总支出中扣回该笔金额）
     * @param id 账单id
     * @return
     */
    Result deleteExpenses(Integer id);
}
