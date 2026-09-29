package com.wtlx.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 旅游应用文档加载器
 *
 * 【什么是文档加载器？】
 * 文档加载器是 RAG 系统的数据入口，负责将外部知识源（文件、数据库、API 等）
 * 转换为 AI 可以处理的 Document 对象。每个 Document 包含内容和对应该的元数据。
 *
 * 【本类功能】
 * 从 classpath 目录加载 Markdown 格式的旅游知识库文档
 * - 扫描 classpath:document/*.md 下的所有 Markdown 文件
 * - 自动提取文件名中的 status 标签（如"国内"、"国外"、"准备"）作为元数据
 * - 将长文档按标题分割为多个独立的 Document 便于后续检索
 *
 * 【元数据说明】
 * 每个加载的文档都会附加以下元数据：
 * - filename: 原始文件名，便于追溯文档来源
 * - status: 从文件名提取的标签，用于按旅游场景过滤
 *   文件名格式需为：*-XX.md（如"云南旅游-国内.md"）
 *
 * 【配置说明】
 * - withHorizontalRuleCreateDocument(true): 用水平线分割文档，生成多个独立文档
 * - withIncludeCodeBlock(false): 不包含代码块内容
 * - withIncludeBlockquote(false): 不包含引用块内容
 *
 * 【使用示例】
 * List<Document> documents = tripDocumentLoader.loadMarkdowns();
 * vectorStore.add(documents);
 */
@Component
@Slf4j
public class TripDocumentLoader {

    /**
     * Spring 资源模式解析器，用于扫描 classpath 下的文件
     * <p>
     * 注：本类涉及 org.springframework.core.io.Resource 资源类型，
     * 与 @Resource 注解重名，此处保留构造器注入。
     */
    private final ResourcePatternResolver resourcePatternResolver;

    /**
     * 构造器注入资源模式解析器
     *
     * @param resourcePatternResolver Spring 资源模式解析器
     */
    public TripDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载多篇 Markdown 文档
     *
     * 扫描 classpath:document/ 目录下的所有 .md 文件，
     * 并将每个文件转换为 Document 对象列表。
     *
     * @return 文档列表，每个 Document 包含内容和元数据
     */
    public List<Document> loadMarkdowns() {
        // 用于存储所有加载的文档
        List<Document> allDocuments = new ArrayList<>();

        try {
            // 扫描 classpath:document/*.md 下的所有 Markdown 文件
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");

            // 遍历每个文件并加载
            for (Resource resource : resources) {
                String filename = resource.getFilename();

                // 从文件名提取 status 元数据
                // 约定文件名格式：*-XX.md，从倒数第6到第4个字符提取标签
                // 例如："云南旅游-国内.md" -> "国内"
                String status = "";
                if (filename != null && filename.length() >= 6) {
                    status = filename.substring(filename.length() - 6, filename.length() - 4);
                }

                // 配置 Markdown 文档解析器
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        // 水平线（---）作为文档分割符，将长文档分割为多个独立文档
                        .withHorizontalRuleCreateDocument(true)
                        // 不包含代码块内容，减少噪音
                        .withIncludeCodeBlock(false)
                        // 不包含引用块内容，减少噪音
                        .withIncludeBlockquote(false)
                        // 附加元数据：文件名
                        .withAdditionalMetadata("filename", filename)
                        // 附加元数据：从文件名提取的 status 标签
                        .withAdditionalMetadata("status", status)
                        .build();

                // 创建 Markdown 文档读取器并解析文档
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                List<Document> documents = reader.read();

                // 将解析后的文档添加到列表
                allDocuments.addAll(documents);

                // 打印加载日志
                log.info("Loaded {} documents from {}", documents.size(), filename);
            }
        } catch (Exception e) {
            // 记录加载失败日志
            log.error("Error loading markdown documents", e);
        }

        return allDocuments;
    }
}
