package com.yuaiagent.config.rag;

import com.yuaiagent.rag.DocQADocumentLoader;
import com.yuaiagent.rag.MyKeywordEnricher;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 描述：文档问答向量数据库配置（初始化基于内存的向量数据库 Bean）
 * 文档已由 MarkdownHeaderTextSplitter 按标题结构切分，此处只做关键词增强和向量化存储
 */
@Configuration
@Slf4j
public class DocQAVectorStoreConfig
{
    @Resource
    private DocQADocumentLoader docQADocumentLoader;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Bean
    VectorStore docQAVectorStore(@Qualifier("zhipuEmbeddingModel") EmbeddingModel zhipuEmbeddingModel)
    {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(zhipuEmbeddingModel).build();

        // 1. 加载文档（内部已按标题结构切分）
        List<Document> documentList = docQADocumentLoader.loadMarkdowns();
        log.info("文档切分完成，共 {} 个片段，开始关键词增强...", documentList.size());

        // 2. 关键词增强：为每个片段生成关键词元数据，提升检索召回率
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documentList);
        log.info("关键词增强完成，开始向量化存储...");

        // 3. 存入向量库
        simpleVectorStore.add(enrichedDocuments);
        log.info("向量化存储完成，共 {} 个文档片段", enrichedDocuments.size());

        return simpleVectorStore;
    }
}
