package com.waclone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class TestBase {

    protected static final String LOZINKA = "lozinka123";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected String body(Map<String, Object> fields) throws Exception {
        return json.writeValueAsString(fields);
    }

    protected Map<String, Object> mapa(Object... parovi) {
        Map<String, Object> out = new HashMap<>();
        for (int i = 0; i < parovi.length; i += 2) {
            out.put((String) parovi[i], parovi[i + 1]);
        }
        return out;
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected String token(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(mapa("username", username, "password", LOZINKA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    protected JsonNode telo(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    protected ResultActions posaljiPoruku(String token, String clientId,
                                          String peer, String tekst) throws Exception {
        return mvc.perform(post("/api/messages")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(mapa("clientId", clientId, "to", peer, "text", tekst))));
    }

    protected ResultActions upisiGrupu(String token, String id, String naziv,
                                       List<String> clanovi, List<String> administratori,
                                       String vlasnik) throws Exception {
        return mvc.perform(post("/api/groups")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(mapa("id", id, "name", naziv, "members", clanovi,
                                   "admins", administratori, "owner", vlasnik))));
    }
}
