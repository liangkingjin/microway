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
}
