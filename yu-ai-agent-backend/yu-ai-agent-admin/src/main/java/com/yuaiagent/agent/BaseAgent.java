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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
    /** 每个 chatId 对应一个信号量（容量1），用于排队而非拒绝 */
    private static final ConcurrentHashMap<String, Semaphore> CHAT_SEMAPHORES = new ConcurrentHashMap<>();
    /** 排队等待超时时间（秒） */
    private static final int QUEUE_TIMEOUT_SECONDS = 30;

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

        // 并发排队：同一 chatId 串行执行，新请求等待而非拒绝
        if (StrUtil.isNotBlank(chatId))
        {
            Semaphore semaphore = CHAT_SEMAPHORES.computeIfAbsent(chatId, k -> new Semaphore(1));
            try
            {
                if (!semaphore.tryAcquire(QUEUE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                {
                    throw new RuntimeException("该对话排队超时，请稍后再试 (chatId: " + chatId + ")");
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待排队被中断 (chatId: " + chatId + ")");
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
            // 3、释放信号量、清理资源
            if (StrUtil.isNotBlank(chatId))
            {
                Semaphore semaphore = CHAT_SEMAPHORES.get(chatId);
                if (semaphore != null) {
                    semaphore.release();
                }
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

        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时

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
            boolean acquired = false;
            try {
                // 并发排队：同一 chatId 串行执行，新请求等待而非拒绝
                if (StrUtil.isNotBlank(chatId)) {
                    Semaphore semaphore = CHAT_SEMAPHORES.computeIfAbsent(chatId, k -> new Semaphore(1));
                    if (!semaphore.tryAcquire(0, TimeUnit.SECONDS)) {
                        // 立即拿不到锁，通知前端正在排队
                        sseEmitter.send("⏳ 已有请求正在处理，正在排队等待中...");
                        if (!semaphore.tryAcquire(QUEUE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            sseEmitter.send("⏳ 排队超时，请稍后再试");
                            sseEmitter.send("[DONE]");
                            sseEmitter.complete();
                            return;
                        }
                    }
                    acquired = true;
                }

                // 2、异步执行代理，更改状态
                this.state = AgentState.RUNNING;

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
                // 3、释放信号量、清理资源
                if (acquired && StrUtil.isNotBlank(chatId))
                {
                    Semaphore semaphore = CHAT_SEMAPHORES.get(chatId);
                    if (semaphore != null) {
                        semaphore.release();
                    }
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
