package com.app.bajajfinservhealthqualifier.runner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeRunner implements CommandLineRunner {

    private final RestTemplate restTemplate;

    @Value("${api.base-url}")
    private String baseUrl;

    @Value("${api.generate-path}")
    private String generatePath;

    @Value("${user.name}")
    private String name;

    @Value("${user.regNo}")
    private String regNo;

    @Value("${user.email}")
    private String email;

    @Override
    public void run(String... args) {
        try {
            log.info("--- STARTING CHALLENGE ---");
            RegistrationResponse authResponse = generateWebhook();

            if (authResponse == null || authResponse.getWebhook() == null) {
                log.error("Failed to get webhook or token.");
                return;
            }

            String solutionSql = solveProblem(regNo);

            submitSolution(authResponse, solutionSql);

        } catch (Exception e) {
            log.error("Execution failed", e);
        }
    }

    private RegistrationResponse generateWebhook() {
        String url = baseUrl + generatePath;
        log.info("1. POST Request to: {}", url);

        RegistrationRequest requestBody = new RegistrationRequest(name, regNo, email);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RegistrationRequest> entity = new HttpEntity<>(requestBody, headers);

        try {
            RegistrationResponse response = restTemplate.postForObject(url, entity, RegistrationResponse.class);
            log.info("   Token Received: {}", (response.getAccessToken() != null ? "YES" : "NO"));
            log.info("   Webhook URL: {}", response.getWebhook());
            return response;
        } catch (Exception e) {
            log.error("   Error in Step 1: {}", e.getMessage());
            throw e;
        }
    }

    private String solveProblem(String regNumber) {
        log.info("2. Solving SQL Problem for RegNo: {}", regNumber);

        String numericPart = regNumber.replaceAll("[^0-9]", "");
        int lastTwoDigits = Integer.parseInt(numericPart.substring(numericPart.length() - 2));

        if (lastTwoDigits % 2 != 0) {
            log.info("   Type: ODD ({}). Using Question 1 Solution.", lastTwoDigits);

            return """
                   SELECT 
                       d.DEPARTMENT_NAME, 
                       t.salary AS SALARY, 
                       CONCAT(e.FIRST_NAME, ' ', e.LAST_NAME) AS EMPLOYEE_NAME, 
                       TIMESTAMPDIFF(YEAR, e.DOB, CURDATE()) AS AGE 
                   FROM (
                       SELECT 
                           e.EMP_ID, 
                           SUM(p.AMOUNT) as salary, 
                           RANK() OVER (PARTITION BY e.DEPARTMENT ORDER BY SUM(p.AMOUNT) DESC) as rnk 
                       FROM EMPLOYEE e 
                       JOIN PAYMENTS p ON e.EMP_ID = p.EMP_ID 
                       WHERE EXTRACT(DAY FROM p.PAYMENT_TIME) <> 1 
                       GROUP BY e.EMP_ID, e.DEPARTMENT
                   ) t 
                   JOIN EMPLOYEE e ON t.EMP_ID = e.EMP_ID 
                   JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID 
                   WHERE t.rnk = 1
                   """;
        } else {
            log.info("   Type: EVEN ({}). Using Question 2 Solution.", lastTwoDigits);
            return "";
        }
    }

    private void submitSolution(RegistrationResponse authResponse, String finalQuery) {
        String url = authResponse.getWebhook();
        log.info("3. POST Request to: {}", url);

        SubmissionRequest requestBody = new SubmissionRequest(finalQuery);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authResponse.getAccessToken());

        HttpEntity<SubmissionRequest> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> result = restTemplate.postForEntity(url, entity, String.class);
            log.info("   Submission Status: {}", result.getStatusCode());
            log.info("   Response Body: {}", result.getBody());
        } catch (Exception e) {
            log.error("   Error in Step 3: {}", e.getMessage());
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RegistrationRequest {
        private String name;
        private String regNo;
        private String email;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RegistrationResponse {
        private String webhook;
        private String accessToken;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SubmissionRequest {
        private String finalQuery;
    }
}