package com.wtlx.controller;


import com.wtlx.dto.Result;
import com.wtlx.entity.Expenses;
import com.wtlx.service.IExpensesService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-07
 */
@Api(tags = "费用模块接口")
@RestController
@RequestMapping("/expenses")
public class ExpensesController {
    @Resource
    private IExpensesService expensesService;


    @GetMapping("/{id}")
    @ApiOperation("查询所有费用")
    public Result queryExpenses(@PathVariable Integer id) {
        return expensesService.queryExpenses(id);
    }

    @PostMapping
    @ApiOperation("添加费用")
    public Result addExpenses(@RequestBody Expenses expenses) {
        return expensesService.addExpenses(expenses);
    }
}
