package com.wtlx.controller;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.dto.ai.ScenicInfoVO;
import com.wtlx.service.IScenicInfoService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 景点 AI 详情接口（挂在 /ai/** 下，受登录拦截保护）。
 * <p>
 * 首次请求触发后台生成并返回 PENDING，前端轮询同一接口直至 DONE / FAILED；
 * 生成结果落库缓存，此后直接命中。
 *
 * @author 金小梁
 */
@RestController
@RequestMapping("/ai")
public class ScenicInfoController {

    @Resource
    private IScenicInfoService scenicInfoService;

    @GetMapping("/scenic-info")
    public Result scenicInfo(@RequestParam String name) {
        ScenicInfoVO vo = scenicInfoService.getOrGenerate(name);
        return Result.ok(vo);
    }

    /**
     * 清除某个景点的缓存：站内游记/攻略更新后，用它可以触发重新检索生成。
     */
    @DeleteMapping("/scenic-info")
    public Result evict(@RequestParam String name) {
        return Result.ok(scenicInfoService.evict(name));
    }
}
