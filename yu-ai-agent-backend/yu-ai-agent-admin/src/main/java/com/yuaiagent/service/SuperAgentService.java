package com.yuaiagent.service;

import com.yuaiagent.agent.YuManus;
import com.yuaiagent.chatmemory.RedisChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 描述：超级智能体 service 层
 */
@Service
@Slf4j
public class SuperAgentService
{

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    private final ChatMemory chatMemory;

    public SuperAgentService(RedisChatMemory redisChatMemory)
    {
        this.chatMemory = redisChatMemory;
    }

    /**
     * 流式调用 Manus 超级智能体
     */
    public SseEmitter doChatByStream(String message, String chatId)
    {
        YuManus yuManus = new YuManus(allTools, dashscopeChatModel);
        yuManus.setChatId(chatId);
        yuManus.setChatMemory(chatMemory);
        return yuManus.runStream(message);
    }

    public List<Message> getChatMessages(String chatId)
    {
        return chatMemory.get(chatId);
    }

    public List<RedisChatMemory.ConversationSummary> listConversations() {
        if (chatMemory instanceof RedisChatMemory) {
            return ((RedisChatMemory) chatMemory).listConversations();
        }
        return List.of();
    }

    public List<String> listChatIds()
    {
        if (chatMemory instanceof RedisChatMemory)
        {
            return ((RedisChatMemory) chatMemory).listConversationIds();
        }
        return List.of();
    }

    public void clearChat(String chatId)
    {
        chatMemory.clear(chatId);
    }
}
