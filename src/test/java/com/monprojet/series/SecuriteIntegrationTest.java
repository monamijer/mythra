package com.monprojet.series;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.monprojet.series.entity.StatutUtilisateur;
import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of authentication and permissions: the whole Spring context
 * (security filter, method security, services, JPA) on an in-memory H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecuriteIntegrationTest {

    private static final String MOT_DE_PASSE = "motdepasse123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}") private String emailAdmin;
    @Value("${app.admin.password}") private String motDePasseAdmin;

    // ---------- Helpers ----------

    private String json(Object objet) throws Exception {
        return objectMapper.writeValueAsString(objet);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String suffixe() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // Inserted directly in the database: fast, and independent from the signup endpoint
    private Utilisateur creerUtilisateur(StatutUtilisateur statut) {
        String s = suffixe();
        return utilisateurRepository.save(Utilisateur.builder()
                .pseudo("u" + s)
                .email("u" + s + "@test.local")
                .motDePasse(passwordEncoder.encode(MOT_DE_PASSE))
                .statut(statut)
                .build());
    }

    private String connexion(String email, String motDePasse) throws Exception {
        MvcResult resultat = mockMvc.perform(post("/api/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "motDePasse", motDePasse))))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(resultat.getResponse().getContentAsString(), "$.token");
    }

    private String tokenDe(Utilisateur utilisateur) throws Exception {
        return connexion(utilisateur.getEmail(), MOT_DE_PASSE);
    }

    private String tokenAdmin() throws Exception {
        return connexion(emailAdmin, motDePasseAdmin);
    }

    private long creerEtLireId(String url, String token, Object corps) throws Exception {
        MvcResult resultat = mockMvc.perform(post(url)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(corps)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(resultat.getResponse().getContentAsString(), "$.id")).longValue();
    }

    // Returns {serieId, saisonId, episodeId}
    private long[] creerSerieAvecUnEpisode(Utilisateur proprietaire, String token) throws Exception {
        long userId = proprietaire.getId();
        long serieId = creerEtLireId("/api/utilisateurs/" + userId + "/series", token,
                Map.of("titre", "Serie de test"));
        long saisonId = creerEtLireId("/api/utilisateurs/" + userId + "/series/" + serieId + "/saisons", token,
                Map.of("numero", 1, "nbEpisodes", 1));
        long episodeId = creerEtLireId("/api/saisons/" + saisonId + "/episodes", token,
                Map.of("numero", 1, "titre", "Pilot", "dureeMinutes", 50));
        return new long[] {serieId, saisonId, episodeId};
    }

    // ---------- Authentication ----------

    @Test
    void inscription_creeUnCompteEnAttente_etLaConnexionEstRefusee() throws Exception {
        String s = suffixe();
        String email = "n" + s + "@test.local";

        mockMvc.perform(post("/api/auth/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pseudo", "n" + s, "email", email, "motDePasse", MOT_DE_PASSE))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(post("/api/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "motDePasse", MOT_DE_PASSE))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("attente")));
    }

    @Test
    void inscription_refuseUnEmailDejaUtilise() throws Exception {
        Utilisateur existant = creerUtilisateur(StatutUtilisateur.APPROUVE);

        mockMvc.perform(post("/api/auth/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pseudo", "autre" + suffixe(), "email", existant.getEmail(),
                                "motDePasse", MOT_DE_PASSE))))
                .andExpect(status().isConflict());
    }

    @Test
    void inscription_refuseDesDonneesInvalides() throws Exception {
        mockMvc.perform(post("/api/auth/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pseudo", "x", "email", "pas-un-email", "motDePasse", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isNotEmpty());
    }

    @Test
    void connexion_refuseUnMauvaisMotDePasse() throws Exception {
        Utilisateur alice = creerUtilisateur(StatutUtilisateur.APPROUVE);

        mockMvc.perform(post("/api/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", alice.getEmail(), "motDePasse", "faux-mot-de-passe"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ladminApprouveUnCompte_qui_peutEnsuiteSeConnecter() throws Exception {
        Utilisateur enAttente = creerUtilisateur(StatutUtilisateur.EN_ATTENTE);

        mockMvc.perform(patch("/api/admin/utilisateurs/" + enAttente.getId() + "/approuver")
                        .header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isOk());

        assertThat(connexion(enAttente.getEmail(), MOT_DE_PASSE)).isNotBlank();
    }

    @Test
    void unCompteRefuseePerdSonAccesMemeAvecUnTokenEncoreValide() throws Exception {
        Utilisateur alice = creerUtilisateur(StatutUtilisateur.APPROUVE);
        String token = tokenDe(alice);
        String urlSeries = "/api/utilisateurs/" + alice.getId() + "/series";

        mockMvc.perform(get(urlSeries).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        alice.setStatut(StatutUtilisateur.REFUSE);
        utilisateurRepository.save(alice);

        mockMvc.perform(get(urlSeries).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    // ---------- Permissions ----------

    @Test
    void sansToken_lAccesEstRefuse() throws Exception {
        Utilisateur alice = creerUtilisateur(StatutUtilisateur.APPROUVE);

        mockMvc.perform(get("/api/utilisateurs/" + alice.getId() + "/series"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unUtilisateurNeVoitQueSesPropresSeries() throws Exception {
        Utilisateur alice = creerUtilisateur(StatutUtilisateur.APPROUVE);
        Utilisateur bob = creerUtilisateur(StatutUtilisateur.APPROUVE);
        String tokenAlice = tokenDe(alice);
        String tokenBob = tokenDe(bob);
        creerEtLireId("/api/utilisateurs/" + alice.getId() + "/series", tokenAlice, Map.of("titre", "Breaking Bad"));

        // Alice sees her series
        mockMvc.perform(get("/api/utilisateurs/" + alice.getId() + "/series")
                        .header("Authorization", bearer(tokenAlice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Bob cannot read Alice's list...
        mockMvc.perform(get("/api/utilisateurs/" + alice.getId() + "/series")
                        .header("Authorization", bearer(tokenBob)))
                .andExpect(status().isForbidden());

        // ...and his own list is empty
        mockMvc.perform(get("/api/utilisateurs/" + bob.getId() + "/series")
                        .header("Authorization", bearer(tokenBob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unUtilisateurNePeutPasLireLesEpisodesDUneSaisonDUnAutre() throws Exception {
        Utilisateur alice = creerUtilisateur(StatutUtilisateur.APPROUVE);
        Utilisateur bob = creerUtilisateur(StatutUtilisateur.APPROUVE);
        String tokenAlice = tokenDe(alice);
        long saisonId = creerSerieAvecUnEpisode(alice, tokenAlice)[1];

        mockMvc.perform(get("/api/saisons/" + saisonId + "/episodes")
                        .header("Authorization", bearer(tokenAlice)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/saisons/" + saisonId + "/episodes")
                        .header("Authorization", bearer(tokenDe(bob))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unUtilisateurNePeutPasMarquerCommeVuUnEpisodeDUnAutre() throws Exception {
        Utilisateur alice = creerUtilisateur(StatutUtilisateur.APPROUVE);
        Utilisateur bob = creerUtilisateur(StatutUtilisateur.APPROUVE);
        long episodeId = creerSerieAvecUnEpisode(alice, tokenDe(alice))[2];

        mockMvc.perform(post("/api/utilisateurs/" + bob.getId() + "/visionnages/" + episodeId)
                        .header("Authorization", bearer(tokenDe(bob))))
                .andExpect(status().isNotFound());
    }

    @Test
    void lesRoutesAdminSontReserveesAuxAdministrateurs() throws Exception {
        Utilisateur alice = creerUtilisateur(StatutUtilisateur.APPROUVE);
        String tokenAlice = tokenDe(alice);

        mockMvc.perform(get("/api/admin/utilisateurs").header("Authorization", bearer(tokenAlice)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/utilisateurs").header("Authorization", bearer(tokenAlice)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/utilisateurs").header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void unAdministrateurAccedeAuxDonneesDeNImporteQuelUtilisateur() throws Exception {
        Utilisateur alice = creerUtilisateur(StatutUtilisateur.APPROUVE);
        creerEtLireId("/api/utilisateurs/" + alice.getId() + "/series", tokenDe(alice), Map.of("titre", "Dark"));

        mockMvc.perform(get("/api/utilisateurs/" + alice.getId() + "/series")
                        .header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // ---------- Deletions keep the database consistent ----------

    @Test
    void supprimerUneSerieDontLesEpisodesSontVus_reussit() throws Exception {
        Utilisateur alice = creerUtilisateur(StatutUtilisateur.APPROUVE);
        String token = tokenDe(alice);
        long[] ids = creerSerieAvecUnEpisode(alice, token);
        String racine = "/api/utilisateurs/" + alice.getId();

        mockMvc.perform(post(racine + "/visionnages/" + ids[2]).header("Authorization", bearer(token)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(racine + "/series/" + ids[0]).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(racine + "/series").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void lAdminSupprimeUnUtilisateurAvecSesSeriesEtSesVisionnages() throws Exception {
        Utilisateur alice = creerUtilisateur(StatutUtilisateur.APPROUVE);
        String tokenAlice = tokenDe(alice);
        long episodeId = creerSerieAvecUnEpisode(alice, tokenAlice)[2];
        mockMvc.perform(post("/api/utilisateurs/" + alice.getId() + "/visionnages/" + episodeId)
                        .header("Authorization", bearer(tokenAlice)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/utilisateurs/" + alice.getId())
                        .header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isNoContent());

        assertThat(utilisateurRepository.findById(alice.getId())).isEmpty();
    }
}
