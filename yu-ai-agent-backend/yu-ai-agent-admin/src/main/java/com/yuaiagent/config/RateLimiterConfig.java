package com.yuaiagent.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API 限流配置
 * 基于 Caffeine 的滑动窗口限流，按 IP 限制 AI 接口的请求频率
 */
@Configuration
@Slf4j
public class RateLimiterConfig
{
    /** 每个 IP 每分钟最大请求数 */
    private static final int MAX_REQUESTS_PER_MINUTE = 30;

    /** 限流缓存：IP → 当前窗口的请求计数 */
    private final Cache<String, AtomicInteger> requestCounts;

    public RateLimiterConfig()
    {
        this.requestCounts = Caffeine.newBuilder()
                .maximumSize(10000)                    // 最多记录 10000 个 IP
                .expireAfterWrite(1, TimeUnit.MINUTES) // 1 分钟窗口自动过期
                .build();
    }

    @Bean
    public FilterRegistrationBean<Filter> rateLimiterFilter()
    {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new Filter()
        {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException
            {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;

                // 只对 AI 接口限流
                String uri = httpRequest.getRequestURI();
                if (uri.startsWith("/api/ai/"))
                {
                    String clientIp = getClientIp(httpRequest);
                    AtomicInteger count = requestCounts.get(clientIp, k -> new AtomicInteger(0));

                    if (count.incrementAndGet() > MAX_REQUESTS_PER_MINUTE)
                    {
                        log.warn("IP {} 请求过于频繁，已限流", clientIp);
                        httpResponse.setStatus(429);
                        httpResponse.setContentType("application/json;charset=UTF-8");
                        httpResponse.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
                        return;
                    }
                }

                chain.doFilter(request, response);
            }
        });
        registrationBean.addUrlPatterns("/api/ai/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    /**
     * 获取客户端真实 IP（考虑代理）
     */
    private String getClientIp(HttpServletRequest request)
    {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
        {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
        {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个
        if (ip != null && ip.contains(","))
        {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
