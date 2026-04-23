package com.stampx.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stampx.service.PassService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Singleton Container pattern: the container is started once in a static block when this
 * class is first loaded, and stopped by Testcontainers' JVM shutdown hook when the JVM exits.
 *
 * This avoids the @Testcontainers / @Container per-class lifecycle that stops the container
 * after each test class finishes. Without the singleton pattern, the second IT class gets a
 * new container with a new port, but Spring's context cache still holds Hikari connections
 * to the now-dead first container, causing "connection has been closed" failures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16");
        postgres.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * PassService makes HTTP calls to the Node.js pass-service.
     * Mock it so integration tests work without the external service.
     */
    @MockBean
    protected PassService passService;

    protected String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    /** Registers a shop and returns the full JSON response as a parsed map. */
    protected java.util.Map<String, Object> registerShop(String name, String slug,
                                                          String email, String password) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "slug": "%s",
                  "email": "%s",
                  "password": "%s",
                  "stampGoal": 5,
                  "rewardDescription": "Free coffee",
                  "primaryColor": "#3B82F6",
                  "logoUrl": ""
                }
                """.formatted(name, slug, email, password);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(),
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    /** Logs in and returns the MockHttpSession (carries the session cookie for subsequent requests). */
    protected jakarta.servlet.http.HttpSession loginAndGetSession(String email,
                                                                   String password) throws Exception {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return result.getRequest().getSession(false);
    }
}
