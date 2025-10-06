# 🚀 Bajaj Finserv Health – Spring Boot App

This project is a **Spring Boot application** built with **Java 21** that demonstrates interacting with a **remote webhook API**. It automatically executes a workflow on application startup:

1. Sends a `generateWebhook` request with student details.
2. Receives a webhook URL + token response.
3. Submits a **final SQL query payload** to the webhook.
4. Logs the response with proper error handling.

## 📂 Project Structure

```
demo/
 ├─ src/
 │   ├─ main/
 │   │   ├─ java/com/example/demo/
 │   │   │   ├─ Application.java      # Main Spring Boot entrypoint
 │   │   │   ├─ StartupRunner.java    # Runs startup workflow
 │   │   │   └─ SQLSolver.java        # Utility for SQL query handling
 │   │   └─ resources/
 │   │       └─ application.properties # Application configuration
 │   └─ test/                         # Unit tests
 ├─ pom.xml                           # Maven build file
 ├─ mvnw / mvnw.cmd                   # Maven wrapper scripts
 └─ README.md                         # This file
```

## 🛠️ Tech Stack

* **Java 21**
* **Spring Boot 3.5.x**
* **Spring WebFlux (Reactive WebClient)**
* **Maven**
* **Netty (Reactive Web Server)**
* **SLF4J / Logback (Logging)**

## ⚙️ Setup & Run

### 1. Clone the repository

```bash
git clone https://github.com/your-username/bajaj-finserv-health-springboot.git
cd bajaj-finserv-health-springboot/demo
```

### 2. Build the project

```bash
./mvnw clean install
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

Or run directly from your IDE (`Application.java`).

## 📝 Source Code

### pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    
    <groupId>com.example</groupId>
    <artifactId>demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>Bajaj Finserv Health Demo</name>
    <description>Spring Boot application for Bajaj Finserv Health webhook integration</description>
    
    <properties>
        <java.version>21</java.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot Starter WebFlux for reactive programming -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        
        <!-- Spring Boot Starter Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        
        <!-- Reactor Test for testing reactive streams -->
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Application.java

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Bajaj Finserv Health Spring Boot application.
 * 
 * @author Samyak Singh
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### StartupRunner.java

```java
package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Executes the webhook workflow on application startup.
 * 
 * Workflow:
 * 1. Send generateWebhook request with student details
 * 2. Receive webhook URL and access token
 * 3. Submit final SQL query to the webhook
 * 4. Log the response
 * 
 * @author Samyak Singh
 */
@Component
public class StartupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);
    
    private static final String BASE_URL = "https://bfhldevapigw.healthrx.co.in/automation-campus/create/user";
    private static final String NAME = "Samyak Singh";
    private static final String REG_NO = "112215161";
    private static final String EMAIL = "112215161@cse.iiitp.ac.in";
    
    private final WebClient webClient;
    private final SQLSolver sqlSolver;

    public StartupRunner() {
        this.webClient = WebClient.builder().build();
        this.sqlSolver = new SQLSolver();
    }

    @Override
    public void run(String... args) {
        log.info("Starting BFH flow on startup. regNo={}", REG_NO);
        
        // Step 1: Generate webhook
        generateWebhook()
            .flatMap(response -> {
                String webhookUrl = (String) response.get("webhookUrl");
                String accessToken = (String) response.get("accessToken");
                
                log.info("Received webhookUrl={} accessToken={}", webhookUrl, accessToken);
                
                // Step 2: Submit final query
                return submitFinalQuery(webhookUrl, accessToken);
            })
            .doOnSuccess(response -> log.info("Query response: {}", response))
            .onErrorResume(error -> {
                log.error("Error during BFH flow execution", error);
                return Mono.just(Map.of("error", "Workflow failed: " + error.getMessage()));
            })
            .block(); // Block to ensure startup completes
    }

    /**
     * Sends a generateWebhook request to obtain webhook URL and access token.
     */
    private Mono<Map<String, Object>> generateWebhook() {
        Map<String, String> requestBody = Map.of(
            "name", NAME,
            "regNo", REG_NO,
            "email", EMAIL
        );
        
        log.info("Sending generateWebhook request: {}", requestBody);
        
        return webClient.post()
            .uri(BASE_URL + "/generateWebhook")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .doOnError(error -> log.error("Failed to generate webhook", error));
    }

    /**
     * Submits the final SQL query to the webhook endpoint.
     */
    private Mono<Map<String, Object>> submitFinalQuery(String webhookUrl, String accessToken) {
        String finalQuery = sqlSolver.getFinalQuery();
        
        Map<String, String> queryPayload = Map.of(
            "query", finalQuery
        );
        
        log.info("Submitting finalQuery to webhook: {}", queryPayload);
        
        return webClient.post()
            .uri(webhookUrl)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .bodyValue(queryPayload)
            .retrieve()
            .bodyToMono(Map.class)
            .doOnError(error -> log.error("Failed to submit final query", error));
    }
}
```

### SQLSolver.java

```java
package com.example.demo;

