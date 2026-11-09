package com.waclone.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {

    private final PasswordService passwords = new PasswordService();

    @Test
    @DisplayName("hes ima oblik broj_iteracija:so:izvedeni_kljuc")
    void hashHasExpectedShape() {
        String stored = passwords.hash("lozinka123");
        String[] parts = stored.split(":");

        assertThat(parts).hasSize(3);
        assertThat(Integer.parseInt(parts[0])).isEqualTo(120_000);
        assertThat(Base64.getDecoder().decode(parts[1])).hasSize(16);
        assertThat(Base64.getDecoder().decode(parts[2])).hasSize(32);
    }

    @Test
    @DisplayName("lozinka se ne cuva u citljivom obliku")
    void hashDoesNotContainThePassword() {
        assertThat(passwords.hash("lozinka123")).doesNotContain("lozinka123");
    }

    @Test
    @DisplayName("ista lozinka daje razlicit hes, jer je so slucajna")
    void samePasswordGivesDifferentHash() {
        assertThat(passwords.hash("lozinka123")).isNotEqualTo(passwords.hash("lozinka123"));
    }

    @Test
    @DisplayName("ispravna lozinka se prihvata")
    void acceptsCorrectPassword() {
        String stored = passwords.hash("lozinka123");
        assertThat(passwords.matches("lozinka123", stored)).isTrue();
    }

    @Test
    @DisplayName("pogresna lozinka se odbija")
    void rejectsWrongPassword() {
        String stored = passwords.hash("lozinka123");
        assertThat(passwords.matches("lozinka124", stored)).isFalse();
        assertThat(passwords.matches("", stored)).isFalse();
        assertThat(passwords.matches("LOZINKA123", stored)).isFalse();
    }

    @Test
    @DisplayName("provera radi i sa slovima izvan engleske azbuke")
    void worksWithNonAsciiPassword() {
        String stored = passwords.hash("шифра-ћирилица");
        assertThat(passwords.matches("шифра-ћирилица", stored)).isTrue();
        assertThat(passwords.matches("шифра-ћирилица ", stored)).isFalse();
    }

    @Test
    @DisplayName("neispravan zapis u bazi ne obara proveru, nego vraca netacno")
    void malformedStoredValueIsRejected() {
        assertThat(passwords.matches("lozinka123", "bez-dvotacke")).isFalse();
        assertThat(passwords.matches("lozinka123", "a:b")).isFalse();
        assertThat(passwords.matches("lozinka123", "nijeBroj:c28=:c28=")).isFalse();
        assertThat(passwords.matches("lozinka123", "120000:???:???")).isFalse();
    }

    @Test
    @DisplayName("zapis sa drugim brojem iteracija i dalje moze da se proveri")
    void honoursIterationCountFromStoredValue() {
        String stored = passwords.hash("lozinka123");
        String[] parts = stored.split(":");
        String withSameIterations = parts[0] + ":" + parts[1] + ":" + parts[2];

        assertThat(passwords.matches("lozinka123", withSameIterations)).isTrue();
        assertThat(passwords.matches("lozinka123", "1000:" + parts[1] + ":" + parts[2])).isFalse();
    }
}
