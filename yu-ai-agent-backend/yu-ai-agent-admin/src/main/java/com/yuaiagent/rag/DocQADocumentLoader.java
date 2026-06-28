package com.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述：智能文档助手文档加载器
 * 使用自定义 MarkdownHeaderTextSplitter 按标题结构切分文档
 */
@Component
@Slf4j
public class DocQADocumentLoader
{
    private final ResourcePatternResolver resourcePatternResolver;
    private final MarkdownHeaderTextSplitter headerSplitter;

    public DocQADocumentLoader(ResourcePatternResolver resourcePatternResolver,
                               MarkdownHeaderTextSplitter headerSplitter)
    {
        this.resourcePatternResolver = resourcePatternResolver;
        this.headerSplitter = headerSplitter;
    }

    /**
     * 加载多篇 Markdown 文档，按标题结构切分
     */
    public List<Document> loadMarkdowns()
    {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                String markdown = resource.getContentAsString(StandardCharsets.UTF_8);
                List<Document> docs = headerSplitter.splitByHeaders(markdown, filename);
                allDocuments.addAll(docs);
                log.info("文档 [{}] 加载完成，切分为 {} 个片段", filename, docs.size());
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        log.info("文档加载总计: {} 个片段", allDocuments.size());
        return allDocuments;
    }
}
