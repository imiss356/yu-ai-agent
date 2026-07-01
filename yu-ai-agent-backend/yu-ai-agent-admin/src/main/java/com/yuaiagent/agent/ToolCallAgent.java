package com.yuaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yuaiagent.agent.cache.ToolResultCache;
import com.yuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 描述：处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent
{
    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 工具结果缓存
    private final ToolResultCache toolResultCache;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    // 英文技术关键词（使用词边界匹配，避免 "go" 匹配 "going"、"react" 匹配 "reaction"）
    private static final List<Pattern> TECH_KEYWORDS_EN = List.of(
            Pattern.compile("\\bjava\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpython\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bjavascript\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btypescript\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bgo\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\brust\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bc\\+\\+\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bc#\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bspring\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bspring\\s*boot\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bvue\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\breact\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bangular\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bnode\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmysql\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bredis\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmongodb\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpostgresql\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdatabase\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bapi\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsdk\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bframework\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bconfig\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bhow\\s+to\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bwhat\\s+is\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bexplain\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btutorial\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdocumentation\\b", Pattern.CASE_INSENSITIVE)
    );

    // 中文技术关键词（中文没有词边界概念，用 contains 即可）
    private static final List<String> TECH_KEYWORDS_CN = List.of(
            "数据库", "框架", "配置",
            "怎么", "如何", "什么是", "解释", "原理", "教程"
    );

    public ToolCallAgent(ToolCallback[] availableTools, ToolResultCache toolResultCache)
    {
        //调用父类BaseAgent的无参构造函数，初始化继承自父类的成员变量和状态
        super();
        this.availableTools = availableTools;
        this.toolResultCache = toolResultCache;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    // 是否需要继续执行行动
    private boolean shouldActFlag = false;

    @Override
    public boolean shouldAct() {
        return shouldActFlag;
    }

    /**
     * 判断是否为技术文档问题
     * 英文关键词使用词边界正则匹配，中文关键词使用 contains
     */
    private boolean isTechQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        // 英文关键词：词边界正则（已含 CASE_INSENSITIVE）
        boolean enMatch = TECH_KEYWORDS_EN.stream().anyMatch(p -> p.matcher(question).find());
        if (enMatch) {
            return true;
        }
        // 中文关键词：contains 即可
        return TECH_KEYWORDS_CN.stream().anyMatch(question::contains);
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 思考结果
     */
    @Override
    public String think()
    {
        // 1、校验提示词，拼接用户提示词
        if (StrUtil.isNotBlank(getNextStepPrompt()))
        {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }

        // 2、智能路由提示：如果是技术问题，追加引导使用 RAG 的提示
        String userQuestion = getMessageList().stream()
                .filter(m -> m instanceof UserMessage)
                .reduce((a, b) -> b)  // 获取最后一条用户消息
                .map(Message::getText)
                .orElse("");

        if (isTechQuestion(userQuestion)) {
            String ragHint = "This appears to be a technical question. Please use the queryDocument tool first to search the knowledge base.";
            UserMessage hintMessage = new UserMessage(ragHint);
            getMessageList().add(hintMessage);
            log.info("检测到技术问题，已添加 RAG 工具引导提示");
        }

        // 3、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 4、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String thought = assistantMessage.getText();
            log.info(getName() + "的思考：" + thought);

            // 如果不需要调用工具，设置标志为 false
            if (toolCallList.isEmpty())
            {
                shouldActFlag = false;
                // 只有不调用工具时，才需要手动记录助手消息
                getMessageList().add(assistantMessage);
                return "🤔 思考：" + thought;
            } else {
                shouldActFlag = true;
                log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
                String toolCallInfo = toolCallList.stream()
                        .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                        .collect(Collectors.joining("\n"));
                log.info(toolCallInfo);
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return "🤔 思考：" + thought;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            shouldActFlag = false;
            return "❌ 错误：" + e.getMessage();
        }
    }


    /**
     * 执行工具调用并处理结果（带缓存）
     *
     * @return 执行结果
     */
    @Override
    public String act()
    {
        if (!toolCallChatResponse.hasToolCalls())
        {
            return "🛠️ 无需调用工具";
        }

        // 获取工具调用列表
        AssistantMessage assistantMessage = toolCallChatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

        try
        {
            // 1. 先查缓存
            ToolResponseMessage cachedResponse = toolResultCache.get(toolCalls);
            ToolResponseMessage toolResponseMessage;

            if (cachedResponse != null)
            {
                // 缓存命中：将助手消息 + 缓存的工具结果拼接到消息列表
                List<Message> messages = new ArrayList<>(getMessageList());
                messages.add(assistantMessage);
                messages.add(cachedResponse);
                setMessageList(messages);
                toolResponseMessage = cachedResponse;
            }
            else
            {
                // 缓存未命中：正常执行工具调用
                Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
                // 记录消息上下文
                setMessageList(toolExecutionResult.conversationHistory());
                toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
                // 写入缓存（仅缓存成功结果）
                toolResultCache.put(toolCalls, toolResponseMessage);
            }

            // 判断是否调用了终止工具
            boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                    .anyMatch(response -> response.name().equals("doTerminate"));
            if (terminateToolCalled)
            {
                // 任务结束，更改状态
                setState(AgentState.FINISHED);
            }

            String results = toolResponseMessage.getResponses().stream()
                    .map(response -> "🛠️ 正在使用工具 " + response.name() + " 获取信息...")
                    .collect(Collectors.joining("\n"));
            log.info(results);

            return results;
        }
        catch (Exception e)
        {
            log.error("工具执行异常: {}", e.getMessage(), e);
            // 将助手消息（含工具调用意图）和错误信息写入消息列表，让 LLM 下一步能感知到失败
            List<Message> messages = new ArrayList<>(getMessageList());
            messages.add(assistantMessage);
            List<ToolResponseMessage.ToolResponse> errorResponses = toolCalls.stream()
                    .map(tc -> new ToolResponseMessage.ToolResponse(tc.id(), tc.name(),
                            "工具执行失败: " + e.getMessage()))
                    .collect(Collectors.toList());
            ToolResponseMessage errorResponseMessage = new ToolResponseMessage(errorResponses);
            messages.add(errorResponseMessage);
            setMessageList(messages);

            return "❌ 工具执行异常：" + e.getMessage();
        }
    }
}
