package com.monprojet.series.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "serie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Serie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titre;

    @Column(length = 80)
    private String genre;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer anneeSortie;

    private Double note;

    private String imageUrl;

    // Reference to the source TMDB series, used for import/dedup (nullable if
    // manually created)
    private Long tmdbId;

    /**
     * Statut manuel choisi par l'utilisateur.
     * Null = statut non forcé → le front peut afficher un statut auto calculé.
     * Non-null = l'utilisateur a explicitement choisi (ne pas écraser).
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatutVisionnage statutVisionnage;

    @Builder.Default
    @OneToMany(mappedBy = "serie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Saison> saisons = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;
}