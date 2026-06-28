package com.yuaiagent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * 描述：创建自定义的 RAG 检索增强顾问的工厂
 * 支持：多查询扩展（双路检索）、LLM Reranking、来源标注
 */
public class DocQARagCustomAdvisorFactory
{
    /**
     * 创建增强版 RAG 检索顾问
     *
     * @param vectorStore    向量存储
     * @param status         文档过滤状态
     * @param chatClientBuilder 用于查询扩展的 ChatClient 构建器
     * @param reranker       LLM 重排序器
     * @return 配置好的 Advisor
     */
    public static Advisor createDocQARagCustomAdvisor(
            VectorStore vectorStore,
            String status,
            ChatClient.Builder chatClientBuilder,
            LLMReranker reranker)
    {
        // 1. 过滤条件：只检索指定状态的文档
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status)
                .build();

        // 2. 向量检索器：topK=5（给 Reranking 留足候选），相似度阈值 0.3
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression)
                .similarityThreshold(0.3)
                .topK(5)
                .build();

        // 3. 多查询扩展：将原始查询扩展为多个变体，提升召回率
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(2)   // 原始查询 + 1 个改写
                .includeOriginal(true)  // 保留原始查询
                .build();

        // 4. 构建 RAG Advisor：查询扩展 → 多路检索 → 结果合并 → Reranking → 上下文注入
        return RetrievalAugmentationAdvisor.builder()
                .queryExpander(queryExpander)
                .documentRetriever(documentRetriever)
                .documentPostProcessors(reranker)   // LLM Reranking
                .queryAugmenter(DocQAContextualQueryAugmenterFactory.createInstance())
                .build();
    }
}
