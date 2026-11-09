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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SendMessageSOTest extends TestBase {

    @Autowired private MessageRepo poruke;
    @Autowired private com.waclone.repo.ParticipantRepo ucesnici;

    private String pera;
    private String mika;

    @BeforeEach
    void napraviNaloge() throws Exception {
        pera = token("pera");
        mika = token("mika");
    }

    @Test
    @DisplayName("ispravni podaci: poruka se upisuje i vraca sa svojom oznakom")
    void ispravnaPorukaSeUpisuje() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "zdravo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("m1"))
                .andExpect(jsonPath("$.from").value("pera"))
                .andExpect(jsonPath("$.text").value("zdravo"))
                .andExpect(jsonPath("$.type").value("text"))
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.deleted").value(false));
    }

    @Test
    @DisplayName("stanje baze posle operacije: postoji tacno jedan red sa tim sadrzajem")
    void porukaJeUpisanaUBazu() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "zdravo").andExpect(status().isOk());

        assertThat(poruke.count()).isEqualTo(1);
        Message upisana = poruke.findAll().get(0);
        assertThat(upisana.getClientId()).isEqualTo("m1");
        assertThat(upisana.getBody()).isEqualTo("zdravo");
        assertThat(upisana.getSender().getUsername()).isEqualTo("pera");
        assertThat(upisana.getSentAt()).isPositive();
    }

    @Test
    @DisplayName("razgovor dvoje korisnika pravi se pri prvoj poruci, sa oba ucesnika")
    void prvaPorukaPraviRazgovorSaObaUcesnika() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "zdravo").andExpect(status().isOk());

        Message upisana = poruke.findAll().get(0);
        assertThat(upisana.getConversation().getExternalId()).isEqualTo("d:mika:pera");
        assertThat(ucesnici.findByConversation(upisana.getConversation()))
                .extracting(u -> u.getUser().getUsername())
                .containsExactlyInAnyOrder("pera", "mika");
    }

    @Test
    @DisplayName("isti upis dva puta ostavlja jednu poruku u bazi")
    void ponovljeniUpisNeUdvostrucavaPoruku() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "zdravo").andExpect(status().isOk());
        posaljiPoruku(pera, "m1", "mika", "zdravo").andExpect(status().isOk());

        assertThat(poruke.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("oba ucesnika pisu u isti razgovor")
    void obaUcesnikaPisuUIstiRazgovor() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "zdravo").andExpect(status().isOk());
        posaljiPoruku(mika, "m2", "pera", "zdravo i tebi").andExpect(status().isOk());

        assertThat(poruke.count()).isEqualTo(2);
        assertThat(poruke.findAll()).extracting(m -> m.getConversation().getExternalId())
                .containsOnly("d:mika:pera");
    }

    @Test
    @DisplayName("nepotpuni podaci: poruka bez oznake se odbija sa 400")
    void porukaBezOznakeSeOdbija() throws Exception {
        mvc.perform(post("/api/messages").header(HttpHeaders.AUTHORIZATION, bearer(pera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(mapa("to", "mika", "text", "zdravo"))))
                .andExpect(status().isBadRequest());

        assertThat(poruke.count()).isZero();
    }

    @Test
    @DisplayName("nepotpuni podaci: poruka bez sagovornika i bez grupe se odbija sa 400")
    void porukaBezOdredistaSeOdbija() throws Exception {
        mvc.perform(post("/api/messages").header(HttpHeaders.AUTHORIZATION, bearer(pera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(mapa("clientId", "m1", "text", "zdravo"))))
                .andExpect(status().isBadRequest());

        assertThat(poruke.count()).isZero();
    }

    @Test
    @DisplayName("nepostojeci sagovornik: operacija se odbija sa 404")
    void nepostojeciSagovornikSeOdbija() throws Exception {
        posaljiPoruku(pera, "m1", "niko", "zdravo").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("bez tokena: operacija se odbija sa 401 i nista se ne upisuje")
    void bezTokenaSeOdbija() throws Exception {
        mvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(body(mapa("clientId", "m1", "to", "mika", "text", "zdravo"))))
                .andExpect(status().isUnauthorized());

        assertThat(poruke.count()).isZero();
    }

    @Test
    @DisplayName("granicne vrednosti: prazan tekst i tekst sa cirilicom se upisuju")
    void granicneVrednostiTeksta() throws Exception {
        posaljiPoruku(pera, "m1", "mika", "").andExpect(status().isOk());
        posaljiPoruku(pera, "m2", "mika", "Здраво, како си?").andExpect(status().isOk());

        assertThat(poruke.count()).isEqualTo(2);
        assertThat(poruke.findAll()).extracting(Message::getBody)
                .contains("Здраво, како си?");
    }
}
