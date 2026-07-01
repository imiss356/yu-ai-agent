package com.yuaiagent.controller;

import com.yuaiagent.chatmemory.RedisChatMemory;
import com.yuaiagent.service.SuperAgentService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 描述：AI 智能体控制层（统一入口）
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private SuperAgentService superAgentService;

    /**
     * 列出所有对话（忽略 type 参数，统一返回）
     */
    @GetMapping("/sessions")
    public List<RedisChatMemory.ConversationSummary> listChatIds(@RequestParam(required = false) String type) {
        return superAgentService.listConversations();
    }

    /**
     * 清空指定对话
     */
    @DeleteMapping("/sessions/{chatId}")
    public boolean clearChat(@PathVariable String chatId, @RequestParam(required = false) String type) {
        superAgentService.clearChat(chatId);
        return true;
    }

    /**
     * 获取指定对话的所有消息
     */
    @GetMapping("/sessions/{chatId}/messages")
    public List<Message> getChatMessages(@PathVariable String chatId, @RequestParam(required = false) String type) {
        return superAgentService.getChatMessages(chatId);
    }

    /**
     * 统一聊天接口（SSE 流式输出）
     * 原 /ai/manus/chat 和 /ai/doc_qa/chat/sse 合并为此接口
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(String message, String chatId) {
        return superAgentService.doChatByStream(message, chatId);
    }

    /**
     * 兼容旧接口 - 重定向到新接口
     */
    @GetMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Deprecated
    public SseEmitter doChatWithManus(String message, String chatId) {
        return superAgentService.doChatByStream(message, chatId);
    }
}
