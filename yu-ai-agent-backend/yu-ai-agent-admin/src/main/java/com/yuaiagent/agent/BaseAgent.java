package com.yuaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.yuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent
{
    /** 正在运行的 chatId 集合（全局共享，防止同一对话并发执行） */
    private static final ConcurrentHashMap<String, Boolean> RUNNING_CHAT_IDS = new ConcurrentHashMap<>();

    // 核心属性
    private String name;

    // 提示词
    private String systemPrompt;
    private String nextStepPrompt;

    // 代理状态
    private AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;
    private int maxSteps = 10;

    // LLM 大模型
    private ChatClient chatClient;

    // Memory 记忆（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    // 对话 ID 和 记忆
    private String chatId;
    private ChatMemory chatMemory;

    /**
     *  * 运行代理

     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt)
    {
        // 1、基础校验
        if (this.state != AgentState.IDLE)
        {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt))
        {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }

        // 并发去重：同一 chatId 不允许同时执行
        if (StrUtil.isNotBlank(chatId))
        {
            if (RUNNING_CHAT_IDS.putIfAbsent(chatId, Boolean.TRUE) != null)
            {
                throw new RuntimeException("该对话正在执行中，请稍后再试 (chatId: " + chatId + ")");
            }
        }

        // 2、执行，更改状态
        this.state = AgentState.RUNNING;

        // 加载记忆
        if (StrUtil.isNotBlank(chatId) && chatMemory != null)
        {
            List<Message> savedMessages = chatMemory.get(chatId);
            if (savedMessages != null)
            {
                this.messageList = new ArrayList<>(savedMessages);
            }
        }

        // 记录消息上下文
        messageList.add(new UserMessage(userPrompt));
        // 保存结果列表
        List<String> results = new ArrayList<>();
        try {
            // 执行循环
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++)
            {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                // 单步执行
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            // 检查是否超出步骤限制
            if (currentStep >= maxSteps)
            {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }

            // 保存记忆
            if (StrUtil.isNotBlank(chatId) && chatMemory != null) {
                chatMemory.add(chatId, messageList);
            }

            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            // 3、清理资源
            if (StrUtil.isNotBlank(chatId))
            {
                RUNNING_CHAT_IDS.remove(chatId);
            }
            this.cleanup();
        }
    }

    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return SseEmitter 用于发送 SSE 数据
     */
    public SseEmitter runStream(String userPrompt)
    {
        // 1、基础校验
        if (this.state != AgentState.IDLE)
        {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt))
        {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }

        // 并发去重：同一 chatId 不允许同时执行
        if (StrUtil.isNotBlank(chatId))
        {
            if (RUNNING_CHAT_IDS.putIfAbsent(chatId, Boolean.TRUE) != null)
            {
                throw new RuntimeException("该对话正在执行中，请稍后再试 (chatId: " + chatId + ")");
            }
        }

        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 2、异步执行代理，更改状态
        this.state = AgentState.RUNNING;

        // 加载记忆
        if (StrUtil.isNotBlank(chatId) && chatMemory != null) {
            List<Message> savedMessages = chatMemory.get(chatId);
            if (savedMessages != null) {
                this.messageList = new ArrayList<>(savedMessages);
            }
        }

        // 记录消息上下文
        messageList.add(new UserMessage(userPrompt));

        CompletableFuture.runAsync(() -> {
            try {
                // 执行循环
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++)
                {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    // 单步执行
                    String stepResult = step();
                    // 发送步骤结果给客户端
                    sseEmitter.send(stepResult);
                }
                // 检查是否超出步骤限制
                if (currentStep >= maxSteps)
                {
                    state = AgentState.FINISHED;
                    sseEmitter.send("Terminated: Reached max steps (" + maxSteps + ")");
                }
                
                // 保存记忆
                if (StrUtil.isNotBlank(chatId) && chatMemory != null) {
                    chatMemory.add(chatId, messageList);
                }
                
                // 执行结束，发送完成标志
                sseEmitter.send("[DONE]");
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent in stream", e);
                try {
                    sseEmitter.send("执行错误: " + e.getMessage());
                } catch (IOException ex) {
                    log.error("Failed to send error message via SSE", ex);
                }
                sseEmitter.completeWithError(e);
            } finally {
                // 3、清理资源
                if (StrUtil.isNotBlank(chatId))
                {
                    RUNNING_CHAT_IDS.remove(chatId);
                }
                this.cleanup();
            }
        });
        return sseEmitter;
    }

    /**
     * 定义单个步骤
     *
     * @return
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup()
    {
        // 子类可以重写此方法来清理资源
    }
}
