package com.wtlx.service;

import com.wtlx.domain.entity.dto.ai.ScenicInfoVO;

import java.util.List;

/**
 * 景点 AI 详情服务：文案基于站内 RAG 知识整理、图片取自站内素材，人工零录入。
 *
 * @author 金小梁
 */
public interface IScenicInfoService {

    /**
     * 按景点名获取 AI 详情；未生成过则触发后台生成并立即返回 PENDING，
     * 前端轮询同一接口直到 DONE / FAILED。
     *
     * @param name 景点名称（行程条目中【】标注的真实 POI 名）
     */
    ScenicInfoVO getOrGenerate(String name);

    /**
     * 预生成：AI 生成行程、写入行程条目后调用，把首次打开详情页的等待挪到后台。
     *
     * @param names 景点名称集合，已有的缓存会被跳过
     */
    void pregenerate(List<String> names);

    /**
     * 清除某个景点的缓存，下次请求会重新检索站内资料并生成。
     * 站内游记/攻略更新后需要刷新景点资料时使用。
     *
     * @param name 景点名称
     * @return 是否确实删除了缓存
     */
    boolean evict(String name);
}
