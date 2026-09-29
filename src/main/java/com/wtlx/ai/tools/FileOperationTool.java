package com.wtlx.ai.tools;

import cn.hutool.core.io.FileUtil;
import com.wtlx.ai.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 文件操作工具
 */
@Slf4j
public class FileOperationTool {

    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of the file to read") String fileName) {
        log.info("开始读取文件: {}", fileName);
        String filePath = FILE_DIR + "/" + fileName;
        try {
            String content = FileUtil.readUtf8String(filePath);
            log.info("文件读取成功，大小: {} 字符", content != null ? content.length() : 0);
            return content;
        } catch (Exception e) {
            log.error("文件读取失败: {}", e.getMessage(), e);
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(
        @ToolParam(description = "Name of the file to write") String fileName,
        @ToolParam(description = "Content to write to the file") String content) {
        log.info("开始写入文件: {}，内容大小: {} 字符", fileName, content.length());
        String filePath = FILE_DIR + "/" + fileName;
        try {
            
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath);
            log.info("文件写入成功，路径: {}", filePath);
            return "File written successfully to: " + filePath;
        } catch (Exception e) {
            log.error("文件写入失败: {}", e.getMessage(), e);
            return "Error writing to file: " + e.getMessage();
        }
    }
}