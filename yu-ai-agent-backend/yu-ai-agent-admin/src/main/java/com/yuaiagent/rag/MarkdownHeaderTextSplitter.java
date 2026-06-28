package com.yuaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Markdown 标题结构的文档切分器
 * 按 H2 级别切分，过长的 section 按 H3 进一步切分
 * 保留代码块，附加标题层级元数据
 */
@Component
public class MarkdownHeaderTextSplitter
{
    /** 最小 chunk 字符数，低于此值与相邻 section 合并 */
    private static final int MIN_CHUNK_CHARS = 100;

    /** 超过此字符数时按 H3 进一步切分 */
    private static final int LARGE_CHUNK_CHARS = 1500;

    /** 用于 TokenTextSplitter 的最终兜底切分 */
    private static final int MAX_CHUNK_TOKENS = 500;

    /** 匹配 Markdown 标题行：# ~ ###### */
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    /** 匹配代码块（围栏式） */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```", Pattern.MULTILINE);

    /**
     * 将 Markdown 原文按标题结构切分为多个 Document
     *
     * @param markdown    Markdown 原文
     * @param filename    文件名，用于元数据
     * @return 切分后的 Document 列表
     */
    public List<Document> splitByHeaders(String markdown, String filename)
    {
        // 先提取所有标题的位置和级别
        List<HeaderInfo> headers = extractHeaders(markdown);

        if (headers.isEmpty())
        {
            // 没有标题，整篇作为一个文档
            Document doc = new Document(markdown);
            doc.getMetadata().put("filename", filename);
            doc.getMetadata().put("status", "tech-doc");
            doc.getMetadata().put("header1", "");
            doc.getMetadata().put("header2", "");
            return List.of(doc);
        }

        // 按 H1 和 H2 切分为 section
        List<Section> sections = splitIntoSections(markdown, headers);

        // 合并过小的 section
        sections = mergeSmallSections(sections);

        // 对过大的 section 按 H3 进一步切分
        List<Section> finalSections = new ArrayList<>();
        for (Section section : sections)
        {
            if (section.content.length() > LARGE_CHUNK_CHARS)
            {
                finalSections.addAll(splitLargeSectionByH3(section));
            }
            else
            {
                finalSections.add(section);
            }
        }

        // 转换为 Document，附加元数据
        List<Document> documents = new ArrayList<>();
        for (Section section : finalSections)
        {
            String content = section.content.trim();
            if (content.isEmpty())
            {
                continue;
            }
            Document doc = new Document(content);
            doc.getMetadata().put("filename", filename);
            doc.getMetadata().put("status", "tech-doc");
            doc.getMetadata().put("header1", section.h1 != null ? section.h1 : "");
            doc.getMetadata().put("header2", section.h2 != null ? section.h2 : "");
            doc.getMetadata().put("header3", section.h3 != null ? section.h3 : "");
            documents.add(doc);
        }

        return documents;
    }

    /**
     * 提取所有标题信息
     */
    private List<HeaderInfo> extractHeaders(String markdown)
    {
        List<HeaderInfo> headers = new ArrayList<>();
        Matcher matcher = HEADER_PATTERN.matcher(markdown);
        while (matcher.find())
        {
            int level = matcher.group(1).length();
            String title = matcher.group(2).trim();
            headers.add(new HeaderInfo(level, title, matcher.start()));
        }
        return headers;
    }

    /**
     * 按 H1/H2 边界切分为 section
     */
    private List<Section> splitIntoSections(String markdown, List<HeaderInfo> headers)
    {
        List<Section> sections = new ArrayList<>();
        String currentH1 = null;
        String currentH2 = null;

        for (int i = 0; i < headers.size(); i++)
        {
            HeaderInfo header = headers.get(i);
            int contentStart = header.endPosition;

            // 计算当前 section 的结束位置
            int contentEnd;
            if (i + 1 < headers.size())
            {
                // 找到下一个同级或更高级的标题
                HeaderInfo nextHeader = headers.get(i + 1);
                if (nextHeader.level <= 2)
                {
                    contentEnd = nextHeader.startPosition;
                }
                else
                {
                    // 下一个是 H3+，继续向后找
                    contentEnd = findNextSectionEnd(markdown, headers, i + 1);
                }
            }
            else
            {
                contentEnd = markdown.length();
            }

            // 更新当前的 H1/H2 上下文
            if (header.level == 1)
            {
                currentH1 = header.title;
                currentH2 = null;
            }
            else if (header.level == 2)
            {
                currentH2 = header.title;
            }

            // 只在 H2 级别创建 section（H1 作为上下文保留）
            if (header.level == 2)
            {
                String content = markdown.substring(contentStart, contentEnd).trim();
                sections.add(new Section(currentH1, header.title, null, content));
            }
        }

        // 如果没有 H2 标题但有 H1，按 H1 切分
        if (sections.isEmpty())
        {
            for (int i = 0; i < headers.size(); i++)
            {
                HeaderInfo header = headers.get(i);
                if (header.level == 1)
                {
                    int contentStart = header.endPosition;
                    int contentEnd = (i + 1 < headers.size()) ? headers.get(i + 1).startPosition : markdown.length();
                    String content = markdown.substring(contentStart, contentEnd).trim();
                    sections.add(new Section(header.title, null, null, content));
                }
            }
        }

        return sections;
    }

    /**
     * 找到下一个 H2 级别标题的位置
     */
    private int findNextSectionEnd(String markdown, List<HeaderInfo> headers, int fromIndex)
    {
        for (int i = fromIndex; i < headers.size(); i++)
        {
            if (headers.get(i).level <= 2)
            {
                return headers.get(i).startPosition;
            }
        }
        return markdown.length();
    }

    /**
     * 对过大的 section 按 H3 进一步切分
     */
    private List<Section> splitLargeSectionByH3(Section section)
    {
        List<Section> result = new ArrayList<>();
        List<HeaderInfo> h3Headers = new ArrayList<>();

        Matcher matcher = HEADER_PATTERN.matcher(section.content);
        while (matcher.find())
        {
            if (matcher.group(1).length() == 3)
            {
                h3Headers.add(new HeaderInfo(3, matcher.group(2).trim(), matcher.start()));
            }
        }

        if (h3Headers.isEmpty())
        {
            // 没有 H3，用 TokenTextSplitter 兜底切分
            result.addAll(tokenBasedSplit(section));
            return result;
        }

        for (int i = 0; i < h3Headers.size(); i++)
        {
            HeaderInfo h3 = h3Headers.get(i);
            int contentStart = h3.endPosition;
            int contentEnd = (i + 1 < h3Headers.size()) ? h3Headers.get(i + 1).startPosition : section.content.length();
            String content = section.content.substring(contentStart, contentEnd).trim();
            result.add(new Section(section.h1, section.h2, h3.title, content));
        }

        return result;
    }

    /**
     * 合并过小的 section
     */
    private List<Section> mergeSmallSections(List<Section> sections)
    {
        if (sections.size() <= 1)
        {
            return sections;
        }

        List<Section> merged = new ArrayList<>();
        Section current = sections.get(0);

        for (int i = 1; i < sections.size(); i++)
        {
            if (current.content.length() < MIN_CHUNK_CHARS)
            {
                // 当前 section 太小，与下一个合并
                Section next = sections.get(i);
                current = new Section(
                    current.h1,
                    current.h2,
                    current.h3,
                    current.content + "\n\n" + next.content
                );
            }
            else
            {
                merged.add(current);
                current = sections.get(i);
            }
        }
        merged.add(current);

        return merged;
    }

    /**
     * 用 TokenTextSplitter 做兜底切分
     */
    private List<Section> tokenBasedSplit(Section section)
    {
        TokenTextSplitter splitter = new TokenTextSplitter(MAX_CHUNK_TOKENS, 100, 10, 5000, true);
        Document doc = new Document(section.content);
        List<Document> splitDocs = splitter.apply(List.of(doc));

        List<Section> result = new ArrayList<>();
        for (Document splitDoc : splitDocs)
        {
            result.add(new Section(section.h1, section.h2, section.h3, splitDoc.getText()));
        }
        return result;
    }

    /**
     * 标题信息
     */
    private static class HeaderInfo
    {
        final int level;
        final String title;
        final int startPosition;

        /** 标题行结束位置（标题文本之后的换行） */
        final int endPosition;

        HeaderInfo(int level, String title, int startPosition)
        {
            this.level = level;
            this.title = title;
            this.startPosition = startPosition;
            // endPosition 跳过标题行本身
            this.endPosition = startPosition + title.length() + level + 1; // # + space + title + \n
        }
    }

    /**
     * 切分后的 section
     */
    static class Section
    {
        final String h1;
        final String h2;
        final String h3;
        final String content;

        Section(String h1, String h2, String h3, String content)
        {
            this.h1 = h1;
            this.h2 = h2;
            this.h3 = h3;
            this.content = content;
        }
    }
}
