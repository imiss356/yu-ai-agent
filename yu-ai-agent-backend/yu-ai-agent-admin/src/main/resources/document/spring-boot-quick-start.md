# Spring Boot 3 快速入门指南

## 概述

Spring Boot 3 是基于 Spring Framework 6 的全新版本，要求 **Java 17+**，推荐使用 **Java 21**。本指南将带你快速了解 Spring Boot 3 的核心特性与最佳实践。

## 环境搭建

### 系统要求

- JDK 17 或更高版本（推荐 JDK 21）
- Maven 3.6.3+ 或 Gradle 7.5+
- IDE：IntelliJ IDEA 2022.1+ 或 VS Code

### 快速创建项目

使用 Spring Initializr 创建项目：

```bash
curl https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.5.0 \
  -d baseDir=my-app \
  -d groupId=com.example \
  -d artifactId=my-app \
  -d dependencies=web,data-jpa,mysql \
  -o my-app.zip
```

## 核心特性

### 1. 自动配置（Auto-Configuration）

Spring Boot 通过 `@SpringBootApplication` 注解开启自动配置，它包含了三个核心注解：

- `@SpringBootConfiguration`：标记配置类
- `@EnableAutoConfiguration`：启用自动配置机制
- `@ComponentScan`：扫描当前包及子包的组件

自动配置通过 `spring-boot-autoconfigure` 模块下的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件来声明。

### 2. 原生镜像支持（GraalVM Native Image）

Spring Boot 3 原生支持 GraalVM 原生镜像编译，大幅降低内存占用和启动时间：

```bash
# 使用 Maven 构建原生镜像
mvn -Pnative spring-boot:build-image
```

原生镜像启动时间可以从秒级降低到毫秒级，内存占用减少 50% 以上。

### 3. 可观测性（Observability）

Spring Boot 3 内置了 Micrometer 和 Micrometer Tracing，支持：

- **Metrics**：通过 `/actuator/metrics` 端点暴露应用指标
- **Tracing**：集成 OpenTelemetry，支持分布式链路追踪
- **Logging**：支持结构化日志输出

配置示例：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0
```

### 4. 虚拟线程（Virtual Threads）

Spring Boot 3.2+ 支持 Java 21 的虚拟线程，只需简单配置：

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

虚拟线程能显著提升高并发 I/O 密集型应用的吞吐量。

## 数据访问

### JPA 与 MySQL

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String email;
}
```

Repository 接口：

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByEmailContaining(String keyword);
}
```

### 多数据源配置

Spring Boot 支持配置多数据源：

```yaml
spring:
  datasource:
    primary:
      url: jdbc:mysql://localhost:3306/db1
      username: root
      password: 123456
    secondary:
      url: jdbc:mysql://localhost:3306/db2
      username: root
      password: 123456
```

## 常见问题

### Q1：如何排除特定的自动配置类？

使用 `exclude` 属性：

```java
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Q2：Spring Boot 3 与 2.x 的主要区别？

1. **最低 Java 版本**：3.x 要求 Java 17，2.x 支持 Java 8
2. **Jakarta EE 迁移**：`javax.*` → `jakarta.*`
3. **移除过时 API**：删除了 2.x 中标记为 `@Deprecated` 的 API
4. **原生镜像支持**：3.x 原生支持 GraalVM

### Q3：如何优化 Spring Boot 应用启动速度？

1. 启用懒加载：`spring.main.lazy-initialization=true`
2. 减少自动配置类的扫描范围
3. 使用 Spring AOT 预编译
4. 升级到 Java 21 并启用虚拟线程

## 小结

Spring Boot 3 是构建微服务和企业级应用的首选框架。掌握其自动配置原理、数据访问模式、可观测性集成和安全配置，能让你在实际项目开发中游刃有余。
