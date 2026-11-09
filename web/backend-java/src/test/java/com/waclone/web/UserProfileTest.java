package com.waclone.web;

import com.waclone.TestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileTest extends TestBase {

    private String peraToken;

    @BeforeEach
    void createUser() throws Exception {
        peraToken = token("pera");
    }

    private Map<String, Object> profile(String displayName, String avatarId) {
        Map<String, Object> req = new HashMap<>();
        req.put("displayName", displayName);
        req.put("avatarId", avatarId);
        return req;
    }

    @Test
    @DisplayName("prijavljeni korisnik moze da procita svoje podatke")
    void ownProfileCanBeRead() throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(peraToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("pera"))
                .andExpect(jsonPath("$.displayName").value("pera"));
    }

    @Test
    @DisplayName("ime za prikaz moze da se promeni")
    void displayNameCanBeChanged() throws Exception {
        mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(peraToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(profile("Pera Peric", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Pera Peric"));

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(peraToken)))
                .andExpect(jsonPath("$.displayName").value("Pera Peric"));
    }

    @Test
    @DisplayName("prazno ime za prikaz ne brise postojece")
    void blankDisplayNameIsIgnored() throws Exception {
        mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(peraToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(profile("   ", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("pera"));
    }

    @Test
    @DisplayName("ispravna oznaka profilne slike se prihvata")
    void validAvatarIdIsAccepted() throws Exception {
        mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(peraToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(profile(null, "0123456789abcdef0123456789abcdef"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarId").value("0123456789abcdef0123456789abcdef"));
    }

    @Test
    @DisplayName("neispravna oznaka profilne slike se odbija sa 400")
    void invalidAvatarIdIsRejected() throws Exception {
        mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(peraToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(profile(null, "nije-oznaka"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("prazna oznaka profilne slike uklanja postojecu")
    void blankAvatarIdRemovesTheAvatar() throws Exception {
        mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(peraToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(profile(null, "0123456789abcdef0123456789abcdef"))))
                .andExpect(status().isOk());

        mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(peraToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(profile(null, ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarId").doesNotExist());
    }

    @Test
    @DisplayName("izmena podataka bez tokena se odbija sa 401")
    void profileCannotBeChangedWithoutToken() throws Exception {
        mvc.perform(put("/api/me").contentType(MediaType.APPLICATION_JSON)
                        .content(body(profile("Podmetnuto", null))))
                .andExpect(status().isUnauthorized());
    }
}
