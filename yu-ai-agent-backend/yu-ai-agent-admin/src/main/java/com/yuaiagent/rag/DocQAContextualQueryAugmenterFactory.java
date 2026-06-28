package com.yuaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

import java.util.List;
import java.util.Map;

/**
 * 描述：创建上下文查询增强器工厂
 * 自定义文档格式化，包含来源信息（文件名、章节标题）
 */
public class DocQAContextualQueryAugmenterFactory
{
    /**
     * 自定义 Prompt 模板，要求 LLM 标注来源
     */
    private static final String PROMPT_TEMPLATE = """
            基于以下检索到的上下文信息回答用户的问题。
            回答时请务必注明参考来源，格式为"根据《文档名》的「章节」"。
            如果上下文中有代码示例，请一并提供。

            上下文信息：
            {context}

            用户问题：{query}
            """;

    /**
     * 空上下文时的提示模板
     */
    private static final String EMPTY_CONTEXT_PROMPT = """
            抱歉，我只能回答技术文档相关的问题，别的没办法帮到您哦。
            请尝试询问关于编程语言、框架、工具或最佳实践方面的问题。
            """;

    public static ContextualQueryAugmenter createInstance()
    {
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .promptTemplate(new PromptTemplate(PROMPT_TEMPLATE))
                .emptyContextPromptTemplate(new PromptTemplate(EMPTY_CONTEXT_PROMPT))
                .documentFormatter(DocQAContextualQueryAugmenterFactory::formatDocuments)
                .build();
    }

    /**
     * 自定义文档格式化：附加来源元数据
     * 格式：[来源: 文件名 > 章节] 文档内容
     */
    private static String formatDocuments(List<Document> documents)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++)
        {
            Document doc = documents.get(i);
            Map<String, Object> metadata = doc.getMetadata();

            String filename = (String) metadata.getOrDefault("filename", "未知文档");
            // 去掉 .md 后缀
            if (filename.endsWith(".md"))
            {
                filename = filename.substring(0, filename.length() - 3);
            }

            String header2 = (String) metadata.getOrDefault("header2", "");
            String header3 = (String) metadata.getOrDefault("header3", "");

            // 构建来源标识
            StringBuilder source = new StringBuilder();
            source.append("[来源: ").append(filename);
            if (!header2.isEmpty())
            {
                source.append(" > ").append(header2);
            }
            if (!header3.isEmpty())
            {
                source.append(" > ").append(header3);
            }
            source.append("]");

            sb.append(source).append("\n").append(doc.getText());
            if (i < documents.size() - 1)
            {
                sb.append("\n\n---\n\n");
            }
        }
        return sb.toString();
    }
}
