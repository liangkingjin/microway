package com.wtlx.ai.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
@Slf4j
@Component
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    @Value("${search-api.api-key}")
    private String apiKey;

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        log.info("开始网页搜索，关键词: {}", query);
        log.info("使用 API Key: {}", apiKey != null && !apiKey.isEmpty() ? "已配置" : "未配置");

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");

        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            log.debug("API 响应: {}", response);

            // 取出返回结果的前 5 条
            JSONObject jsonObject = JSONUtil.parseObj(response);
            // 提取 organic_results 部分
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");

            // 检查结果是否为空
            if (organicResults == null || organicResults.isEmpty()) {
                log.warn("搜索未返回任何结果");
                return "No search results found for query: " + query;
            }

            // 安全地获取前5条结果（实际结果可能少于5条）
            int resultCount = Math.min(organicResults.size(), 5);
            List<Object> objects = organicResults.subList(0, resultCount);

            log.info("搜索完成，结果数量: {}/{}", resultCount, organicResults.size());

            // 拼接搜索结果为字符串
            String result = objects.stream().map(obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                return tmpJSONObject.toString();
            }).collect(Collectors.joining(","));

            return result;
        } catch (Exception e) {
            log.error("网页搜索失败: {}", e.getMessage(), e);
            return "Error searching Baidu: " + e.getMessage();
        }
    }
}
