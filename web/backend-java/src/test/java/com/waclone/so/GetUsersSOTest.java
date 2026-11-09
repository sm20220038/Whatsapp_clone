package com.waclone.so;

import com.fasterxml.jackson.databind.JsonNode;
import com.waclone.TestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GetUsersSOTest extends TestBase {

    private List<String> imena(JsonNode lista) {
        List<String> out = new ArrayList<>();
        lista.forEach(u -> out.add(u.get("username").asText()));
        return out;
    }

    @Test
    @DisplayName("spisak sadrzi sve ostale korisnike, bez prijavljenog")
    void spisakSadrziOstaleKorisnikeBezPrijavljenog() throws Exception {
        String pera = token("pera");
        token("mika");
        token("laza");

        JsonNode lista = telo(mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk()));

        assertThat(imena(lista)).contains("mika", "laza").doesNotContain("pera");
    }

    @Test
    @DisplayName("svaki korisnik ima username, displayName i lastSeen, a nema zapis lozinke")
    void korisnikNosiJavnePodatkeBezLozinke() throws Exception {
        String pera = token("pera");
        token("mika");

        JsonNode lista = telo(mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk()));
        JsonNode mika = null;
        for (JsonNode u : lista) if (u.get("username").asText().equals("mika")) mika = u;

        assertThat(mika).isNotNull();
        assertThat(mika.has("displayName")).isTrue();
        assertThat(mika.has("lastSeen")).isTrue();
        assertThat(mika.has("passwordHash")).isFalse();
        assertThat(mika.has("password")).isFalse();
    }

    @Test
    @DisplayName("novoregistrovan korisnik se odmah pojavljuje u spisku")
    void novoregistrovanKorisnikSeOdmahPojavljuje() throws Exception {
        String pera = token("pera");
        JsonNode pre = telo(mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk()));
        assertThat(imena(pre)).doesNotContain("zika");

        token("zika");
        JsonNode posle = telo(mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk()));
        assertThat(imena(posle)).contains("zika");
        assertThat(posle.size()).isEqualTo(pre.size() + 1);
    }

    @Test
    @DisplayName("bez tokena spisak korisnika nije dostupan")
    void bezTokenaNijeDostupan() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer pogresan.token.x"))
                .andExpect(status().isUnauthorized());
    }
}
