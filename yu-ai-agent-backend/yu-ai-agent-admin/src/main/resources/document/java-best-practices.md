# Java 开发最佳实践

## 编码规范

### 命名约定

遵循 Java 社区公认的命名规范：

| 元素 | 规范 | 示例 |
|------|------|------|
| 类名 | 大驼峰（PascalCase） | `UserService`, `OrderController` |
| 方法名 | 小驼峰（camelCase） | `findByUsername()`, `getTotalPrice()` |
| 常量 | 全大写蛇形（UPPER_SNAKE） | `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT` |
| 包名 | 全小写，点分隔 | `com.example.user.service` |

### 使用 Lombok 简化代码

```java
// 不推荐：大量样板代码
public class User {
    private String name;
    private int age;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    // ... 更多 getter/setter
}

// 推荐：使用 Lombok
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private String name;
    private int age;
}
```

## 集合操作

### 优先使用 Stream API

```java
// 不推荐：传统 for 循环
List<String> activeUserNames = new ArrayList<>();
for (User user : users) {
    if (user.isActive()) {
        activeUserNames.add(user.getName());
    }
}

// 推荐：Stream API
List<String> activeUserNames = users.stream()
    .filter(User::isActive)
    .map(User::getName)
    .toList();
```

### 选择合适的集合类型

- **需要有序、可重复**：`ArrayList`
- **需要去重**：`HashSet`
- **需要排序**：`TreeSet` 或 `stream().sorted()`
- **键值对**：`HashMap`（无序）或 `LinkedHashMap`（有序）
- **线程安全**：`ConcurrentHashMap` 或 `CopyOnWriteArrayList`

## 异常处理

### 原则

1. 不要捕获后不做任何处理（吞异常）
2. 不要使用异常控制业务流程
3. 抛出具体异常而非 `Exception`
4. 在合适的层级处理异常

### 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", message));
    }
}
```

## 并发编程

### 使用 CompletableFuture

```java
// 并行调用多个服务
CompletableFuture<User> userFuture = CompletableFuture
    .supplyAsync(() -> userService.findById(userId));

CompletableFuture<List<Order>> ordersFuture = CompletableFuture
    .supplyAsync(() -> orderService.findByUserId(userId));

// 等待所有结果
CompletableFuture.allOf(userFuture, ordersFuture).join();
```

### 线程池配置

```java
@Bean
public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("async-");
    executor.setRejectedExecutionHandler(new CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

## 性能优化

### 1. 字符串拼接

```java
// 不推荐：循环中使用 +
String result = "";
for (int i = 0; i < 1000; i++) {
    result += "item" + i;  // 每次创建新 String 对象
}

// 推荐：使用 StringBuilder
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 1000; i++) {
    sb.append("item").append(i);
}
String result = sb.toString();
```

### 2. 避免不必要的装箱

```java
// 不推荐：自动装箱
Long sum = 0L;  // 每次加法都会装箱
for (long i = 0; i < 100000; i++) {
    sum += i;
}

// 推荐：使用基本类型
long sum = 0L;
for (long i = 0; i < 100000; i++) {
    sum += i;
}
```

### 3. 合理使用缓存

对于频繁访问且不常变化的数据，使用本地缓存：

```java
@Cacheable(value = "users", key = "#id")
public User getUserById(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
}
```

## 安全实践

1. **敏感信息加密**：数据库密码、API Key 等使用环境变量或密钥管理服务
2. **输入校验**：所有外部输入都要校验，使用 `@Valid` + Bean Validation
3. **SQL 注入防护**：使用参数化查询，禁止拼接 SQL
4. **XSS 防护**：对输出内容进行转义
5. **CORS 配置**：限制允许的源、方法和头部

## 测试

### 单元测试

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldReturnUserWhenExists() {
        User user = new User(1L, "test");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.getUserById(1L);

        assertEquals("test", result.getUsername());
    }
}
```

## 小结

遵循这些最佳实践，能显著提升代码的可读性、可维护性和性能。在实际开发中，结合团队规范和项目特点灵活运用。
