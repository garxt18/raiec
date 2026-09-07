package com.raiec.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.Filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The hosting platform's health check treats any non-2xx as unhealthy and will fail the
 * deploy. This endpoint therefore has to answer 200 with no credentials — a rule that is
 * easy to break later by tightening security, and expensive to discover, because the
 * symptom is a deploy that times out while the application log shows a clean startup.
 */
@SpringBootTest
class HealthEndpointTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private Filter springSecurityFilterChain;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(springSecurityFilterChain).build();
    }

    @Test
    void healthIsReachableWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthReportsDatabaseReachability() throws Exception {
        // A green check should mean the app can serve a request, not just that the
        // process is alive with a dead connection pool.
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    void otherEndpointsStillRequireAToken() throws Exception {
        // Making health public must not have opened anything else.
        mvc.perform(get("/api/tenders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/lar")).andExpect(status().isUnauthorized());
    }
}
