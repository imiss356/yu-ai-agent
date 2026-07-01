package com.yuaiagent.tools;

import com.yuaiagent.rag.DocQARagCustomAdvisorFactory;
import com.yuaiagent.rag.LLMReranker;
import com.yuaiagent.advisor.MyLoggerAdvisor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * 文档问答工具 - 将 RAG 能力封装为 Agent 可调用的工具
 */
@Component
@Slf4j
public class DocQATool {

    private final ChatClient chatClient;
    private final ChatClient.Builder chatClientBuilder;

    @Resource
    private VectorStore docQAVectorStore;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private LLMReranker llmReranker;

    @Resource
    private ChatMemory chatMemory;

    /** 增强版 RAG Advisor（懒加载） */
    private Advisor enhancedRagAdvisor;

    public DocQATool(ChatModel openAiChatModel) {
        this.chatClientBuilder = ChatClient.builder(openAiChatModel);
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        你是一个专业的技术文档助手，专门帮助用户理解和查阅技术文档。
                        请基于提供的文档内容给出准确、清晰的回答。
                        回答时请注明参考的文档来源和章节。
                        """)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
    }

    /**
     * 获取增强版 RAG Advisor
     */
    private Advisor getEnhancedRagAdvisor() {
        if (enhancedRagAdvisor == null) {
            enhancedRagAdvisor = DocQARagCustomAdvisorFactory.createDocQARagCustomAdvisor(
                    docQAVectorStore,
                    "tech-doc",
                    chatClientBuilder,
                    llmReranker
            );
        }
        return enhancedRagAdvisor;
    }

    /**
     * 查询技术文档知识库
     */
    @Tool(description = "查询技术文档知识库，当用户询问编程、框架、技术相关问题时使用此工具。返回基于文档的准确答案。")
    public String queryDocument(@ToolParam(description = "用户的技术问题") String question) {
        log.info("调用文档问答工具，问题: {}", question);
        try {
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .user(question)
                    .advisors(getEnhancedRagAdvisor())
                    .call()
                    .chatResponse();
            String content = chatResponse.getResult().getOutput().getText();
            log.info("文档问答结果: {}", content);
            return content;
        } catch (Exception e) {
            log.error("文档问答失败: {}", e.getMessage());
            return "文档查询失败: " + e.getMessage();
        }
    }
}
