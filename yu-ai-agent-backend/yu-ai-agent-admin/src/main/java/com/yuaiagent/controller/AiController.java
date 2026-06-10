package com.yuaiagent.controller;

import com.yuaiagent.chatmemory.RedisChatMemory;
import com.yuaiagent.service.DocQAService;
import com.yuaiagent.service.SuperAgentService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import java.io.IOException;
import java.util.List;

/**
 * 描述：AI 智能文档助手控制层
 */
@RestController
@RequestMapping("/ai")
public class AiController
{
    @Resource
    private DocQAService docQAService;

    @Resource
    private SuperAgentService superAgentService;

    /**
     * 列出所有对话
     * @param type 智能体类型 (doc|super)
     */
    @GetMapping("/sessions")
    public List<RedisChatMemory.ConversationSummary> listChatIds(String type)
     {
        if ("super".equals(type))
        {
            return superAgentService.listConversations();
        }
        return docQAService.listConversations();
    }

    /**
     * 清空指定对话
     */
    @DeleteMapping("/sessions/{chatId}")
    public boolean clearChat(@PathVariable String chatId, String type)
     {
        if ("super".equals(type))
        {
            superAgentService.clearChat(chatId);
        } else {
            docQAService.clearChat(chatId);
        }
        return true;
    }

    /**
     * 获取指定对话的所有消息
     */
    @GetMapping("/sessions/{chatId}/messages")
    public List<Message> getChatMessages(@PathVariable String chatId, String type)
     {
        if ("super".equals(type))
        {
            return superAgentService.getChatMessages(chatId);
        }
        return docQAService.getChatMessages(chatId);
    }

    /**
     * 同步调用 AI 文档问答
     */
    @GetMapping("/doc_qa/chat/sync")
    public String doChatWithDocQASync(String message, String chatId)
    {
        return docQAService.doChat(message, chatId);
    }

    /**
     * SSE 流式调用 AI 文档问答
     */
    @GetMapping(value = "/doc_qa/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithDocQASSE(String message, String chatId)
    {
        return docQAService.doChatByStream(message, chatId);
    }

    @GetMapping(value = "/doc_qa/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithDocQAServerSentEvent(String message, String chatId)
    {
        return docQAService.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    @GetMapping(value = "/doc_qa/chat/sse_emitter")
    public SseEmitter doChatWithDocQAServerSseEmitter(String message, String chatId)
    {
        SseEmitter sseEmitter = new SseEmitter(180000L);
        docQAService.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        return sseEmitter;
    }

    /**
     * 流式调用超级智能体
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message, String chatId)
    {
        return superAgentService.doChatByStream(message, chatId);
    }
}
