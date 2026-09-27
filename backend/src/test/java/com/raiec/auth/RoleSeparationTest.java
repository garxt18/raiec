package com.raiec.auth;

import com.jayway.jsonpath.JsonPath;
import com.raiec.auth.entity.Role;
import com.raiec.auth.service.UserAdminService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The separation the vetting process depends on: the department that files an estimate
 * must not be the one that passes it.
 *
 * <p>These go through the real security filter chain rather than calling services, because
 * the rule being tested is a filter rule — a service-level test would pass whether or not
 * the endpoint was actually protected.
 */
@SpringBootTest
@Transactional
class RoleSeparationTest {

    @Autowired
    private WebApplicationContext wac;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter chain;
    @Autowired
    private UserAdminService userAdmin;

    private MockMvc mvc;
    private static final String PASSWORD = "a-long-enough-password";

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(chain).build();
    }

    private String tokenFor(String username, Role role) throws Exception {
        userAdmin.create(username, PASSWORD, role, username);
        var res = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.token");
    }

    @Test
    void aFilerMayNotApproveATender() throws Exception {
        String token = tokenFor("sep.filer", Role.FILER);
        mvc.perform(post("/api/tenders/1/approve").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void aFilerMayNotRejectOrSendBackForInformation() throws Exception {
        String token = tokenFor("sep.filer2", Role.FILER);
        mvc.perform(post("/api/tenders/1/reject").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tenders/1/request-info").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"remark\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aFilerMayStillReadTheirTenders() throws Exception {
        String token = tokenFor("sep.filer3", Role.FILER);
        // Filing is their job, so the pipeline must remain fully visible to them.
        mvc.perform(get("/api/tenders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void anOfficerMayDecide() throws Exception {
        String token = tokenFor("sep.officer", Role.OFFICER);
        // 404 or 400 means the rule let them through and the tender simply is not there;
        // 403 would mean the role was refused, which is what this asserts against.
        int code = mvc.perform(post("/api/tenders/999999/approve")
                .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertNotEquals(403, code,
                "an officer must not be refused the decision endpoints");
    }

    @Test
    void onlyAnAdminMayReachAccountability() throws Exception {
        mvc.perform(get("/api/admin/accountability")
                .header("Authorization", "Bearer " + tokenFor("sep.officer2", Role.OFFICER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/accountability")
                .header("Authorization", "Bearer " + tokenFor("sep.admin", Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAnAdminMayManageAccounts() throws Exception {
        mvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + tokenFor("sep.filer4", Role.FILER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingATenderIsAnAdminPower() throws Exception {
        // Deleting a tender takes its audit trail with it.
        mvc.perform(delete("/api/tenders/1")
                .header("Authorization", "Bearer " + tokenFor("sep.officer3", Role.OFFICER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedRequestIsRefusedOutright() throws Exception {
        mvc.perform(post("/api/tenders/1/approve")).andExpect(status().isUnauthorized());
    }
}
