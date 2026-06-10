package com.yuaiagent.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class DocQATest
{
    @Resource
    private DocQAService docQAService;

    @Test
    void doChat()
    {
        String chatId = UUID.randomUUID().toString();
        String userMessage = "你好，我是Java程序员";
        String answer = docQAService.doChat(userMessage, chatId);
        userMessage = "Spring Boot 如何配置多数据源？";
        answer = docQAService.doChat(userMessage, chatId);
        userMessage = "我刚才问了什么问题？帮我回忆一下";
        answer = docQAService.doChat(userMessage, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatByStream()
    {
        String chatId = UUID.randomUUID().toString();
        String userMessage = "请介绍一下Spring Boot的核心特性";

        System.out.println("\n--- 流式对话测试开始 ---");
        docQAService.doChatByStream(userMessage, chatId)
                .doOnNext(System.out::print)
                .doOnComplete(System.out::println)
                .blockLast();
        System.out.println("--- 流式对话测试结束 ---\n");
    }

    @Test
    void doChatWithReport()
    {
        String chatId = UUID.randomUUID().toString();
        String message = "请根据Spring Boot最佳实践给我一些建议";
        DocQAService.DocQAReport report = docQAService.doChatWithReport(message, chatId);
        Assertions.assertNotNull(report);
    }

    @Test
    void doChatWithRag()
    {
        String chatId = UUID.randomUUID().toString();
        String message = "Spring Boot 如何快速集成MyBatis？";
        String answer = docQAService.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    private void testMessage(String message)
    {
        String chatId = UUID.randomUUID().toString();
        String answer = docQAService.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools()
    {
        testMessage("搜索一下2024年Java开发的最新趋势");

        testMessage("抓取Spring官方文档中关于IoC容器的内容");

        testMessage("下载一张Java吉祥物Duke的图片");

        testMessage("执行 dir 命令查看当前目录结构");

        testMessage("保存一份Java学习笔记为文件");

        testMessage("生成一份Spring Boot入门指南PDF");
    }

    @Test
    void doChatWithMcp()
    {
        String chatId = UUID.randomUUID().toString();
        String message = "帮我搜索一些技术架构相关的图片";
        String answer = docQAService.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
