package com.waclone.so;

import com.waclone.TestBase;
import com.waclone.model.CallLog;
import com.waclone.model.Enums.CallStatus;
import com.waclone.model.Enums.CallType;
import com.waclone.repo.CallLogRepo;
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

class StartCallSOTest extends TestBase {

    @Autowired private CallLogRepo pozivi;

    private String pera;
    private String mika;

    @BeforeEach
    void napraviNaloge() throws Exception {
        pera = token("pera");
        mika = token("mika");
    }

    private ResultActions zabelezi(String token, String peer, boolean odlazni,
                                   String vrsta, String ishod, Long trajanje) throws Exception {
        return mvc.perform(post("/api/calls")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(mapa("peer", peer, "outgoing", odlazni, "type", vrsta,
                                   "status", ishod, "durationMs", trajanje))));
    }

    @Test
    @DisplayName("ispravni podaci: zapis o pozivu se pravi sa vrstom, ishodom i trajanjem")
    void zapisOPozivuSePravi() throws Exception {
        zabelezi(pera, "mika", true, "video", "ended", 12_000L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peer").value("mika"))
                .andExpect(jsonPath("$.outgoing").value(true))
                .andExpect(jsonPath("$.type").value("video"))
                .andExpect(jsonPath("$.status").value("ended"))
                .andExpect(jsonPath("$.durationMs").value(12_000));
    }

    @Test
    @DisplayName("stanje baze posle operacije: upisan je jedan poziv, sa oba ucesnika")
    void pozivJeUpisanUBazu() throws Exception {
        zabelezi(pera, "mika", true, "video", "ended", 12_000L).andExpect(status().isOk());

        assertThat(pozivi.count()).isEqualTo(1);
        CallLog upisan = pozivi.findAll().get(0);
        assertThat(upisan.getCaller().getUsername()).isEqualTo("pera");
        assertThat(upisan.getCallee().getUsername()).isEqualTo("mika");
        assertThat(upisan.getType()).isEqualTo(CallType.VIDEO);
        assertThat(upisan.getStatus()).isEqualTo(CallStatus.ENDED);
    }

    @Test
    @DisplayName("druga strana isti poziv vidi kao dolazni")
    void drugaStranaVidiDolazniPoziv() throws Exception {
        zabelezi(pera, "mika", true, "video", "ended", 12_000L).andExpect(status().isOk());

        mvc.perform(get("/api/calls").header(HttpHeaders.AUTHORIZATION, bearer(mika)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].outgoing").value(false))
                .andExpect(jsonPath("$[0].peer").value("pera"));
    }

    @Test
    @DisplayName("dolazni poziv se upisuje sa zamenjenim ulogama")
    void dolazniPozivZamenjujeUloge() throws Exception {
        zabelezi(pera, "mika", false, "voice", "missed", null).andExpect(status().isOk());

        CallLog upisan = pozivi.findAll().get(0);
        assertThat(upisan.getCaller().getUsername()).isEqualTo("mika");
        assertThat(upisan.getCallee().getUsername()).isEqualTo("pera");
        assertThat(upisan.getStatus()).isEqualTo(CallStatus.MISSED);
    }

    @Test
    @DisplayName("propusten poziv nema trajanje")
    void propustenPozivNemaTrajanje() throws Exception {
        zabelezi(pera, "mika", true, "voice", "missed", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMs").value(0));
    }

    @Test
    @DisplayName("nepoznata vrsta i nepoznat ishod svode se na podrazumevane vrednosti")
    void nepoznateVrednostiSeSvodeNaPodrazumevane() throws Exception {
        zabelezi(pera, "mika", true, "nepoznato", "nepoznato", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("voice"))
                .andExpect(jsonPath("$.status").value("ended"));
    }

    @Test
    @DisplayName("evidencija poziva vraca se najpre sa najnovijim pozivom")
    void evidencijaPocinjeOdNajnovijeg() throws Exception {
        zabelezi(pera, "mika", true, "voice", "ended", 1_000L).andExpect(status().isOk());
        Thread.sleep(25);
        zabelezi(pera, "mika", true, "video", "ended", 2_000L).andExpect(status().isOk());

        mvc.perform(get("/api/calls").header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("video"));
    }

    @Test
    @DisplayName("nepotpuni podaci: bez sagovornika operacija se odbija sa 400")
    void bezSagovornikaSeOdbija() throws Exception {
        zabelezi(pera, null, true, "voice", "ended", null).andExpect(status().isBadRequest());
        zabelezi(pera, "  ", true, "voice", "ended", null).andExpect(status().isBadRequest());

        assertThat(pozivi.count()).isZero();
    }

    @Test
    @DisplayName("nepostojeci sagovornik: operacija se odbija sa 404")
    void nepostojeciSagovornikSeOdbija() throws Exception {
        zabelezi(pera, "niko", true, "voice", "ended", null).andExpect(status().isNotFound());

        assertThat(pozivi.count()).isZero();
    }

    @Test
    @DisplayName("bez tokena: zapis se ne pravi, vraca se 401")
    void bezTokenaSeOdbija() throws Exception {
        mvc.perform(post("/api/calls").contentType(MediaType.APPLICATION_JSON)
                        .content(body(mapa("peer", "mika", "outgoing", true,
                                           "type", "voice", "status", "ended"))))
                .andExpect(status().isUnauthorized());

        assertThat(pozivi.count()).isZero();
    }
}
