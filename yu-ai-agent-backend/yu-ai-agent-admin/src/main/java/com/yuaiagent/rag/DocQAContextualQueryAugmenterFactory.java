package com.yuaiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 描述：创建上下文查询增强器工厂
 */
public class DocQAContextualQueryAugmenterFactory
{
    public static ContextualQueryAugmenter createInstance()
    {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                你应该输出下面的内容：
                抱歉，我只能回答技术文档相关的问题，别的没办法帮到您哦。
                请尝试询问关于编程语言、框架、工具或最佳实践方面的问题。
                """);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}
