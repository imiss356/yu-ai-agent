package com.yuaiagent.agent;

import com.yuaiagent.advisor.MyLoggerAdvisor;
import com.yuaiagent.agent.cache.ToolResultCache;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 描述：AI 超级智能体（拥有自主规划能力，可以直接使用）
 */
@Component
public class YuManus extends ToolCallAgent
{
    public YuManus(ToolCallback[] allTools, ChatModel openAiChatModel, ToolResultCache toolResultCache)
    {
        super(allTools, toolResultCache);
        this.setName("yuManus");
        String SYSTEM_PROMPT = """
                You are YuManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.

                ## Tool Selection Rules:

                ### Use queryDocument tool when:
                - Questions about programming languages (Java, Python, JavaScript, etc.)
                - Questions about frameworks (Spring Boot, Vue, React, etc.)
                - Questions about technical concepts, APIs, or documentation
                - Questions starting with "how to", "what is", "explain" about technical topics
                - Examples: "Spring Boot 怎么配置 Redis？", "Vue3 的 Composition API 是什么？"

                ### Use web search when:
                - Real-time information needed (news, prices, weather)
                - General knowledge not related to programming
                - When queryDocument doesn't provide satisfactory answer

                ### Use other tools based on explicit user requests:
                - File operations, terminal commands, PDF generation, etc.

                IMPORTANT: When in doubt about technical questions, ALWAYS try queryDocument FIRST.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
