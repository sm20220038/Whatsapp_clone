package com.waclone.so;

import com.waclone.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GetHistorySOTest extends TestBase {

    private String pera;
    private String mika;
    private String zoran;

    @BeforeEach
    void napraviNaloge() throws Exception {
        pera = token("pera");
        mika = token("mika");
        zoran = token("zoran");
    }

    @Test
    @DisplayName("ispravni podaci: vracaju se sve poruke izabranog razgovora")
    void vracajuSeSvePorukeRazgovora() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "prva").andExpect(status().isOk());
        posaljiPoruku(mika, "m2", "pera", "druga").andExpect(status().isOk());

        mvc.perform(get("/api/messages").param("peer", "mika")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("oba ucesnika vide istu istoriju")
    void obaUcesnikaVideIstuIstoriju() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "prva").andExpect(status().isOk());
        posaljiPoruku(mika, "m2", "pera", "druga").andExpect(status().isOk());

        mvc.perform(get("/api/messages").param("peer", "pera")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mika)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("poruke se vracaju hronoloski, od najstarije")
    void porukeSuHronoloski() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "prva").andExpect(status().isOk());
        Thread.sleep(25);
        posaljiPoruku(pera, "m2", "mika", "druga").andExpect(status().isOk());
        Thread.sleep(25);
        posaljiPoruku(pera, "m3", "mika", "treca").andExpect(status().isOk());

        mvc.perform(get("/api/messages").param("peer", "mika")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("prva"))
                .andExpect(jsonPath("$[1].text").value("druga"))
                .andExpect(jsonPath("$[2].text").value("treca"));
    }

    @Test
    @DisplayName("stranicenje: trazeni broj poruka se postuje")
    void stranicenjeVracaTrazeniBroj() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "prva").andExpect(status().isOk());
        Thread.sleep(25);
        posaljiPoruku(pera, "m2", "mika", "druga").andExpect(status().isOk());
        Thread.sleep(25);
        posaljiPoruku(pera, "m3", "mika", "treca").andExpect(status().isOk());

        mvc.perform(get("/api/messages").param("peer", "mika").param("limit", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].text").value("treca"));
    }

    @Test
    @DisplayName("stranicenje: zadata granica vremena vraca starije poruke")
    void granicaVremenaVracaStarijePoruke() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "prva").andExpect(status().isOk());
        Thread.sleep(25);
        long granica = telo(posaljiPoruku(pera, "m2", "mika", "druga")
                .andExpect(status().isOk())).get("ts").asLong();

        mvc.perform(get("/api/messages").param("peer", "mika")
                        .param("before", String.valueOf(granica))
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("prva"));
    }

    @Test
    @DisplayName("prazan razgovor vraca prazan spisak")
    void prazanRazgovorVracaPrazanSpisak() throws Exception {
        mvc.perform(get("/api/messages").param("peer", "mika")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("poruke drugog razgovora se ne mesaju u istoriju")
    void porukeDrugogRazgovoraSeNeMesaju() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "za miku").andExpect(status().isOk());
        posaljiPoruku(pera, "m2", "zoran", "za zorana").andExpect(status().isOk());

        mvc.perform(get("/api/messages").param("peer", "mika")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("za miku"));
    }

    @Test
    @DisplayName("istoriju grupe ne moze da procita onaj ko nije clan, vraca se 403")
    void istorijuGrupeNeCitaKoNijeClan() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", java.util.List.of("pera", "mika"),
                   java.util.List.of(), "pera").andExpect(status().isOk());

        mvc.perform(get("/api/messages").param("group", "g:1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(zoran)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("nepotpuni podaci: bez sagovornika i bez grupe vraca se 400")
    void bezOdredistaSeOdbija() throws Exception {
        mvc.perform(get("/api/messages").header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("bez tokena: istorija se ne izdaje, vraca se 401")
    void bezTokenaSeOdbija() throws Exception {
        mvc.perform(get("/api/messages").param("peer", "mika"))
                .andExpect(status().isUnauthorized());
    }
}
