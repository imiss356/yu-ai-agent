package com.yuaiagent.config.rag;

import com.yuaiagent.rag.DocQADocumentLoader;
import com.yuaiagent.rag.MyKeywordEnricher;
import com.yuaiagent.rag.MyTokenTextSplitter;
import jakarta.annotation.Resource;
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
 */
@Configuration
public class DocQAVectorStoreConfig
{
    @Resource
    private DocQADocumentLoader docQADocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Bean
    VectorStore docQAVectorStore(@Qualifier("zhipuEmbeddingModel") EmbeddingModel zhipuEmbeddingModel)
    {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(zhipuEmbeddingModel).build();

        List<Document> documentList = docQADocumentLoader.loadMarkdowns();

        List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documentList);

        // 暂时跳过关键词增强，直接使用切割后的文档
        // List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(splitDocuments);
        simpleVectorStore.add(splitDocuments);

        return simpleVectorStore;
    }
}
