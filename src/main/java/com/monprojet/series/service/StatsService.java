package com.monprojet.series.service;

import com.monprojet.series.dto.response.StatsUtilisateurResponse;
import com.monprojet.series.dto.response.StatsUtilisateurResponse.GenreStat;
import com.monprojet.series.dto.response.StatsUtilisateurResponse.VisionnageRecent;
import com.monprojet.series.entity.Serie;
import com.monprojet.series.entity.StatutVisionnage;
import com.monprojet.series.entity.Visionnage;
import com.monprojet.series.repository.EpisodeRepository;
import com.monprojet.series.repository.SerieRepository;
import com.monprojet.series.repository.VisionnageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final SerieRepository serieRepository;
    private final EpisodeRepository episodeRepository;
    private final VisionnageRepository visionnageRepository;

    public StatsUtilisateurResponse calculer(Long utilisateurId) {

        // ----- Séries par statut -----
        long total = serieRepository.countByUtilisateur_Id(utilisateurId);
        long terminees = serieRepository.countByUtilisateur_IdAndStatutVisionnage(utilisateurId, StatutVisionnage.TERMINEE);
        long enCours = serieRepository.countByUtilisateur_IdAndStatutVisionnage(utilisateurId, StatutVisionnage.EN_COURS);
        long aVoir = serieRepository.countByUtilisateur_IdAndStatutVisionnage(utilisateurId, StatutVisionnage.A_VOIR);
        long abandonnees = serieRepository.countByUtilisateur_IdAndStatutVisionnage(utilisateurId, StatutVisionnage.ABANDONNEE);

        // ----- Temps -----
        long tempsVisionne = visionnageRepository.sommeDureeVisionneeTotale(utilisateurId);
        long tempsTotal = episodeRepository.sommeDureeTotaleUtilisateur(utilisateurId);
        long tempsRestant = Math.max(0, tempsTotal - tempsVisionne);

        // ----- Épisodes -----
        long episodesVus = visionnageRepository.countByUtilisateur_Id(utilisateurId);
        long episodesTotal = episodeRepository.findAll().stream()
                .filter(e -> e.getSaison().getSerie().getUtilisateur().getId().equals(utilisateurId))
                .count();

        // ----- Notes personnelles -----
        List<Serie> toutesSeries = serieRepository.findByUtilisateurId(utilisateurId);
        List<Integer> notes = toutesSeries.stream()
                .map(Serie::getNotePersonnelle)
                .filter(n -> n != null)
                .toList();
        Double moyenne = notes.isEmpty()
                ? null
                : Math.round(notes.stream().mapToInt(Integer::intValue).average().orElse(0) * 10) / 10.0;
        long seriesNotees = notes.size();
        long seriesNonNotees = total - seriesNotees;

        // ----- Top genres (par épisodes vus) -----
        List<Visionnage> tous = visionnageRepository.findByUtilisateur_Id(utilisateurId);
        Map<String, long[]> parGenre = new HashMap<>(); // genre -> [episodes, minutes]
        for (Visionnage v : tous) {
            String genre = v.getEpisode().getSaison().getSerie().getGenre();
            if (genre == null || genre.isBlank()) continue;
            Integer duree = v.getEpisode().getDureeMinutes();
            long min = duree == null ? 0 : duree;
            parGenre.computeIfAbsent(genre, k -> new long[2]);
            parGenre.get(genre)[0]++;
            parGenre.get(genre)[1] += min;
        }
        List<GenreStat> topGenres = parGenre.entrySet().stream()
                .map(e -> new GenreStat(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(GenreStat::episodesVus).reversed())
                .limit(5)
                .toList();

        // ----- Activité récente -----
        List<VisionnageRecent> recent = visionnageRepository
                .findTop10ByUtilisateur_IdOrderByDateVisionnageDesc(utilisateurId)
                .stream()
                .map(v -> new VisionnageRecent(
                        v.getEpisode().getSaison().getSerie().getTitre(),
                        v.getEpisode().getTitre(),
                        v.getEpisode().getSaison().getNumero(),
                        v.getEpisode().getNumero(),
                        v.getDateVisionnage()
                ))
                .toList();

        return new StatsUtilisateurResponse(
                total, terminees, enCours, aVoir, abandonnees,
                tempsVisionne, tempsRestant,
                episodesVus, episodesTotal,
                moyenne, seriesNotees, seriesNonNotees,
                topGenres, recent
        );
    }
}