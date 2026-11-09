package com.waclone.so;

import com.waclone.TestBase;
import com.waclone.model.Enums.MemberRole;
import com.waclone.repo.ConversationRepo;
import com.waclone.repo.ParticipantRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AddMembersSOTest extends TestBase {

    @Autowired private ConversationRepo razgovori;
    @Autowired private ParticipantRepo ucesnici;

    private String pera;
    private String mika;
    private String zoran;

    @BeforeEach
    void napraviGrupu() throws Exception {
        pera = token("pera");
        mika = token("mika");
        zoran = token("zoran");
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika"), List.of(), "pera")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ispravni podaci: nov clan se dodaje u grupu")
    void novClanSeDodaje() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika", "zoran"), List.of(), "pera")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.roles.zoran").value("member"));
    }

    @Test
    @DisplayName("stanje baze posle operacije: upisan je nov ucesnik, a razgovor je isti")
    void novUcesnikJeUpisan() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika", "zoran"), List.of(), "pera")
                .andExpect(status().isOk());

        var grupa = razgovori.findByExternalId("g:1").orElseThrow();
        assertThat(razgovori.count()).isEqualTo(1);
        assertThat(ucesnici.findByConversation(grupa))
                .extracting(u -> u.getUser().getUsername())
                .containsExactlyInAnyOrder("pera", "mika", "zoran");
    }

    @Test
    @DisplayName("ponovljeno dodavanje istog clana ne pravi drugi red u bazi")
    void ponovljenoDodavanjeNePraviDrugiRed() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika", "zoran"), List.of(), "pera")
                .andExpect(status().isOk());
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika", "zoran"), List.of(), "pera")
                .andExpect(status().isOk());

        assertThat(ucesnici.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("clan kome je dodeljena uloga administratora dobija tu ulogu")
    void clanuSeMenjaUlogaUAdministratora() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika"), List.of("mika"), "pera")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.mika").value("admin"));

        var grupa = razgovori.findByExternalId("g:1").orElseThrow();
        assertThat(ucesnici.findByConversation(grupa))
                .filteredOn(u -> u.getUser().getUsername().equals("mika"))
                .allMatch(u -> u.getRole() == MemberRole.ADMIN);
    }

    @Test
    @DisplayName("clan koga vise nema na spisku uklanja se iz grupe")
    void uklonjenClanNestajeIzGrupe() throws Exception {
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera"), List.of(), "pera")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1));

        assertThat(ucesnici.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("dodat clan odmah vidi grupu i njenu istoriju")
    void dodatClanVidiGrupu() throws Exception {
        posaljiPorukuUGrupu(pera, "m1", "g:1", "zdravo ekipo").andExpect(status().isOk());
        upisiGrupu(pera, "g:1", "Ekipa", List.of("pera", "mika", "zoran"), List.of(), "pera")
                .andExpect(status().isOk());

        mvc.perform(get("/api/groups/g:1").header(HttpHeaders.AUTHORIZATION, bearer(zoran)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(3));

        mvc.perform(get("/api/messages").param("group", "g:1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(zoran)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("ko nije clan grupe ne moze da menja njen spisak clanova, vraca se 403")
    void koNijeClanNeMenjaSpisak() throws Exception {
        upisiGrupu(zoran, "g:1", "Ekipa", List.of("pera", "mika"), List.of(), "pera")
                .andExpect(status().isForbidden());

        assertThat(ucesnici.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("clan grupe moze da procita njen spisak clanova")
    void clanCitaSpisakClanova() throws Exception {
        mvc.perform(get("/api/groups/g:1").header(HttpHeaders.AUTHORIZATION, bearer(mika)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.pera").value("owner"))
                .andExpect(jsonPath("$.roles.mika").value("member"));
    }

    @Test
    @DisplayName("grupa koja ne postoji vraca 404")
    void nepostojecaGrupaVraca404() throws Exception {
        mvc.perform(get("/api/groups/g:nema").header(HttpHeaders.AUTHORIZATION, bearer(pera)))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions posaljiPorukuUGrupu(
            String token, String clientId, String grupa, String tekst) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/messages")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(body(mapa("clientId", clientId, "group", grupa, "text", tekst))));
    }
}
