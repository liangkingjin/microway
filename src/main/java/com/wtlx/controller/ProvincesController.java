package com.wtlx.controller;


import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.Provinces;
import com.wtlx.service.IProvincesService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-03-13
 */
@RestController
@RequestMapping("/provinces")
@Api(tags = "省份模块")
public class ProvincesController {
    @Autowired
    private IProvincesService provincesService;

    /**
     * 查询所有的省份
     * @return
     */
    @GetMapping
    public Result queryProvinces(){
        List<Provinces> provinces = provincesService.queryProvinces();
        return Result.ok(provinces);
    }


}
