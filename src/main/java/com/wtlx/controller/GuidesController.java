package com.wtlx.controller;


import com.wtlx.dto.Result;
import com.wtlx.entity.Guides;
import com.wtlx.service.IGuidesService;
import com.wtlx.service.impl.GuidesServiceImpl;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@Api(tags = "攻略模块接口")
@RestController
@RequestMapping("/guides")
public class GuidesController {
    @Autowired
    private IGuidesService guidesService;

    @Autowired
    private GuidesServiceImpl guidesServiceImpl;

    /**
     * 查询省份对应的攻略
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result queryProvincesById(@PathVariable Long id) {
        List<Guides> guides = guidesService.queryProvincesByIds(id);
        return Result.ok(guides);
    }

    /**
     * 查询攻略仔细信息
     * @param id
     * @return
     */
    @GetMapping
    public Result CheckGuides(Long id) {
        Guides guide = guidesServiceImpl.query().eq("id", id).one();
        return Result.ok(guide);
    }
}
