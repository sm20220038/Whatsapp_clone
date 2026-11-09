package com.waclone.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "tajna-za-testove-0123456789-abcdefghij";

    private final JwtService jwt = new JwtService(SECRET, 72);

    @Test
    @DisplayName("token ima tri dela odvojena tackom")
    void tokenHasThreeParts() {
        assertThat(jwt.issue("pera").split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("zaglavlje tokena navodi algoritam HS256")
    void headerDeclaresHs256() {
        String header = new String(Base64.getUrlDecoder().decode(jwt.issue("pera").split("\\.")[0]),
                StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    @DisplayName("provera ispravnog tokena vraca korisnicko ime")
    void verifyReturnsUsername() {
        assertThat(jwt.verify(jwt.issue("pera"))).contains("pera");
    }

    @Test
    @DisplayName("izmenjen sadrzaj tokena se odbija")
    void rejectsTamperedPayload() {
        String[] parts = jwt.issue("pera").split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"mika\",\"iat\":1,\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));

        assertThat(jwt.verify(parts[0] + "." + forged + "." + parts[2])).isEmpty();
    }

    @Test
    @DisplayName("token potpisan drugom tajnom se odbija")
    void rejectsTokenSignedWithAnotherSecret() {
        JwtService other = new JwtService("neka-sasvim-druga-tajna-0987654321", 72);
        assertThat(jwt.verify(other.issue("pera"))).isEmpty();
    }

    @Test
    @DisplayName("istekao token se odbija")
    void rejectsExpiredToken() {
        JwtService expiring = new JwtService(SECRET, 0);
        assertThat(expiring.verify(expiring.issue("pera"))).isEmpty();
    }

    @Test
    @DisplayName("token koji nedostaje ili nema oblik tokena se odbija")
    void rejectsMissingOrMalformedToken() {
        assertThat(jwt.verify(null)).isEmpty();
        assertThat(jwt.verify("")).isEmpty();
        assertThat(jwt.verify("samo.dva")).isEmpty();
        assertThat(jwt.verify("a.b.c.d")).isEmpty();
    }

    @Test
    @DisplayName("potpis koji nije ispravan Base64 se odbija bez greske")
    void rejectsSignatureThatIsNotBase64() {
        String[] parts = jwt.issue("pera").split("\\.");
        assertThat(jwt.verify(parts[0] + "." + parts[1] + ".!!!")).isEmpty();
    }

    @Test
    @DisplayName("navodnik u korisnickom imenu ne kvari zapis tokena")
    void escapesQuotesInUsername() {
        Optional<String> sub = jwt.verify(jwt.issue("pe\"ra"));
        assertThat(sub).isPresent();
    }

    @Test
    @DisplayName("dva tokena istog korisnika proveravaju se na isto ime")
    void twoTokensResolveToSameUser() {
        assertThat(jwt.verify(jwt.issue("mika"))).contains("mika");
        assertThat(jwt.verify(jwt.issue("mika"))).contains("mika");
    }
}
