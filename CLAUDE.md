# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 小技巧

- 后端改动后用 `mvn compile` 快速验证编译，不需要 `package` 整个打包
- Redis ChatMemory 的 `KEYS chat:memory:*` 在 key 量大时会阻塞，目前项目规模可用，上线前应改 `SCAN`
- `application.yml` 中 `spring.ai.dashscope.api-key` 和 `search-api.api-key` 是占位符，本地跑需要替换
- `yu-image-search-mcp-server/` 是独立进程，不随主应用启动，需要单独 run
- 前端 SSE token 通过 URL query param 传递（`?token=xxx`），因为浏览器 EventSource 不支持自定义 Header

---

## Project Overview

TechDoc AI Assistant — 基于 Spring Boot 3 + Spring AI 1.0 的 AI 智能文档助手，包含两个核心能力：
1. **DocQAService**：RAG 文档问答（Query 改写 → 向量检索 → 上下文增强生成）
2. **YuManus**：ReAct 模式自主规划智能体（Think-Act-Observe 循环，可调用 7 种工具 + MCP 远程服务）

前端为 Vue 3 SPA，通过 SSE 实时推送 AI 输出。

---

## Build & Run Commands

```bash
# 后端编译（跳过测试，因为部分测试依赖外部服务如 Redis/MySQL/LLM API）
cd yu-ai-agent-backend/yu-ai-agent-admin
mvn clean package -DskipTests

# 启动后端（默认端口 8123，context-path /api）
java -jar target/yu-ai-agent-admin-0.0.1-SNAPSHOT.jar

# 单测运行
mvn test
mvn test -Dtest=DocQATest          # 运行单个测试类
mvn test -Dtest=DocQATest#testDoChat  # 运行单个测试方法

# 前端开发
cd yu-ai-agent-frontend
npm install
npm run dev      # dev server on localhost:3000
npm run build    # 输出到 dist/
```

---

## Architecture

```
yu-ai-agent-backend/
  yu-ai-agent-admin/          ← 主应用（Spring Boot）
  yu-image-search-mcp-server/ ← 独立 MCP Server（图片搜索，独立进程）
yu-ai-agent-frontend/         ← Vue 3 SPA
```

### 后端分层架构

```
Controller (AiController, AuthController)
    │
Service (DocQAService, SuperAgentService)
    │
Agent 框架 (BaseAgent → ReActAgent → ToolCallAgent → YuManus)  ← SuperAgentService 使用
ChatClient + Advisor 链 (MessageChatMemoryAdvisor, MyLoggerAdvisor)  ← DocQAService 使用
    │
chatmemory/RedisChatMemory    ← Redis List 存储，滑动窗口压缩 + LLM 智能摘要
tools/                         ← 7 种 @Tool 注解工具（WebSearch/FileOp/Terminal/PDF/...）
rag/                           ← 文档加载 → TokenTextSplitter → KeywordMetadataEnricher → 向量存储
```

### 两种 Agent 使用模式

| | DocQAService | SuperAgentService (YuManus) |
|---|---|---|
| 记忆管理 | `MessageChatMemoryAdvisor` 自动托管 | BaseAgent 手动调 `chatMemory.get()/add()` |
| 工具调用 | Spring AI 内置机制（单轮） | 手动 `ToolCallingManager`（ReAct 多步） |
| 输出方式 | `Flux<String>` (reactive) | `SseEmitter` (async push) |
| 调用端点 | `GET /ai/doc_qa/chat/sse` | `GET /ai/manus/chat` |

---

## Key Technical Decisions

### Agent 框架禁用 Spring AI 内置工具调用

```java
// ToolCallAgent.java
this.chatOptions = DashScopeChatOptions.builder()
        .withInternalToolExecutionEnabled(false)  // 必须禁用
        .build();
```

原因：ReAct 循环需要在 think 和 act 之间插入自定义逻辑（状态检查、SSE 推送、终止判断）。禁用后 think() 只获取 tool call 列表不执行，act() 手动调 `toolCallingManager.executeToolCalls()` 并处理 `conversationHistory`。

### Redis 记忆用 StringRedisTemplate 而非 RedisTemplate

泛型擦除问题：`RedisTemplate<String, Object>` + Jackson 取出时反序列化成 `List<LinkedHashMap>` → ClassCastException。用 `StringRedisTemplate` 手动 JSON + `TypeReference` 显式指定类型。

### 滑动窗口压缩（RedisChatMemory）

两个阈值：`MAX_MESSAGES=40`（压缩后保留）、`COMPRESS_THRESHOLD=60`（触发压缩）。避免每 1-2 条消息就触发 LLM 摘要调用。压缩时优先用 LLM 生成智能摘要，失败回退到简单字符串拼接。`SUMMARY` 角色映射为 `SystemMessage` 注入上下文。

### SSE Token 双通道提取

SSE 连接（EventSource）无法自定义请求头，JWT token 通过 query param `?token=xxx` 传递。`JwtAuthenticationFilter` 同时支持 Header 和 query param 两种方式。

---

## Configuration

- **主配置**：`yu-ai-agent-admin/src/main/resources/application.yml`
- **数据源**：`application-druid.yml`（MySQL + Druid 连接池）
- **端口**：8123，context-path：`/api`
- **Redis**：localhost:6379，database 0
- **JWT**：secret 在 application.yml 中，token 有效期 24h
- **API Keys**：DashScope（通义千问）和 SearchAPI 的 key 需要替换 `替换为您的API_KEY`

---

## MCP Server

`yu-image-search-mcp-server/` 是独立的 MCP Server 进程，提供图片搜索能力（Pexels API）。主应用通过 `spring-ai-starter-mcp-client` 连接，配置在 `application.yml` 的 `spring.ai.mcp-client` 段。

---

## Conventions

- **包路径**：`com.yuaiagent`
- **实体/DTO**：用 Lombok `@Data`，JPA 实体有 `@Entity` + `@Table`，DTO 有 Jakarta Validation 注解
- **工具注册**：用 Spring AI `@Tool` 注解标注方法，框架自动注册为 `ToolCallback` Bean
- **Advisor 实现**：实现 `CallAdvisor`（同步）和 `StreamAdvisor`（流式）两个接口
- **chatId 前缀约定**：`doc_`（文档助手）、`super_`（超级智能体），由前端生成，后端不区分
- **前端 API 封装**：`src/api/index.js` 中统一定义，SSE 用 `connectSSE()` 封装 EventSource
- **前端鉴权**：token 存 localStorage，请求拦截器自动注入 `Authorization: Bearer xxx`
- **DocQAService 死代码**：`doChatWithReport`、`doChatWithTools`、`doChatWithMcp` 三个方法在 Controller 中无对应端点，属于学习过程中保留的未接入路径
# 沟通方式
- 默认中文回复；代码、命令、变量名、文件路径保持英文
- 结论先行，简洁直接，不先铺垫背景
- 不谄媚，不夸"这是个很好的问题"，不以"当然可以"开头
- 给真实判断——方案有问题直接指出，发现更好做法主动说明

# Git
- 不自动 git commit 或 git push，除非我明确要求
- 提交前先展示将要提交的变更摘要
- commit message 使用简洁英文

# 红线操作
以下操作即使在 auto-accept 模式下也必须先问我：
- 删除文件、目录或 git 历史
- 修改 .env、密钥、token、证书、CI/CD 配置
- git push、git rebase、git reset --hard、强制推送
- 公开发布（npm publish、生产部署等）
