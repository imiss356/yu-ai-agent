package com.yuaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiAgentAdminApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(AiAgentAdminApplication.class, args);
        System.out.print("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                               ║");
        System.out.println("║          AI 智能文档助手 - TechDoc AI Assistant                  ║");
        System.out.println("║                                                               ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
