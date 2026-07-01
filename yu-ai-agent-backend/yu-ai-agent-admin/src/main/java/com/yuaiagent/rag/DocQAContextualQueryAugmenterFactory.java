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
     * 注意：这里只告知"未找到"，不拒绝回答，让 Agent 有机会用其他工具（如搜索）补充信息
     */
    private static final String EMPTY_CONTEXT_PROMPT = """
            知识库中未找到与该问题直接相关的文档内容。
            请根据你已有的知识尽可能回答，并告知用户该回答未参考知识库文档。
            如果你认为需要更多信息，可以尝试使用其他工具（如搜索网页）来获取答案。
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
