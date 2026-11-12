package com.monprojet.series;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.monprojet.series.entity.StatutUtilisateur;
import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.repository.PhotoProfilRepository;
import com.monprojet.series.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end checks of the profile endpoints: permissions, token renewal and photo lifecycle. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfilIntegrationTest {

    private static final String MOT_DE_PASSE = "motdepasse123";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PhotoProfilRepository photoProfilRepository;
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

    private Utilisateur creerUtilisateur() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        return utilisateurRepository.save(Utilisateur.builder()
                .pseudo("p" + s)
                .email("p" + s + "@test.local")
                .motDePasse(passwordEncoder.encode(MOT_DE_PASSE))
                .statut(StatutUtilisateur.APPROUVE)
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

    private String tokenAdmin() throws Exception {
        return connexion(emailAdmin, motDePasseAdmin);
    }

    private static MockMultipartFile fichier(byte[] octets) {
        return new MockMultipartFile("fichier", "avatar.jpg", "image/jpeg", octets);
    }

    // ---------- Profile ----------

    @Test
    void modifierProfil_changeLIdentite_etLAncienTokenNeFonctionnePlus() throws Exception {
        Utilisateur alice = creerUtilisateur();
        String ancienToken = connexion(alice.getEmail(), MOT_DE_PASSE);
        String nouvelEmail = "nouveau-" + alice.getPseudo() + "@test.local";

        MvcResult resultat = mockMvc.perform(put("/api/utilisateurs/" + alice.getId() + "/profil")
                        .header("Authorization", bearer(ancienToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pseudo", "renomme-" + alice.getPseudo(), "email", nouvelEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.pseudo").value("renomme-" + alice.getPseudo()))
                .andReturn();
        String nouveauToken = JsonPath.read(resultat.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/api/utilisateurs/" + alice.getId()).header("Authorization", bearer(nouveauToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(nouvelEmail));

        // The old token carries the old email, which no longer exists
        mockMvc.perform(get("/api/utilisateurs/" + alice.getId()).header("Authorization", bearer(ancienToken)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void modifierProfil_refuseUnEmailDejaUtilise() throws Exception {
        Utilisateur alice = creerUtilisateur();
        Utilisateur bob = creerUtilisateur();
        String tokenAlice = connexion(alice.getEmail(), MOT_DE_PASSE);

        mockMvc.perform(put("/api/utilisateurs/" + alice.getId() + "/profil")
                        .header("Authorization", bearer(tokenAlice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pseudo", alice.getPseudo(), "email", bob.getEmail()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("email")));
    }

    @Test
    void modifierProfil_refuseDesDonneesInvalides() throws Exception {
        Utilisateur alice = creerUtilisateur();
        String tokenAlice = connexion(alice.getEmail(), MOT_DE_PASSE);

        mockMvc.perform(put("/api/utilisateurs/" + alice.getId() + "/profil")
                        .header("Authorization", bearer(tokenAlice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pseudo", " ", "email", "pas-un-email"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modifierProfil_estRefuseSurLeCompteDUnAutre_meme_pourUnAdministrateur() throws Exception {
        Utilisateur alice = creerUtilisateur();
        Utilisateur bob = creerUtilisateur();
        String corps = json(Map.of("pseudo", "pirate", "email", "pirate@test.local"));

        mockMvc.perform(put("/api/utilisateurs/" + bob.getId() + "/profil")
                        .header("Authorization", bearer(connexion(alice.getEmail(), MOT_DE_PASSE)))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());

        // An admin must not obtain a token for someone else's account
        mockMvc.perform(put("/api/utilisateurs/" + bob.getId() + "/profil")
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
    }

    // ---------- Password ----------

    @Test
    void changerMotDePasse_exigeLeMotDePasseActuel_etFonctionneEnsuite() throws Exception {
        Utilisateur alice = creerUtilisateur();
        String token = connexion(alice.getEmail(), MOT_DE_PASSE);
        String url = "/api/utilisateurs/" + alice.getId() + "/mot-de-passe";

        // Wrong current password: 409, and the session stays valid
        mockMvc.perform(put(url).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ancienMotDePasse", "incorrect-1", "nouveauMotDePasse", "nouveau-mdp-1"))))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/utilisateurs/" + alice.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(put(url).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ancienMotDePasse", MOT_DE_PASSE, "nouveauMotDePasse", "nouveau-mdp-1"))))
                .andExpect(status().isNoContent());

        assertThat(connexion(alice.getEmail(), "nouveau-mdp-1")).isNotBlank();
        mockMvc.perform(post("/api/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", alice.getEmail(), "motDePasse", MOT_DE_PASSE))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changerMotDePasse_refuseUnNouveauMotDePasseTropCourt() throws Exception {
        Utilisateur alice = creerUtilisateur();

        mockMvc.perform(put("/api/utilisateurs/" + alice.getId() + "/mot-de-passe")
                        .header("Authorization", bearer(connexion(alice.getEmail(), MOT_DE_PASSE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ancienMotDePasse", MOT_DE_PASSE, "nouveauMotDePasse", "court"))))
                .andExpect(status().isBadRequest());
    }

    // ---------- Photo ----------

    @Test
    void photo_cycleDeVie_envoi_lecture_suppression() throws Exception {
        Utilisateur alice = creerUtilisateur();
        String token = connexion(alice.getEmail(), MOT_DE_PASSE);
        String url = "/api/utilisateurs/" + alice.getId() + "/photo";

        mockMvc.perform(get(url).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(multipart(url).file(fichier(JPEG)).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoVersion").exists());

        mockMvc.perform(get(url).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG));

        mockMvc.perform(get("/api/utilisateurs/" + alice.getId()).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.photoVersion").exists());

        mockMvc.perform(delete(url).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(url).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/utilisateurs/" + alice.getId()).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.photoVersion").doesNotExist());
    }

    @Test
    void photo_refuseUnFichierQuiNEstPasUneImage() throws Exception {
        Utilisateur alice = creerUtilisateur();
        String token = connexion(alice.getEmail(), MOT_DE_PASSE);
        MockMultipartFile svg = new MockMultipartFile("fichier", "x.jpg", "image/jpeg",
                "<svg onload=alert(1)></svg>".getBytes());

        // The declared type says "image/jpeg": only the file content counts
        mockMvc.perform(multipart("/api/utilisateurs/" + alice.getId() + "/photo")
                        .file(svg).header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Format")));
    }

    @Test
    void photo_envoiRefuseSurLeCompteDUnAutre_etLectureProtegee() throws Exception {
        Utilisateur alice = creerUtilisateur();
        Utilisateur bob = creerUtilisateur();
        String tokenAlice = connexion(alice.getEmail(), MOT_DE_PASSE);
        String tokenBob = connexion(bob.getEmail(), MOT_DE_PASSE);
        String urlAlice = "/api/utilisateurs/" + alice.getId() + "/photo";

        mockMvc.perform(multipart(urlAlice).file(fichier(JPEG)).header("Authorization", bearer(tokenAlice)))
                .andExpect(status().isOk());

        // Another user: neither upload nor read
        mockMvc.perform(multipart(urlAlice).file(fichier(JPEG)).header("Authorization", bearer(tokenBob)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(urlAlice).header("Authorization", bearer(tokenBob)))
                .andExpect(status().isForbidden());

        // An admin cannot upload for someone else, but can read and remove (moderation)
        String tokenAdmin = tokenAdmin();
        mockMvc.perform(multipart(urlAlice).file(fichier(JPEG)).header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(urlAlice).header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(urlAlice).header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isNoContent());

        // No token at all
        mockMvc.perform(get(urlAlice)).andExpect(status().is4xxClientError());
    }

    @Test
    void supprimerLeCompte_supprimeAussiSaPhoto() throws Exception {
        Utilisateur alice = creerUtilisateur();
        String token = connexion(alice.getEmail(), MOT_DE_PASSE);
        mockMvc.perform(multipart("/api/utilisateurs/" + alice.getId() + "/photo")
                        .file(fichier(JPEG)).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertThat(photoProfilRepository.existsById(alice.getId())).isTrue();

        mockMvc.perform(delete("/api/admin/utilisateurs/" + alice.getId())
                        .header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().is2xxSuccessful());

        assertThat(photoProfilRepository.existsById(alice.getId())).isFalse();
    }
}
