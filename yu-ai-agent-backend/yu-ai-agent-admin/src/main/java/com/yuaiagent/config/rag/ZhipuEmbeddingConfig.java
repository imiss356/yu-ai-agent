package com.yuaiagent.config.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 描述：智谱 Embedding 模型配置（直接调用智谱 API，绕过 Spring AI 的 /v1 拼接）
 */
@Configuration
public class ZhipuEmbeddingConfig
{
    @Value("${zhipu.embedding.api-key}")
    private String apiKey;

    @Value("${zhipu.embedding.base-url}")
    private String baseUrl;

    @Value("${zhipu.embedding.model}")
    private String model;

    @Bean
    EmbeddingModel zhipuEmbeddingModel()
    {
        return new ZhipuEmbeddingModel(apiKey, baseUrl + "/embeddings");
    }

    /**
     * 自定义 EmbeddingModel 实现，直接调用智谱 API
     */
    static class ZhipuEmbeddingModel implements EmbeddingModel
    {
        private final String apiKey;
        private final String endpoint;
        private final RestTemplate restTemplate = new RestTemplate();

        public ZhipuEmbeddingModel(String apiKey, String endpoint)
        {
            this.apiKey = apiKey;
            this.endpoint = endpoint;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request)
        {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 智谱 API 只支持单条输入，批量处理
            List<float[]> embeddings = new java.util.ArrayList<>();
            List<String> inputs = request.getInstructions();
            int totalTokens = 0;

            for (String input : inputs)
            {
                Map<String, Object> body = Map.of("model", "Embedding-3", "input", input);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(endpoint, entity, Map.class);

                if (response != null && response.containsKey("data"))
                {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                    if (!data.isEmpty())
                    {
                        @SuppressWarnings("unchecked")
                        List<Number> embedding = (List<Number>) data.get(0).get("embedding");
                        float[] arr = new float[embedding.size()];
                        for (int i = 0; i < embedding.size(); i++)
                        {
                            arr[i] = embedding.get(i).floatValue();
                        }
                        embeddings.add(arr);
                    }
                }

                // 统计 token
                if (response != null && response.containsKey("usage"))
                {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> usage = (Map<String, Object>) response.get("usage");
                    totalTokens += ((Number) usage.get("total_tokens")).intValue();
                }
            }

            List<Embedding> embeddingList = new java.util.ArrayList<>();
            for (int i = 0; i < embeddings.size(); i++)
            {
                embeddingList.add(new Embedding(embeddings.get(i), i));
            }

            EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata("zhipu-embedding-3",
                    new org.springframework.ai.chat.metadata.DefaultUsage(totalTokens, totalTokens));

            return new EmbeddingResponse(embeddingList, metadata);
        }

        @Override
        public float[] embed(String text)
        {
            return call(new EmbeddingRequest(List.of(text), null)).getResult().getOutput();
        }

        @Override
        public float[] embed(Document document)
        {
            return embed(document.getText());
        }
    }
}
