package com.example.ClankerOrchestrator.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class AgentPermissionApiTests {

    @Autowired
    private MockMvc mockMvc;

    private long CreateAgent(String name) throws Exception {
        String json = mockMvc.perform(post("/Agent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void StateIncludesPermissionList() throws Exception {
        long id = CreateAgent("Clanker");

        mockMvc.perform(get("/Agent/" + id + "/state"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("IDLE"))
            .andExpect(jsonPath("$.permissions").isArray())
            .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void GetPermissionsReturnsEmptyList() throws Exception {
        long id = CreateAgent("Clanker");

        mockMvc.perform(get("/Agent/" + id + "/permission"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void PermissionReplyWithoutSessionIsNotFound() throws Exception {
        long id = CreateAgent("Clanker");

        mockMvc.perform(post("/Agent/" + id + "/permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reply\":\"once\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void PermissionReplyRejectsUnknownReplyValue() throws Exception {
        long id = CreateAgent("Clanker");

        mockMvc.perform(post("/Agent/" + id + "/permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reply\":\"maybe\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void PermissionReplyForUnknownAgentIsNotFound() throws Exception {
        mockMvc.perform(post("/Agent/999/permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reply\":\"once\"}"))
            .andExpect(status().isNotFound());
    }
}
