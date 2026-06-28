package com.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 LLM 的文档重排序器
 * 对检索到的文档片段，用 LLM 评估与用户问题的相关性并打分，按分数重排序
 */
@Component
@Slf4j
public class LLMReranker implements DocumentPostProcessor
{
    private final ChatModel chatModel;

    /** 重排序后保留的文档数量 */
    private static final int TOP_N = 3;

    /** 打分的 Prompt 模板 */
    private static final String RERANK_PROMPT = """
            你是一个文档相关性评估专家。请评估以下文档片段与用户问题的相关性。

            用户问题：{question}

            文档片段：
            {document}

            请只输出一个 1-10 的整数分数，表示该文档对回答问题的帮助程度：
            - 1-3：完全不相关
            - 4-6：部分相关
            - 7-10：高度相关，能直接回答问题

            只输出数字，不要任何解释。
            """;

    private static final Pattern SCORE_PATTERN = Pattern.compile("\\b(10|[1-9])\\b");

    public LLMReranker(ChatModel chatModel)
    {
        this.chatModel = chatModel;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents)
    {
        if (documents == null || documents.isEmpty())
        {
            return documents;
        }

        String question = query.text();
        log.info("Reranking: 对 {} 个文档片段进行重排序", documents.size());

        // 为每个文档打分
        List<ScoredDocument> scored = new ArrayList<>();
        for (Document doc : documents)
        {
            int score = scoreDocument(question, doc);
            scored.add(new ScoredDocument(doc, score));
        }

        // 按分数降序排序，取 top N
        List<Document> result = scored.stream()
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .limit(TOP_N)
                .map(sd -> sd.doc)
                .collect(Collectors.toList());

        log.info("Reranking 完成: {} -> {} 个文档，最高分: {}，最低分: {}",
                documents.size(), result.size(),
                scored.stream().mapToInt(sd -> sd.score).max().orElse(0),
                scored.stream().mapToInt(sd -> sd.score).min().orElse(0));

        return result;
    }

    /**
     * 用 LLM 为单个文档打分
     */
    private int scoreDocument(String question, Document document)
    {
        try
        {
            // 截取文档前 500 字符，避免 Prompt 过长
            String docText = document.getText();
            if (docText.length() > 500)
            {
                docText = docText.substring(0, 500) + "...";
            }

            String prompt = RERANK_PROMPT
                    .replace("{question}", question)
                    .replace("{document}", docText);

            String response = chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

            return parseScore(response);
        }
        catch (Exception e)
        {
            log.warn("Reranking 打分失败，默认分数 5", e);
            return 5;
        }
    }

    /**
     * 从 LLM 响应中解析分数
     */
    private int parseScore(String response)
    {
        if (response == null)
        {
            return 5;
        }
        Matcher matcher = SCORE_PATTERN.matcher(response.trim());
        if (matcher.find())
        {
            return Integer.parseInt(matcher.group(1));
        }
        return 5;
    }

    /**
     * 带分数的文档包装
     */
    private static class ScoredDocument
    {
        final Document doc;
        final int score;

        ScoredDocument(Document doc, int score)
        {
            this.doc = doc;
            this.score = score;
        }
    }
}
