package com.yuaiagent.chatmemory;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RedisChatMemory implements ChatMemory {

    private static final String KEY_PREFIX = "chat:memory:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final int MAX_MESSAGES = 40;       // 压缩后保留的消息数
    private static final int COMPRESS_THRESHOLD = 60; // 超过此阈值才触发压缩（滑动窗口 20 条缓冲）
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private ChatModel chatModel;

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;
        for (Message msg : messages) {
            try {
                String json = objectMapper.writeValueAsString(toDto(msg));
                stringRedisTemplate.opsForList().rightPush(key, json);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize chat message", e);
            }
        }
        // 超过上限则压缩旧消息
        Long size = stringRedisTemplate.opsForList().size(key);
        if (size != null && size > COMPRESS_THRESHOLD) {
            compressHistory(key, size);
        }
        // 每次写入刷新 TTL，实现活跃对话保留、闲置自动清理
        stringRedisTemplate.expire(key, TTL);
    }

    /**
     * 裁剪旧消息并生成摘要，保留最近 MAX_MESSAGES 条
     */
    private void compressHistory(String key, Long size) {
        int removeCount = size.intValue() - MAX_MESSAGES;
        List<String> oldChunks = stringRedisTemplate.opsForList().range(key, 0, removeCount - 1);
        // 原子裁剪
        stringRedisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
        if (oldChunks != null && !oldChunks.isEmpty()) {
            String summary = generateSummary(oldChunks);
            try {
                ChatMessageDTO summaryDto = new ChatMessageDTO(summary, "SUMMARY");
                stringRedisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(summaryDto));
            } catch (Exception e) {
                // 摘要插入失败不影响主流程
            }
        }
    }

    /**
     * 优先用大模型生成智能摘要，失败则回退到简单拼接
     */
    private String generateSummary(List<String> oldJsons) {
        if (chatModel != null) {
            try {
                return buildLLMSummary(oldJsons);
            } catch (Exception e) {
                // LLM 调用失败，回退到简单摘要
            }
        }
        return buildSimpleSummary(oldJsons);
    }

    /**
     * 调用大模型生成一句话摘要
     */
    private String buildLLMSummary(List<String> oldJsons) {
        StringBuilder dialog = new StringBuilder();
        for (String json : oldJsons) {
            try {
                ChatMessageDTO dto = objectMapper.readValue(json, ChatMessageDTO.class);
                String speaker = switch (dto.getRole()) {
                    case "USER" -> "用户";
                    case "ASSISTANT" -> "助手";
                    case "SUMMARY" -> "历史摘要";
                    default -> null;
                };
                if (speaker != null && dto.getContent() != null) {
                    dialog.append(speaker).append("：")
                            .append(dto.getContent().length() > 100
                                    ? dto.getContent().substring(0, 100)
                                    : dto.getContent())
                            .append("\n");
                }
            } catch (Exception e) { /* skip */ }
        }
        if (dialog.isEmpty()) return "[历史对话已归档]";

        String prompt = "以下是一段技术对话记录，请用一句话（不超过40字）总结用户主要问了哪些问题：\n\n" + dialog;
        String result = chatModel.call(new Prompt(new UserMessage(prompt)))
                .getResult().getOutput().getText();
        return "[历史摘要] " + (result.length() > 50 ? result.substring(0, 50) : result);
    }

    /**
     * 简单拼接用户问题作为摘要（LLM 不可用时的回退方案）
     */
    private String buildSimpleSummary(List<String> oldJsons) {
        List<String> questions = new ArrayList<>();
        for (String json : oldJsons) {
            try {
                ChatMessageDTO dto = objectMapper.readValue(json, ChatMessageDTO.class);
                if ("USER".equals(dto.getRole()) && dto.getContent() != null) {
                    String q = dto.getContent().length() > 40
                            ? dto.getContent().substring(0, 40) + "..."
                            : dto.getContent();
                    questions.add(q);
                }
            } catch (Exception e) { /* skip */ }
        }
        if (questions.isEmpty()) return "[历史对话已归档]";
        if (questions.size() <= 2) {
            return "[历史归档] " + String.join("；", questions);
        }
        return "[历史归档] " + questions.get(0) + "；"
                + questions.get(questions.size() - 1)
                + " 等" + questions.size() + "轮对话";
    }

    @Override
    public List<Message> get(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        List<String> jsons = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (jsons == null || jsons.isEmpty()) return List.of();
        return jsons.stream()
                .map(json -> {
                    try {
                        return toMessage(objectMapper.readValue(json, ChatMessageDTO.class));
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void clear(String conversationId) {
        stringRedisTemplate.delete(KEY_PREFIX + conversationId);
    }

    public record ConversationSummary(String id, String title) {}

    public List<ConversationSummary> listConversations() {
        Set<String> keys = stringRedisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null) return List.of();
        List<ConversationSummary> summaries = new ArrayList<>();
        for (String key : keys) {
            String id = key.substring(KEY_PREFIX.length());
            String title = "新对话";
            List<String> head = stringRedisTemplate.opsForList().range(key, 0, 5);
            if (head != null) {
                for (String json : head) {
                    try {
                        ChatMessageDTO dto = objectMapper.readValue(json, ChatMessageDTO.class);
                        if ("USER".equals(dto.getRole()) && dto.getContent() != null) {
                            title = dto.getContent().length() > 20
                                    ? dto.getContent().substring(0, 20) + "..."
                                    : dto.getContent();
                            break;
                        }
                    } catch (Exception e) { /* skip */ }
                }
            }
            summaries.add(new ConversationSummary(id, title));
        }
        return summaries;
    }

    public List<String> listConversationIds() {
        Set<String> keys = stringRedisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null) return List.of();
        return keys.stream()
                .map(k -> k.substring(KEY_PREFIX.length()))
                .collect(Collectors.toList());
    }

    private ChatMessageDTO toDto(Message message) {
        String role = switch (message.getMessageType()) {
            case USER -> "USER";
            case ASSISTANT -> "ASSISTANT";
            case SYSTEM -> "SYSTEM";
            default -> "TOOL";
        };
        return new ChatMessageDTO(message.getText(), role);
    }

    private Message toMessage(ChatMessageDTO dto) {
        return switch (dto.getRole()) {
            case "USER" -> new UserMessage(dto.getContent());
            case "ASSISTANT" -> new AssistantMessage(dto.getContent());
            case "SYSTEM", "SUMMARY" -> new SystemMessage(dto.getContent());
            default -> new AssistantMessage(dto.getContent());
        };
    }
}
