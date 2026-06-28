package com.yuaiagent.agent.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 工具调用结果缓存
 * 相同工具 + 相同参数的调用，在 TTL 内直接返回缓存结果，避免重复执行
 */
@Component
@Slf4j
public class ToolResultCache
{
    /** 缓存 key → 工具调用结果（ToolResponseMessage） */
    private final Cache<String, ToolResponseMessage> cache;

    public ToolResultCache()
    {
        this.cache = Caffeine.newBuilder()
                .maximumSize(500)                    // 最多缓存 500 条
                .expireAfterWrite(5, TimeUnit.MINUTES)  // 5 分钟过期
                .build();
    }

    /**
     * 生成缓存 key：工具名 + 参数的哈希
     */
    public String generateKey(List<AssistantMessage.ToolCall> toolCalls)
    {
        String raw = toolCalls.stream()
                .map(tc -> tc.name() + ":" + tc.arguments())
                .collect(Collectors.joining("|"));
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            return String.valueOf(raw.hashCode());
        }
    }

    /**
     * 查询缓存
     *
     * @param toolCalls 工具调用列表
     * @return 缓存的 ToolResponseMessage，未命中返回 null
     */
    public ToolResponseMessage get(List<AssistantMessage.ToolCall> toolCalls)
    {
        String key = generateKey(toolCalls);
        ToolResponseMessage cached = cache.getIfPresent(key);
        if (cached != null)
        {
            log.info("工具调用缓存命中，工具: {}", toolCalls.stream()
                    .map(AssistantMessage.ToolCall::name)
                    .collect(Collectors.joining(", ")));
        }
        return cached;
    }

    /**
     * 写入缓存
     *
     * @param toolCalls         工具调用列表
     * @param toolResponseMessage 工具执行结果
     */
    public void put(List<AssistantMessage.ToolCall> toolCalls, ToolResponseMessage toolResponseMessage)
    {
        String key = generateKey(toolCalls);
        cache.put(key, toolResponseMessage);
        log.debug("工具调用结果已缓存，工具: {}", toolCalls.stream()
                .map(AssistantMessage.ToolCall::name)
                .collect(Collectors.joining(", ")));
    }
}
