package com.waclone.so;

import com.waclone.TestBase;
import com.waclone.model.Enums.ConversationType;
import com.waclone.model.Enums.MemberRole;
import com.waclone.repo.ConversationRepo;
import com.waclone.repo.ParticipantRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreateGroupSOTest extends TestBase {

    @Autowired private ConversationRepo razgovori;
    @Autowired private ParticipantRepo ucesnici;

    private String pera;
    private String zoran;

    @BeforeEach
    void napraviNaloge() throws Exception {
        pera = token("pera");
        token("mika");
        zoran = token("zoran");
    }

    @Test
    @DisplayName("ispravni podaci: grupa se pravi, sa nazivom i clanovima")
    void grupaSePravi() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika"), List.of(), "pera")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("g:1"))
                .andExpect(jsonPath("$.name").value("Ekipa"))
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    @DisplayName("korisnik koji je grupu napravio dobija ulogu vlasnika")
    void tvoracGrupeJeVlasnik() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika"), List.of(), "pera")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.pera").value("owner"))
                .andExpect(jsonPath("$.roles.mika").value("member"));
    }

    @Test
    @DisplayName("stanje baze posle operacije: upisan je razgovor i po jedan ucesnik za clana")
    void razgovorIUcesniciSuUpisani() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika"), List.of(), "pera")
                .andExpect(status().isOk());

        var grupa = razgovori.findByExternalId("g:1").orElseThrow();
        assertThat(grupa.getType()).isEqualTo(ConversationType.GROUP);
        assertThat(grupa.getTitle()).isEqualTo("Ekipa");
        assertThat(ucesnici.findByConversation(grupa))
                .extracting(u -> u.getUser().getUsername())
                .containsExactlyInAnyOrder("pera", "mika");
        assertThat(ucesnici.findByConversation(grupa))
                .filteredOn(u -> u.getUser().getUsername().equals("pera"))
                .allMatch(u -> u.getRole() == MemberRole.OWNER);
    }

    @Test
    @DisplayName("navedeni administrator dobija ulogu administratora")
    void administratorDobijaSvojuUlogu() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika", "zoran"),
                   List.of("mika"), "pera")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.mika").value("admin"))
                .andExpect(jsonPath("$.roles.zoran").value("member"));
    }

    @Test
    @DisplayName("ponovljeni upis iste grupe ne pravi drugi razgovor")
    void ponovljeniUpisNePraviDrugiRazgovor() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika"), List.of(), "pera")
                .andExpect(status().isOk());
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika"), List.of(), "pera")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));

        assertThat(razgovori.count()).isEqualTo(1);
        assertThat(ucesnici.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("izmenjen naziv grupe se upisuje")
    void izmenjenNazivSeUpisuje() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika"), List.of(), "pera")
                .andExpect(status().isOk());
        upisiGrupu(pera, "g:1", "Nova ekipa", List.of("pera", "mika"), List.of(), "pera")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nova ekipa"));

        assertThat(razgovori.findByExternalId("g:1").orElseThrow().getTitle())
                .isEqualTo("Nova ekipa");
    }

    @Test
    @DisplayName("nepotpuni podaci: grupa bez oznake se odbija sa 400")
    void grupaBezOznakeSeOdbija() throws Exception {
        upisiGrupu(pera, null, "Ekipa", List.of("pera"), List.of(), "pera")
                .andExpect(status().isBadRequest());

        assertThat(razgovori.count()).isZero();
    }

    @Test
    @DisplayName("ko nije na spisku clanova ne moze da upise grupu, vraca se 403")
    void koNijeClanNePraviGrupu() throws Exception {
        upisiGrupu(zoran, "g:1", "Ekipa", List.of("pera", "mika"), List.of(), "pera")
                .andExpect(status().isForbidden());

        assertThat(razgovori.count()).isZero();
    }

    @Test
    @DisplayName("nepostojeci clan se preskace, a grupa se ipak pravi")
    void nepostojeciClanSePreskace() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "niko"), List.of(), "pera")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.roles.pera").value("owner"));
    }

    @Test
    @DisplayName("bez tokena: grupa se ne pravi, vraca se 401")
    void bezTokenaSeOdbija() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/groups")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body(mapa("id", "g:1", "name", "Ekipa",
                                           "members", List.of("pera"), "admins", List.of(),
                                           "owner", "pera"))))
                .andExpect(status().isUnauthorized());

        assertThat(razgovori.count()).isZero();
    }
}