/**
 * Utility class for managing SQL queries.
 * 
 * @author Samyak Singh
 */
public class SQLSolver {

    /**
     * Returns the final SQL query for the assignment.
     * 
     * Query finds the employee with the highest salary paid on a day other than the 1st,
     * including their salary, name, age, and department.
     */
    public String getFinalQuery() {
        return """
            SELECT p.amount AS SALARY,
                   CONCAT(e.first_name, ' ', e.last_name) AS NAME,
                   TIMESTAMPDIFF(YEAR, e.dob, p.payment_time) AS AGE,
                   d.department_name AS DEPARTMENT_NAME
            FROM payments p
            JOIN employee e ON p.emp_id = e.emp_id
            JOIN department d ON e.department = d.department_id
            WHERE DAY(p.payment_time) <> 1
              AND p.amount = (SELECT MAX(amount) FROM payments WHERE DAY(payment_time) <> 1);
            """;
    }
}
```

### application.properties

```properties
# Application name
spring.application.name=Bajaj Finserv Health Demo

# Server configuration
server.port=8080

# Logging configuration
logging.level.com.example.demo=INFO
logging.level.org.springframework.web=INFO
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
```

## ▶️ Application Flow

1. On startup, `StartupRunner` triggers automatically.
2. Sends a JSON request:

```json
{
  "name": "Samyak Singh",
  "regNo": "112215161",
  "email": "112215161@cse.iiitp.ac.in"
}
```

3. Receives a `webhookUrl` and `accessToken`.
4. Submits a **final SQL query**:

```sql
SELECT p.amount AS SALARY,
       CONCAT(e.first_name, ' ', e.last_name) AS NAME,
       TIMESTAMPDIFF(YEAR, e.dob, p.payment_time) AS AGE,
       d.department_name AS DEPARTMENT_NAME
FROM payments p
JOIN employee e ON p.emp_id = e.emp_id
JOIN department d ON e.department = d.department_id
WHERE DAY(p.payment_time) <> 1
  AND p.amount = (SELECT MAX(amount) FROM payments WHERE DAY(payment_time) <> 1);
```

5. Logs the query result or a fallback if an error occurs.
   
## Output

<img width="2879" height="1799" alt="Screenshot 2025-10-06 170548" src="https://github.com/user-attachments/assets/9df08e58-225d-4806-aa57-2bcae1dde4a6" />

<img width="2879" height="1799" alt="Screenshot 2025-10-06 173451" src="https://github.com/user-attachments/assets/56e1cab2-43bf-40f0-a51c-967b2b3465bf" />

<img width="2879" height="1796" alt="Screenshot 2025-10-06 174058" src="https://github.com/user-attachments/assets/67c1ca35-fca7-4ae2-96c6-79011dac013f" />


## 🐞 Error Handling

* Uses `onErrorResume` to gracefully recover from webhook/API failures.
* Prevents `NullPointerException` (previously caused by `onErrorReturn(null)`).
* Logs errors with full stack traces.
* Application continues to run even if the workflow fails.

## 📜 Example Logs

```
2025-10-06 10:30:45 - Starting BFH flow on startup. regNo=112215161
2025-10-06 10:30:45 - Sending generateWebhook request: {name=Samyak Singh, regNo=112215161, email=112215161@cse.iiitp.ac.in}
2025-10-06 10:30:46 - Received webhookUrl=https://example.com/webhook/abc123 accessToken=token_xyz789
2025-10-06 10:30:46 - Submitting finalQuery to webhook: {query=SELECT p.amount AS SALARY...}
2025-10-06 10:30:47 - Query response: {status=success, data={...}}
```

## 🧪 Testing

### Run all tests

```bash
./mvnw test
```

### Test coverage

* Unit tests for `SQLSolver`
* Integration tests for `StartupRunner` (mock WebClient)
* Error handling scenarios

## ✅ Future Improvements

* Add **unit tests** for API calls with MockWebServer
* Externalize config (webhook URL, regNo, query) in `application.yml`
* Add retry mechanism with exponential backoff
* Dockerize the app for deployment
* CI/CD integration (GitHub Actions)
* Add health check endpoints
* Implement request/response logging interceptor
* Add Swagger/OpenAPI documentation

## 📦 Building for Production

### Create executable JAR

```bash
./mvnw clean package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

### Docker deployment (future)

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## 📄 License

This project is created for educational purposes as part of the Bajaj Finserv Health assignment.

## 👨‍💻 Author

**Samyak Singh**  
📧 samyaksingh1028@gmail.com  
📍 Pune, India  
🎓 IIIT Patna - 112215161

## 🤝 Contributing

This is an assignment project. Please do not submit pull requests.

## 📞 Support

For issues or questions, contact the author via email.
