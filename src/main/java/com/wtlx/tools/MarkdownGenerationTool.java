package com.wtlx.tools;

import cn.hutool.core.io.FileUtil;
import com.wtlx.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Markdown 文件生成工具
 * 用于创建和写入 Markdown 格式的文档
 */
@Slf4j
@Component
public class MarkdownGenerationTool {

    private final String MARKDOWN_DIR = FileConstant.FILE_SAVE_DIR + "/markdown";

    /**
     * 创建 Markdown 文件
     *
     * @param fileName 文件名（不需要 .md 后缀）
     * @param title    文档标题
     * @param content  文档内容（Markdown 格式）
     * @return 操作结果
     */
    @Tool(description = "Create a Markdown file with the given title and content.")
    public String createMarkdown(
            @ToolParam(description = "File name without .md extension") String fileName,
            @ToolParam(description = "Document title") String title,
            @ToolParam(description = "Markdown content") String content) {
        
        log.info("开始创建 Markdown 文件: {}, 标题: {}", fileName, title);
        
        // 确保文件名有 .md 后缀
        String actualFileName = fileName.endsWith(".md") ? fileName : fileName + ".md";
        String filePath = MARKDOWN_DIR + "/" + actualFileName;
        
        try {
            // 创建目录
            FileUtil.mkdir(MARKDOWN_DIR);
            
            // 构建完整的 Markdown 内容
            StringBuilder markdown = new StringBuilder();
            markdown.append("# ").append(title).append("\n\n");
            markdown.append("> 生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
            markdown.append("---\n\n");
            markdown.append(content);
            
            // 写入文件
            FileUtil.writeString(markdown.toString(), filePath, StandardCharsets.UTF_8);
            
            log.info("Markdown 文件创建成功: {}", filePath);
            return "Markdown file created successfully: " + filePath;
        } catch (Exception e) {
            log.error("创建 Markdown 文件失败: {}", e.getMessage(), e);
            return "Error creating Markdown file: " + e.getMessage();
        }
    }

    /**
     * 追加内容到 Markdown 文件
     *
     * @param fileName 文件名
     * @param content  要追加的内容
     * @return 操作结果
     */
    @Tool(description = "Append content to an existing Markdown file.")
    public String appendToMarkdown(
            @ToolParam(description = "File name without .md extension") String fileName,
            @ToolParam(description = "Content to append") String content) {
        
        log.info("开始追加内容到 Markdown 文件: {}", fileName);
        
        String actualFileName = fileName.endsWith(".md") ? fileName : fileName + ".md";
        String filePath = MARKDOWN_DIR + "/" + actualFileName;
        
        try {
            if (!FileUtil.exist(filePath)) {
                return "Error: File does not exist: " + filePath;
            }
            
            // 追加内容
            FileUtil.appendString("\n\n" + content, filePath, StandardCharsets.UTF_8);
            
            log.info("内容追加成功: {}", filePath);
            return "Content appended successfully to: " + filePath;
        } catch (Exception e) {
            log.error("追加内容失败: {}", e.getMessage(), e);
            return "Error appending to Markdown file: " + e.getMessage();
        }
    }

    /**
     * 读取 Markdown 文件内容
     *
     * @param fileName 文件名
     * @return 文件内容
     */
    @Tool(description = "Read the content of a Markdown file.")
    public String readMarkdown(
            @ToolParam(description = "File name without .md extension") String fileName) {
        
        log.info("开始读取 Markdown 文件: {}", fileName);
        
        String actualFileName = fileName.endsWith(".md") ? fileName : fileName + ".md";
        String filePath = MARKDOWN_DIR + "/" + actualFileName;
        
        try {
            if (!FileUtil.exist(filePath)) {
                return "Error: File does not exist: " + filePath;
            }
            
            String content = FileUtil.readString(filePath, StandardCharsets.UTF_8);
            log.info("Markdown 文件读取成功，大小: {} 字符", content.length());
            return content;
        } catch (Exception e) {
            log.error("读取 Markdown 文件失败: {}", e.getMessage(), e);
            return "Error reading Markdown file: " + e.getMessage();
        }
    }

    /**
     * 列出所有 Markdown 文件
     *
     * @return 文件列表
     */
    @Tool(description = "List all Markdown files in the markdown directory.")
    public String listMarkdownFiles() {
        log.info("开始列出所有 Markdown 文件");
        
        try {
            java.util.List<String> files = FileUtil.listFileNames(MARKDOWN_DIR);
            
            if (files.isEmpty()) {
                return "No Markdown files found in: " + MARKDOWN_DIR;
            }
            
            StringBuilder result = new StringBuilder("Markdown files (" + files.size() + "):\n");
            for (String file : files) {
                if (file.endsWith(".md")) {
                    result.append("- ").append(file).append("\n");
                }
            }
            
            return result.toString();
        } catch (Exception e) {
            log.error("列出 Markdown 文件失败: {}", e.getMessage());
            return "Error listing Markdown files: " + e.getMessage();
        }
    }
}
