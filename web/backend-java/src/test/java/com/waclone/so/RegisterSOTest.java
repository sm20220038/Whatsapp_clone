package com.waclone.so;

import com.waclone.TestBase;
import com.waclone.model.User;
import com.waclone.repo.UserRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegisterSOTest extends TestBase {

    @Autowired private UserRepo korisnici;

    private org.springframework.test.web.servlet.ResultActions registruj(String username,
                                                                        String displayName,
                                                                        String password)
            throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(mapa("username", username, "displayName", displayName,
                                   "password", password))));
    }

    @Test
    @DisplayName("ispravni podaci: pravi se nov korisnik i izdaje se token")
    void ispravniPodaciPraveKorisnika() throws Exception {
        registruj("pera", "Pera Peric", LOZINKA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("pera"))
                .andExpect(jsonPath("$.user.displayName").value("Pera Peric"));
    }

    @Test
    @DisplayName("stanje baze posle operacije: korisnik postoji i ima ulogu USER")
    void korisnikJeUpisanUBazu() throws Exception {
        registruj("pera", "Pera Peric", LOZINKA).andExpect(status().isOk());

        User upisan = korisnici.findByUsername("pera").orElseThrow();
        assertThat(upisan.getUsername()).isEqualTo("pera");
        assertThat(upisan.getDisplayName()).isEqualTo("Pera Peric");
        assertThat(upisan.getRole()).isEqualTo(com.waclone.model.Enums.Role.USER);
        assertThat(korisnici.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("lozinka se ne cuva u citljivom obliku, nego kao PBKDF2 hes")
    void lozinkaSeCuvaKaoHes() throws Exception {
        registruj("pera", null, LOZINKA).andExpect(status().isOk());

        String hes = korisnici.findByUsername("pera").orElseThrow().getPasswordHash();
        assertThat(hes).doesNotContain(LOZINKA);
        assertThat(hes).startsWith("120000:");
        assertThat(hes.split(":")).hasSize(3);
    }

    @Test
    @DisplayName("korisnicko ime se svodi na mala slova i skracuju mu se razmaci")
    void korisnickoImeSeNormalizuje() throws Exception {
        registruj("  PERA  ", null, LOZINKA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("pera"));

        assertThat(korisnici.existsByUsername("pera")).isTrue();
    }

    @Test
    @DisplayName("nepotpuni podaci: bez imena za prikaz uzima se korisnicko ime")
    void bezImenaZaPrikazUzimaSeKorisnickoIme() throws Exception {
        registruj("pera", null, LOZINKA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.displayName").value("pera"));
    }

    @Test
    @DisplayName("zauzeto korisnicko ime: operacija se odbija i nov red se ne upisuje")
    void zauzetoKorisnickoImeSeOdbija() throws Exception {
        registruj("pera", null, LOZINKA).andExpect(status().isOk());

        registruj("pera", null, "drugalozinka").andExpect(status().isConflict());
        assertThat(korisnici.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("nedozvoljeni znaci u korisnickom imenu se odbijaju")
    void nedozvoljeniZnaciSeOdbijaju() throws Exception {
        registruj("pera peric", null, LOZINKA).andExpect(status().isBadRequest());
        registruj("pera@fon", null, LOZINKA).andExpect(status().isBadRequest());
        registruj("Пера", null, LOZINKA).andExpect(status().isBadRequest());

        assertThat(korisnici.count()).isZero();
    }

    @Test
    @DisplayName("granicne vrednosti: prekratko ime i prekratka lozinka se odbijaju")
    void granicneVrednostiSeOdbijaju() throws Exception {
        registruj("p", null, LOZINKA).andExpect(status().isBadRequest());
        registruj("pera", null, "abc").andExpect(status().isBadRequest());
        registruj("pe", null, "abcd").andExpect(status().isOk());
    }

    @Test
    @DisplayName("neinicijalizovani podaci: bez imena i bez lozinke operacija se odbija")
    void neinicijalizovaniPodaciSeOdbijaju() throws Exception {
        registruj(null, null, LOZINKA).andExpect(status().isBadRequest());
        registruj("pera", null, null).andExpect(status().isBadRequest());

        assertThat(korisnici.count()).isZero();
    }
}
