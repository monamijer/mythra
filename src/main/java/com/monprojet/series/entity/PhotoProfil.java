package com.monprojet.series.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Profile picture of a user, kept in its own table so that loading a user
 * (done on every authenticated request) never drags the image bytes along.
 * The primary key is the owner's id: one photo at most per account.
 */
@Entity
@Table(name = "photo_profil")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PhotoProfil {

    @Id
    @Column(name = "utilisateur_id")
    private Long utilisateurId;

    // Detected from the file signature, never taken from the client's header
    @Column(name = "type_contenu", nullable = false, length = 30)
    private String typeContenu;

    @Lob
    @Column(nullable = false)
    private byte[] donnees;
}
