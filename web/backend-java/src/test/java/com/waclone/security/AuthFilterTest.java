package com.waclone.security;

import com.waclone.TestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFilterTest extends TestBase {

    @Test
    @DisplayName("zahtev bez tokena se odbija sa 401")
    void zahtevBezTokenaSeOdbija() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/calls")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("zahtev sa ispravnim tokenom prolazi")
    void ispravanTokenProlazi() throws Exception {
        String token = token("pera");

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("pera"));
    }

    @Test
    @DisplayName("zahtev sa izmenjenim tokenom se odbija sa 401")
    void izmenjenTokenSeOdbija() throws Exception {
        String token = token("pera");
        String izmenjen = token.substring(0, token.length() - 3) + "AAA";

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(izmenjen)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("zahtev sa besmislenim tokenom se odbija sa 401")
    void besmislenTokenSeOdbija() throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer nije-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("zaglavlje bez reci Bearer se ne priznaje")
    void zaglavljeBezBearerNeVazi() throws Exception {
        String token = token("pera");

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("kod GET zahteva token moze da stigne i kao parametar upita")
    void getZahtevPrihvataTokenKaoParametar() throws Exception {
        String token = token("pera");

        mvc.perform(get("/api/me").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("pera"));
    }

    @Test
    @DisplayName("registracija i prijava su dostupne bez tokena")
    void putanjeZaPrijavuSuOtvorene() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(mapa("username", "pera", "password", LOZINKA))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(mapa("username", "pera", "password", LOZINKA))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("spisak korisnika ne sadrzi onoga ko ga trazi")
    void spisakKorisnikaNeSadrziTrazioca() throws Exception {
        String token = token("pera");
        token("mika");

        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("mika"));
    }

    @Test
    @DisplayName("token jednog korisnika ne otvara podatke drugog korisnika")
    void tudjTokenNeOtvaraTudjePodatke() throws Exception {
        token("pera");
        String mika = token("mika");

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mika)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mika"));
    }
}
