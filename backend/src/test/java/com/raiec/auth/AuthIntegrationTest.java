package com.raiec.auth;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthIntegrationTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(springSecurityFilterChain).build();
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    @Test
    void protectedEndpointRequiresToken_andLoginGrantsAccess() throws Exception {
        // 1. No token -> 401
        mvc.perform(get("/api/tenders")).andExpect(status().isUnauthorized());

        // 2. Login as the seeded admin -> token + role
        MvcResult res = login("admin", "admin@123");
        org.junit.jupiter.api.Assertions.assertEquals(200, res.getResponse().getStatus());
        String body = res.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals("ADMIN", JsonPath.read(body, "$.role"));
        String token = JsonPath.read(body, "$.token");

        // 3. With the token -> 200
        mvc.perform(get("/api/tenders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void officerCannotImportReferenceData() throws Exception {
        MvcResult res = login("officer", "officer@123");
        String body = res.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals("OFFICER", JsonPath.read(body, "$.role"));
        String token = JsonPath.read(body, "$.token");

        // ADMIN-only endpoint -> 403 for an officer
        mvc.perform(post("/api/reference/import/irussor").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void officerCanReadButNotChangeThresholds() throws Exception {
        MvcResult res = login("officer", "officer@123");
        String token = JsonPath.read(res.getResponse().getContentAsString(), "$.token");
        // Reading is part of the officer's job: a verdict is meaningless without its band.
        mvc.perform(get("/api/settings/thresholds").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // Changing them is department-wide, so it is admin-only.
        mvc.perform(put("/api/settings/thresholds")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"warnPct\":6}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void badPasswordIsRejected() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(401, login("admin", "wrong").getResponse().getStatus());
    }
}
