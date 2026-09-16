package com.monprojet.series.dto.response;

/**
 * Bande-annonce YouTube d'une série.
 *
 * @param cle        clé YouTube (ex: "dQw4w9WgXcQ")
 * @param nom        titre de la vidéo
 * @param type       Trailer, Teaser, Clip...
 * @param youtubeUrl URL complète de la vidéo sur YouTube
 * @param embedUrl   URL pour intégration iframe (youtube-nocookie pour éviter les cookies)
 */
public record VideoResponse(
        String cle,
        String nom,
        String type,
        String youtubeUrl,
        String embedUrl
) {
}