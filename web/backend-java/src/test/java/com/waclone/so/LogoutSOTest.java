package com.waclone.so;

import com.waclone.TestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogoutSOTest extends TestBase {

    @Test
    @DisplayName("server ne pamti sesiju, pa ne postoji ni putanja za odjavu")
    void nemaServerskePutanjeZaOdjavu() throws Exception {
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("posle odjave zahtev bez tokena se odbija sa 401")
    void posleOdjaveZahtevBezTokenaSeOdbija() throws Exception {
        String token = token("pera");
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("odjava jednog korisnika ne dira druge prijavljene korisnike")
    void odjavaNeDiraDrugeKorisnike() throws Exception {
        token("pera");
        String mika = token("mika");

        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mika)))
                .andExpect(status().isOk());
    }
}
