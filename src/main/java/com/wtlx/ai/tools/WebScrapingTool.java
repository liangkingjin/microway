package com.wtlx.ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具
 */
@Slf4j
public class WebScrapingTool {

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        log.info("开始抓取网页: {}", url);

        try {
            Document document = Jsoup.connect(url).get();
            String html = document.html();

            log.info("网页抓取成功，内容长度: {} 字符", html != null ? html.length() : 0);
            return html;
        } catch (Exception e) {
            log.error("网页抓取失败: {}", e.getMessage(), e);
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
