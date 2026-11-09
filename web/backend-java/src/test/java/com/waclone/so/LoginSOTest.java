package com.waclone.so;

import com.waclone.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginSOTest extends TestBase {

    @BeforeEach
    void napraviNalog() throws Exception {
        token("pera");
    }

    private ResultActions prijavi(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(mapa("username", username, "password", password))));
    }

    @Test
    @DisplayName("ispravni podaci: korisnik je prijavljen i dobija token")
    void ispravnaPrijavaIzdajeToken() throws Exception {
        prijavi("pera", LOZINKA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("pera"));
    }

    @Test
    @DisplayName("izdatim tokenom se pristupa zasticenim putanjama")
    void izdatiTokenOtvaraZasticenePutanje() throws Exception {
        String token = telo(prijavi("pera", LOZINKA).andExpect(status().isOk()))
                .get("token").asText();

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("pera"));
    }

    @Test
    @DisplayName("posle prijave ucitava se spisak ostalih korisnika")
    void poPrijaviSeUcitavaSpisakKorisnika() throws Exception {
        token("mika");
        String token = telo(prijavi("pera", LOZINKA).andExpect(status().isOk()))
                .get("token").asText();

        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("mika"));
    }

    @Test
    @DisplayName("prijava velikim slovima uspeva, jer se ime svodi na mala slova")
    void prijavaNeZaviziOdVelicineSlova() throws Exception {
        prijavi("PERA", LOZINKA).andExpect(status().isOk());
        prijavi("  Pera ", LOZINKA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("pogresna lozinka: prijava se odbija sa 401")
    void pogresnaLozinkaSeOdbija() throws Exception {
        prijavi("pera", "pogresna").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("nepostojeci korisnik: prijava se odbija sa 401")
    void nepostojeciKorisnikSeOdbija() throws Exception {
        prijavi("niko", LOZINKA).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("neinicijalizovani podaci: prazna lozinka i prazno ime se odbijaju sa 400")
    void praznaPoljaSeOdbijaju() throws Exception {
        prijavi("pera", "").andExpect(status().isBadRequest());
        prijavi("", LOZINKA).andExpect(status().isBadRequest());
        prijavi(null, LOZINKA).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("neuspela prijava ne izdaje token")
    void neuspelaPrijavaNeIzdajeToken() throws Exception {
        String odgovor = prijavi("pera", "pogresna")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(odgovor).doesNotContain("token");
    }
}
