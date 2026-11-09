package com.waclone.so;

import com.waclone.TestBase;
import com.waclone.model.Message;
import com.waclone.repo.MessageRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeleteMessageSOTest extends TestBase {

    @Autowired private MessageRepo poruke;

    private String pera;
    private String mika;

    @BeforeEach
    void napraviNaloge() throws Exception {
        pera = token("pera");
        mika = token("mika");
        posaljiPoruku(pera, "m1", "mika", "zdravo").andExpect(status().isOk());
    }

    private ResultActions obrisi(String token, String clientId, String peer) throws Exception {
        return mvc.perform(post("/api/messages/delete")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(mapa("clientId", clientId, "peer", peer))));
    }

    @Test
    @DisplayName("ispravni podaci: poruka se oznacava kao obrisana")
    void porukaSeOznacavaKaoObrisana() throws Exception {
        obrisi(pera, "m1", "mika")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("m1"))
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.text").doesNotExist());
    }

    @Test
    @DisplayName("stanje baze posle operacije: red ostaje, a sadrzaj je uklonjen")
    void redOstajeUBaziBezSadrzaja() throws Exception {
        obrisi(pera, "m1", "mika").andExpect(status().isOk());

        assertThat(poruke.count()).isEqualTo(1);
        Message obrisana = poruke.findAll().get(0);
        assertThat(obrisana.isDeleted()).isTrue();
        assertThat(obrisana.getBody()).isNull();
        assertThat(obrisana.getFileName()).isNull();
        assertThat(obrisana.getStoragePath()).isNull();
    }

    @Test
    @DisplayName("obrisana poruka i dalje stoji u istoriji, sa oznakom da je uklonjena")
    void obrisanaPorukaOstajeUIstoriji() throws Exception {
        obrisi(pera, "m1", "mika").andExpect(status().isOk());

        mvc.perform(get("/api/messages").param("peer", "mika")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].deleted").value(true));
    }

    @Test
    @DisplayName("obrisanu poruku vidi kao uklonjenu i druga strana razgovora")
    void drugaStranaVidiUklonjenuPoruku() throws Exception {
        obrisi(pera, "m1", "mika").andExpect(status().isOk());

        mvc.perform(get("/api/messages").param("peer", "pera")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mika)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].text").doesNotExist());
    }

    @Test
    @DisplayName("tudja poruka: operacija se odbija sa 403 i poruka ostaje netaknuta")
    void tudjaPorukaSeNeBrise() throws Exception {
        obrisi(mika, "m1", "pera").andExpect(status().isForbidden());

        Message netaknuta = poruke.findAll().get(0);
        assertThat(netaknuta.isDeleted()).isFalse();
        assertThat(netaknuta.getBody()).isEqualTo("zdravo");
    }

    @Test
    @DisplayName("poruka koja ne postoji: vraca se 404")
    void nepostojecaPorukaVraca404() throws Exception {
        obrisi(pera, "nema", "mika").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("obrisana poruka vise ne moze da se izmeni, vraca se 409")
    void obrisanaPorukaSeNeMenja() throws Exception {
        obrisi(pera, "m1", "mika").andExpect(status().isOk());

        mvc.perform(post("/api/messages/edit").header(HttpHeaders.AUTHORIZATION, bearer(pera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(mapa("clientId", "m1", "peer", "mika", "text", "opet"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("ponovljeno brisanje iste poruke ne menja ishod")
    void ponovljenoBrisanjeNeMenjaIshod() throws Exception {
        obrisi(pera, "m1", "mika").andExpect(status().isOk());
        obrisi(pera, "m1", "mika")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        assertThat(poruke.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("bez tokena: operacija se odbija sa 401")
    void bezTokenaSeOdbija() throws Exception {
        mvc.perform(post("/api/messages/delete").contentType(MediaType.APPLICATION_JSON)
                        .content(body(mapa("clientId", "m1", "peer", "mika"))))
                .andExpect(status().isUnauthorized());

        assertThat(poruke.findAll().get(0).isDeleted()).isFalse();
    }
}
