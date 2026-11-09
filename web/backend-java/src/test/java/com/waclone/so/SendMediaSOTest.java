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
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SendMediaSOTest extends TestBase {

    @Autowired private MessageRepo poruke;

    private String pera;

    @BeforeEach
    void napraviNaloge() throws Exception {
        pera = token("pera");
        token("mika");
    }

    private MockMultipartFile datoteka(String naziv, String sadrzaj) {
        return new MockMultipartFile("file", naziv, "text/plain",
                sadrzaj.getBytes(StandardCharsets.UTF_8));
    }

    private String otpremi(String naziv, String sadrzaj) throws Exception {
        return telo(mvc.perform(multipart("/api/media").file(datoteka(naziv, sadrzaj))
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())).get("id").asText();
    }

    @Test
    @DisplayName("ispravni podaci: datoteka se otprema i dobija oznaku, naziv i velicinu")
    void datotekaSeOtprema() throws Exception {
        mvc.perform(multipart("/api/media").file(datoteka("izvestaj.txt", "sadrzaj datoteke"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.matchesPattern("[a-f0-9]{32}")))
                .andExpect(jsonPath("$.fileName").value("izvestaj.txt"))
                .andExpect(jsonPath("$.size").value(16))
                .andExpect(jsonPath("$.sha256").isNotEmpty())
                .andExpect(jsonPath("$.uploadedBy").value("pera"));
    }

    @Test
    @DisplayName("otpremljena datoteka se preuzima neizmenjena")
    void datotekaSePreuzimaNeizmenjena() throws Exception {
        String id = otpremi("izvestaj.txt", "sadrzaj datoteke");

        byte[] preuzeto = mvc.perform(get("/api/media/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(preuzeto, StandardCharsets.UTF_8)).isEqualTo("sadrzaj datoteke");
    }

    @Test
    @DisplayName("poruka sa datotekom cuva u bazi samo zapis o njoj, ne i sadrzaj")
    void porukaCuvaSamoZapisODatoteci() throws Exception {
        String id = otpremi("slika.png", "lazna slika");

        mvc.perform(post("/api/messages").header(HttpHeaders.AUTHORIZATION, bearer(pera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(mapa("clientId", "m1", "to", "mika", "text", null,
                                           "type", "image", "fileName", "slika.png",
                                           "fileSizeBytes", 11L, "storagePath", id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("m1"))
                .andExpect(jsonPath("$.fileName").value("slika.png"))
                .andExpect(jsonPath("$.storagePath").value(id));

        assertThat(poruke.count()).isEqualTo(1);
        Message upisana = poruke.findAll().get(0);
        assertThat(upisana.getBody()).isNull();
    }

    @Test
    @DisplayName("prazna datoteka se odbija sa 400")
    void praznaDatotekaSeOdbija() throws Exception {
        mvc.perform(multipart("/api/media").file(datoteka("prazna.txt", ""))
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("neispravna oznaka datoteke se odbija sa 400")
    void neispravnaOznakaSeOdbija() throws Exception {
        mvc.perform(get("/api/media/nije-oznaka")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("datoteka koja ne postoji vraca 404")
    void nepostojecaDatotekaVraca404() throws Exception {
        mvc.perform(get("/api/media/0123456789abcdef0123456789abcdef")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("bez tokena se ne moze ni otpremiti ni preuzeti datoteka")
    void bezTokenaNemaPristupaMedijima() throws Exception {
        String id = otpremi("izvestaj.txt", "sadrzaj datoteke");

        mvc.perform(multipart("/api/media").file(datoteka("druga.txt", "nesto")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/media/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("naziv datoteke se ocisti od putanje, da se ne izadje iz direktorijuma")
    void nazivDatotekeSeCisti() throws Exception {
        mvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "../../tajna.txt", "text/plain",
                                "sadrzaj".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("tajna.txt"));
    }
}
