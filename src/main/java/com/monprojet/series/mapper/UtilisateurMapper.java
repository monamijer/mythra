// UtilisateurMapper.java
package com.monprojet.series.mapper;

import com.monprojet.series.dto.response.UtilisateurResponse;
import com.monprojet.series.entity.Utilisateur;

public final class UtilisateurMapper {

    private UtilisateurMapper() {}

    public static UtilisateurResponse toResponse(Utilisateur utilisateur) {
        return new UtilisateurResponse(
                utilisateur.getId(),
                utilisateur.getPseudo(),
                utilisateur.getEmail(),
                utilisateur.getRole().name(),
                utilisateur.getStatut().name(),
                utilisateur.getDateInscription(),
                utilisateur.getDerniereConnexion(),
                utilisateur.getPhotoVersion()
        );
    }
}