package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Component
public class StartupRunner implements ApplicationRunner {

    private final Logger log = LoggerFactory.getLogger(StartupRunner.class);
    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.name}")
    private String name;

    @Value("${app.regNo}")
    private String regNo;

    @Value("${app.email}")
    private String email;

    @Value("${bfh.baseGenerateUrl}")
    private String generateUrl;

    @Value("${fallback.finalQuery:}")
    private String fallbackFinalQuery;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting BFH flow on startup. regNo={}", regNo);

        // 1. Generate Webhook
        JsonNode genBody = mapper.createObjectNode()
                .put("name", name)
                .put("regNo", regNo)
                .put("email", email);

        log.info("Sending generateWebhook request: {}", genBody);

        String genRespStr = webClient.post()
                .uri(generateUrl.trim())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(genBody) // pass JsonNode directly
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.warn("generateWebhook request failed: {}", e.getMessage());
                    return Mono.empty();
                })
                .block();

        if (!StringUtils.hasText(genRespStr)) {
            log.warn("Empty response from generateWebhook; aborting.");
            return;
        }

        JsonNode genResp = mapper.readTree(genRespStr);
        String webhookUrl = Optional.ofNullable(genResp.path("webhook").asText(null))
                .orElse(genResp.path("webHook").asText(null));
        String accessToken = Optional.ofNullable(genResp.path("accessToken").asText(null))
                .orElse(genResp.path("token").asText(null));

        if (!StringUtils.hasText(webhookUrl) || !StringUtils.hasText(accessToken)) {
            log.warn("Webhook URL or access token not found; aborting.");
            return;
        }

        webhookUrl = webhookUrl.trim();
        accessToken = accessToken.trim();

        log.info("Received webhookUrl={} accessTokenPresent={}", webhookUrl, accessToken);

        // 2. Solve SQL problem
        String finalQuery = SQLSolver.solveHighestSalaryNotOnFirstDay();
        if (!StringUtils.hasText(finalQuery)) {
            finalQuery = fallbackFinalQuery;
        }

        if (!StringUtils.hasText(finalQuery)) {
            log.warn("No finalQuery available; aborting.");
            return;
        }

        JsonNode submitBody = mapper.createObjectNode().put("finalQuery", finalQuery);

        log.info("Submitting finalQuery: {}", submitBody);

        String submitResp = webClient.post()
                .uri(webhookUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(submitBody)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.warn("submission failed: {}", e.getMessage()))
                .onErrorResume(ex -> {
                    log.error("Error occurred while processing", ex);
                    return Mono.empty(); // or Mono.just(new MyResponse("fallback"))
                })
                .block();

        log.info("Submission response: {}", submitResp);
    }
}
