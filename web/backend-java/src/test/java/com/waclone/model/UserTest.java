package com.waclone.model;

import com.waclone.model.Enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("konstruktor postavlja ime, ime za prikaz i hes lozinke")
    void constructorSetsFields() {
        User user = new User("pera", "Pera Peric", "120000:so:kljuc");

        assertThat(user.getUsername()).isEqualTo("pera");
        assertThat(user.getDisplayName()).isEqualTo("Pera Peric");
        assertThat(user.getPasswordHash()).isEqualTo("120000:so:kljuc");
    }

    @Test
    @DisplayName("novi korisnik ima ulogu USER i nije povezan")
    void defaultsAreUserAndOffline() {
        User user = new User("pera", "Pera", "hes");

        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isOnline()).isFalse();
        assertThat(user.getLastSeen()).isZero();
        assertThat(user.getAvatarId()).isNull();
    }

    @Test
    @DisplayName("kljuc korisnika dodeljuje baza, pa je pre upisa prazan")
    void identifierIsAssignedByDatabase() {
        assertThat(new User("pera", "Pera", "hes").getUserId()).isNull();
    }

    @Test
    @DisplayName("ime za prikaz, uloga, stanje i profilna slika mogu da se menjaju")
    void mutableFieldsCanBeChanged() {
        User user = new User("pera", "Pera", "hes");

        user.setDisplayName("Petar");
        user.setRole(Role.ADMIN);
        user.setOnline(true);
        user.setLastSeen(1_700_000_000_000L);
        user.setAvatarId("0123456789abcdef0123456789abcdef");

        assertThat(user.getDisplayName()).isEqualTo("Petar");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.isOnline()).isTrue();
        assertThat(user.getLastSeen()).isEqualTo(1_700_000_000_000L);
        assertThat(user.getAvatarId()).isEqualTo("0123456789abcdef0123456789abcdef");
    }
}
