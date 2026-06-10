# Spring AI 框架使用指南

## 概述

Spring AI 是 Spring 生态的 AI 集成框架，旨在简化 AI 大模型与 Java 应用的集成。它提供了统一的 API 来对接不同的 AI 服务提供商，包括 OpenAI、Azure OpenAI、阿里云 DashScope、Ollama 等。

## 核心概念

### ChatClient

`ChatClient` 是 Spring AI 的核心入口，提供流式 API 来构建和发送提示词：

```java
@Bean
public ChatClient chatClient(ChatModel chatModel) {
    return ChatClient.builder(chatModel).build();
}

// 使用
String response = chatClient.prompt()
    .user("Spring Boot 的核心特性有哪些？")
    .call()
    .content();
```

### ChatModel

`ChatModel` 是抽象的大模型接口，不同的服务商提供各自的实现：

- **DashScope**：阿里云通义千问
- **OpenAI**：GPT 系列模型
- **Ollama**：本地部署的开源模型

配置示例（DashScope）：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
```

### Advisor（拦截器）

Advisor 是 Spring AI 的拦截器机制，可以在请求前后执行自定义逻辑：

```java
// 对话记忆 Advisor
MessageChatMemoryAdvisor.builder(chatMemory).build();

// 日志 Advisor
new MyLoggerAdvisor();

// Re-Reading Advisor（增强推理）
new ReReadingAdvisor();
```

多个 Advisor 会按添加顺序组成处理链。

## RAG（检索增强生成）

### 什么是 RAG

RAG 是将信息检索与文本生成相结合的技术。在回答用户问题前，先从知识库中检索相关文档，然后将文档内容作为上下文提供给大模型。

### RAG 工作流程

1. **文档加载**（ETL 的 Extract）
2. **文档切割**（ETL 的 Transform）
3. **向量化存储**（ETL 的 Load）
4. **查询检索**
5. **上下文增强**
6. **生成回答**

### 文档加载示例

```java
@Component
public class DocumentLoader {
    private final ResourcePatternResolver resolver;

    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        Resource[] resources = resolver.getResources("classpath:document/*.md");
        for (Resource resource : resources) {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withAdditionalMetadata("source", resource.getFilename())
                .build();
            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
            allDocuments.addAll(reader.get());
        }
        return allDocuments;
    }
}
```

### 向量存储

Spring AI 支持多种向量数据库：

- **PGVector**：PostgreSQL 扩展，适合中小规模
- **SimpleVectorStore**：内存存储，适合开发测试
- **Elasticsearch**：适合大规模生产环境
- **Redis**：适合低延迟场景

配置 PGVector：

```java
@Bean
public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .dimensions(1536)
        .distanceType(COSINE_DISTANCE)
        .indexType(HNSW)
        .initializeSchema(true)
        .build();
}
```

### 查询增强

使用 `QuestionAnswerAdvisor` 将向量检索集成到对话流程中：

```java
String answer = chatClient.prompt()
    .user(message)
    .advisors(new QuestionAnswerAdvisor(vectorStore))
    .call()
    .content();
```

## Tool Calling（工具调用）

Spring AI 允许大模型在对话过程中调用 Java 方法（工具）：

```java
@Tool(description = "搜索网页内容")
public String searchWeb(@ToolParam(description = "搜索关键词") String query) {
    // 执行搜索逻辑
    return searchResult;
}

// 注册工具
ToolCallback[] tools = MethodToolCallbackProvider.builder()
    .toolObjects(new MyTools())
    .build()
    .getToolCallbacks();

// 对话中使用
chatClient.prompt()
    .user(message)
    .toolCallbacks(tools)
    .call();
```

## MCP（Model Context Protocol）

MCP 是一种标准化协议，用于 AI 模型与外部工具/服务的安全通信。

### MCP 通信模式

- **SSE（Server-Sent Events）**：HTTP 长连接，适合 Web 场景
- **STDIO**：标准输入输出，适合本地进程间通信

### MCP 客户端配置

```json
{
  "mcpServers": {
    "image-search": {
      "url": "http://localhost:8127/sse"
    }
  }
}
```

## Agent 开发模式

### ReAct 模式

ReAct（Reasoning and Acting）是经典的 AI Agent 模式，核心循环为：

1. **Think（思考）**：分析当前状态，决定下一步行动
2. **Act（行动）**：执行工具调用
3. **Observe（观察）**：收集工具执行结果
4. **Repeat**：直到目标完成

```java
public abstract class ReActAgent extends BaseAgent {
    public abstract String think();
    public abstract String act();
    public abstract boolean shouldAct();

    public String step() {
        String thought = think();
        if (!shouldAct()) return thought;
        String actionResult = act();
        return thought + "\n" + actionResult;
    }
}
```

### Agent 状态管理

```java
public enum AgentState {
    IDLE,       // 空闲，等待任务
    RUNNING,    // 执行中
    FINISHED,   // 已完成
    ERROR       // 出错
}
```

## SSE 流式输出

Server-Sent Events 适用于需要实时推送 AI 生成内容的场景：

```java
@GetMapping(value = "/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chatStream(String message) {
    return chatClient.prompt()
        .user(message)
        .stream()
        .content();
}
```

## 小结

Spring AI 大幅简化了 Java 应用中集成 AI 能力的复杂度。通过 ChatClient、RAG、Tool Calling、MCP 和 Agent 模式，可以快速构建功能丰富的 AI 应用。
