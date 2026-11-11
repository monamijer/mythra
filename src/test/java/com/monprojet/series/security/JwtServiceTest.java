package com.monprojet.series.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // Two different Base64 secrets (48 bytes each), used only by these tests
    private static final String SECRET =
            "2fZgkI62k7ToLGkniWBE4EV+Gq9iYzzSjDXrEJCNb7xS5Q76LH64Y92Ko2EpDlP3";
    private static final String AUTRE_SECRET =
            "zdsDdYFQ8HfaI6IdjTP5OEbHY8IWmuv7JzciU9BzdFjtWrbNi3X/YrmnFF47/fuP";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    private UserDetails utilisateur(String email) {
        return User.withUsername(email).password("HASH").roles("USER").build();
    }

    @Test
    void genererToken_contientLEmailDeLUtilisateur() {
        String token = jwtService.genererToken(utilisateur("alice@example.com"));

        assertThat(jwtService.extraireEmail(token)).isEqualTo("alice@example.com");
    }

    @Test
    void estValide_accepteUnTokenFraisPourLeBonUtilisateur() {
        UserDetails alice = utilisateur("alice@example.com");
        String token = jwtService.genererToken(alice);

        assertThat(jwtService.estValide(token, alice)).isTrue();
    }

    @Test
    void estValide_refuseUnTokenPresenteParUnAutreUtilisateur() {
        String token = jwtService.genererToken(utilisateur("alice@example.com"));

        assertThat(jwtService.estValide(token, utilisateur("bob@example.com"))).isFalse();
    }

    @Test
    void estValide_refuseUnTokenExpire() {
        JwtService expireDeja = new JwtService(SECRET, -1_000);
        UserDetails alice = utilisateur("alice@example.com");
        String token = expireDeja.genererToken(alice);

        assertThat(jwtService.estValide(token, alice)).isFalse();
    }

    @Test
    void estValide_refuseUnTokenSigneAvecUneAutreCle() {
        JwtService autreService = new JwtService(AUTRE_SECRET, 60_000);
        UserDetails alice = utilisateur("alice@example.com");
        String tokenFalsifie = autreService.genererToken(alice);

        assertThat(jwtService.estValide(tokenFalsifie, alice)).isFalse();
    }

    @Test
    void estValide_refuseUneChaineQuiNEstPasUnToken() {
        assertThat(jwtService.estValide("pas-un-token", utilisateur("alice@example.com"))).isFalse();
    }

    @Test
    void extraireEmail_leveUneExceptionPourUnTokenSigneAvecUneAutreCle() {
        String tokenFalsifie = new JwtService(AUTRE_SECRET, 60_000).genererToken(utilisateur("alice@example.com"));

        assertThatThrownBy(() -> jwtService.extraireEmail(tokenFalsifie)).isInstanceOf(Exception.class);
    }
}
