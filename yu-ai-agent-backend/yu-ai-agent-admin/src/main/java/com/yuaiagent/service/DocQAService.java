package com.yuaiagent.service;

import com.yuaiagent.advisor.MyLoggerAdvisor;
import com.yuaiagent.chatmemory.RedisChatMemory;
import com.yuaiagent.rag.DocQARagCustomAdvisorFactory;
import com.yuaiagent.rag.LLMReranker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 描述：智能文档助手 service 层
 */
@Component
@Slf4j
public class DocQAService
{
    private final ChatClient chatClient;
    private final ChatClient.Builder chatClientBuilder;
    private final ChatMemory chatMemory;

    private static final String SYSTEM_PROMPT = """
            你是一个专业的技术文档助手，专门帮助用户理解和查阅技术文档。
            你可以回答关于编程语言、框架、工具、最佳实践等技术问题。
            请基于提供的文档内容给出准确、清晰的回答。
            如果文档中没有相关信息，请诚实告知用户，并给出相关建议。
            回答时请注明参考的文档来源和章节，例如"根据《文档名》的「章节名」..."。
            """;

    public DocQAService(ChatModel openAiChatModel, RedisChatMemory redisChatMemory)
    {
        this.chatMemory = redisChatMemory;
        this.chatClientBuilder = ChatClient.builder(openAiChatModel);
        chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    public List<RedisChatMemory.ConversationSummary> listConversations() {
        if (chatMemory instanceof RedisChatMemory) {
            return ((RedisChatMemory) chatMemory).listConversations();
        }
        return List.of();
    }

    public List<String> listChatIds() {
        if (chatMemory instanceof RedisChatMemory) {
            return ((RedisChatMemory) chatMemory).listConversationIds();
        }
        return List.of();
    }

    public void clearChat(String chatId) {
        chatMemory.clear(chatId);
    }

    public List<Message> getChatMessages(String chatId) {
        return chatMemory.get(chatId);
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     */
    public String doChat(String message, String chatId)
    {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     */
    public Flux<String> doChatByStream(String message, String chatId)
    {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    /** 定义类型 **/
    record DocQAReport(String title, List<String> suggestions)
    {
    }

    /**
     * AI 报告功能（实战结构化输出）
     */
    public DocQAReport doChatWithReport(String message, String chatId)
    {
        DocQAReport report = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成总结报告，标题为文档问答总结，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(DocQAReport.class);
        log.info("report: {}", report);
        return report;
    }

    /** AI 知识库问答功能 **/
    @Resource
    private VectorStore docQAVectorStore;

    @Resource
    private Advisor docQARagCloudAdvisor;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private LLMReranker llmReranker;

    /** 增强版 RAG Advisor（懒加载，首次使用时构建） */
    private Advisor enhancedRagAdvisor;

    /**
     * 获取增强版 RAG Advisor（多查询扩展 + LLM Reranking）
     */
    private Advisor getEnhancedRagAdvisor()
    {
        if (enhancedRagAdvisor == null)
        {
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
     * 和 RAG 知识库进行对话（增强版：多查询扩展 + LLM Reranking + 来源标注）
     */
    public String doChatWithRag(String message, String chatId)
    {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(getEnhancedRagAdvisor())
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /** AI 调用工具能力 **/
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 报告功能（支持调用工具）
     */
    public String doChatWithTools(String message, String chatId)
    {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /** AI 调用 MCP 服务 **/
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 报告功能（调用 MCP 服务）
     */
    public String doChatWithMcp(String message, String chatId)
    {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
