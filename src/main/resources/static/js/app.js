// js/app.js
// Vanilla JS, no build step — fetch() against the same-origin Spring Boot API.

const API = "/api";

// --- Thème clair / sombre (appliqué le plus tôt possible) ---

const CLE_THEME = "theme";

function appliquerTheme(theme) {
  document.documentElement.setAttribute("data-theme", theme);
  const icone = theme === "dark" ? "bi bi-sun" : "bi bi-moon-stars";
  document.querySelectorAll("#btn-theme i, #btn-theme-drawer i").forEach((i) => { i.className = icone; });
}

function themeInitial() {
  const sauvegarde = localStorage.getItem(CLE_THEME);
  if (sauvegarde === "dark" || sauvegarde === "light") return sauvegarde;
  // Pas de préférence enregistrée → on suit le système
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function basculerTheme() {
  const actuel = document.documentElement.getAttribute("data-theme") || "light";
  const nouveau = actuel === "dark" ? "light" : "dark";
  localStorage.setItem(CLE_THEME, nouveau);
  appliquerTheme(nouveau);
}

// Applique le thème immédiatement (avant le rendu du body si possible)
appliquerTheme(themeInitial());

const IMAGE_PLACEHOLDER =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='200' height='300'%3E" +
  "%3Crect width='200' height='300' fill='%23ddd'/%3E" +
  "%3Ctext x='50%25' y='50%25' font-size='16' fill='%23888' text-anchor='middle' dy='.3em'%3EPas d'image%3C/text%3E" +
  "%3C/svg%3E";

// --- Auth / session storage ---

function getToken() { return localStorage.getItem("token"); }
function getUtilisateurId() { return localStorage.getItem("utilisateurId"); }
function getPseudo() { return localStorage.getItem("pseudo"); }

function setSession(token, utilisateurId, pseudo, role, statut) {
  localStorage.setItem("token", token);
  localStorage.setItem("utilisateurId", utilisateurId);
  localStorage.setItem("pseudo", pseudo);
  localStorage.setItem("role", role);
  localStorage.setItem("statut", statut);
}

function getRole() { return localStorage.getItem("role"); }
function getStatut() { return localStorage.getItem("statut"); }

function deconnexion() {
  localStorage.removeItem("token");
  localStorage.removeItem("utilisateurId");
  localStorage.removeItem("pseudo");
  localStorage.removeItem("role");
  localStorage.removeItem("statut");
  location.reload();
}

// --- Auth tabs ---

document.querySelectorAll(".auth-tab").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".auth-tab").forEach((b) => {
      b.classList.remove("active");
      b.setAttribute("aria-selected", "false");
    });
    document.querySelectorAll(".auth-panel").forEach((p) => {
      p.classList.remove("active");
      p.setAttribute("hidden", "");
    });
    btn.classList.add("active");
    btn.setAttribute("aria-selected", "true");
    const panel = document.getElementById(`form-${btn.dataset.auth}`);
    panel.classList.add("active");
    panel.removeAttribute("hidden");
    document.getElementById("login-erreur").textContent = "";
  });
});

// --- Generic API wrapper ---

async function appelApi(url, options = {}) {
  const token = getToken();
  const headers = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const reponse = await fetch(url, { headers, ...options });

  // On a protected route, 401/403 means the session is gone: back to the login screen.
  // On /auth/ routes the server's own message (wrong password, account pending...)
  // must reach the user, so those responses go through the normal error path below.
  if ((reponse.status === 401 || reponse.status === 403) && !url.includes("/auth/")) {
    deconnexion();
    throw new Error("Non authentifié");
  }

  if (!reponse.ok) {
    const erreur = await reponse.json().catch(() => ({ message: reponse.statusText }));
    throw new Error(erreur.message || "Erreur API");
  }
  return reponse.status === 204 ? null : reponse.json();
}

/** Escapes untrusted text before it is inserted into an HTML template. */
function esc(valeur) {
  return String(valeur ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/** Non-blocking notification (replaces alert()). type: "succes" | "erreur" | "info". */
function afficherToast(message, type = "info", duree = 4500) {
  const zone = document.getElementById("toasts");
  if (!zone) return;
  const icones = { succes: "check-circle-fill", erreur: "exclamation-triangle-fill", info: "info-circle-fill" };
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.setAttribute("role", type === "erreur" ? "alert" : "status");
  toast.innerHTML = `<i class="bi bi-${icones[type] || icones.info}" aria-hidden="true"></i><span></span>`;
  toast.querySelector("span").textContent = message;
  zone.appendChild(toast);
  setTimeout(() => {
    toast.classList.add("sortie");
    setTimeout(() => toast.remove(), 220);
  }, duree);
}

// --- Import button (shared by poster cards and the detail modal) ---

const importsEnCours = new Set();

function htmlBoutonImport(tmdbId, libelle = "Importer", id = "") {
  return `<button type="button" class="btn-importer"${id ? ` id="${id}"` : ""} data-tmdb-id="${esc(tmdbId)}" data-etat="idle" data-libelle="${esc(libelle)}"><i class="bi bi-plus-lg" aria-hidden="true"></i><span class="btn-txt">${esc(libelle)}</span></button>`;
}

/** Sets the visual + ARIA state of an import button: idle | loading | ok | erreur. */
function majBoutonImport(btn, etat) {
  const libelle = btn.dataset.libelle || "Importer";
  const etats = {
    idle: ['<i class="bi bi-plus-lg" aria-hidden="true"></i>', libelle],
    loading: ['<span class="spinner" aria-hidden="true"></span>', "Import…"],
    ok: ['<i class="bi bi-check2" aria-hidden="true"></i>', "Importé"],
    erreur: ['<i class="bi bi-arrow-repeat" aria-hidden="true"></i>', "Réessayer"],
  };
  const [icone, texte] = etats[etat];
  btn.dataset.etat = etat;
  btn.innerHTML = `${icone}<span class="btn-txt">${esc(texte)}</span>`;
  // aria-disabled (not disabled) so keyboard focus is not lost during the request
  btn.setAttribute("aria-disabled", String(etat === "loading" || etat === "ok"));
  btn.setAttribute("aria-busy", String(etat === "loading"));
}

function synchroniserImport(tmdbId, etat) {
  document.querySelectorAll(`.btn-importer[data-tmdb-id="${CSS.escape(String(tmdbId))}"]`)
    .forEach((b) => majBoutonImport(b, etat));
}

function serieDejaImportee(tmdbId) {
  return seriesEnMemoire.some((x) => x.serie && x.serie.tmdbId != null && String(x.serie.tmdbId) === String(tmdbId));
}

async function importerSerie(tmdbId, titre) {
  const cle = String(tmdbId);
  if (importsEnCours.has(cle)) return; // prevents repeated submissions
  importsEnCours.add(cle);
  synchroniserImport(cle, "loading");
  try {
    await appelApi(`${API}/utilisateurs/${getUtilisateurId()}/tmdb/importer/${tmdbId}`, { method: "POST" });
    synchroniserImport(cle, "ok");
    afficherToast(titre ? `« ${titre} » a été ajoutée à votre bibliothèque.` : "Série ajoutée à votre bibliothèque.", "succes");
    chargerMesSeries();
  } catch (err) {
    synchroniserImport(cle, "erreur");
    afficherToast(err.message, "erreur");
  } finally {
    importsEnCours.delete(cle);
  }
}

function brancherBoutonImport(btn, titre) {
  if (serieDejaImportee(btn.dataset.tmdbId)) majBoutonImport(btn, "ok");
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    if (btn.dataset.etat === "loading" || btn.dataset.etat === "ok") return;
    importerSerie(btn.dataset.tmdbId, titre);
  });
}

function creerElement(html) {
  const conteneur = document.createElement("div");
  conteneur.innerHTML = html.trim();
  return conteneur.firstElementChild;
}

function attacherPlaceholder(img) {
  if (!img) return;
  img.addEventListener("error", () => { img.src = IMAGE_PLACEHOLDER; }, { once: true });
}

function messageVide(texte) {
  return `<p class="aucun-resultat" role="status"><i class="bi bi-binoculars" aria-hidden="true"></i><span>${texte}</span></p>`;
}

/** Placeholder cards shown while TMDB results load. */
function squelettes(n = 10) {
  return Array.from({ length: n }, () =>
    '<div class="serie-card poster skeleton" aria-hidden="true"><div class="sk-img"></div></div>'
  ).join("") + '<span class="sr-only" role="status">Chargement des résultats</span>';
}
function messageChargement(texte = "Chargement...") {
  return `<p class="chargement" role="status">${texte}</p>`;
}
function messageErreur(texte) {
  return `<p class="erreur-api" role="alert">Erreur : ${texte}</p>`;
}

/** Génère un affichage d'étoiles ★★★☆☆ + note /10. */
function afficherEtoiles(note) {
  if (!note) return "";
  const pleines = Math.round(note / 2); // 1-10 → 1-5 étoiles
  return "★".repeat(pleines) + "☆".repeat(5 - pleines);
}

// --- Login / Inscription ---

function afficherLogin() {
  document.getElementById("app").classList.add("hidden");
  document.getElementById("login").classList.remove("hidden");
}

function afficherApp(pseudo) {
  document.getElementById("login").classList.add("hidden");
  document.getElementById("app").classList.remove("hidden");
  if (pseudo) {
    document.getElementById("nom-utilisateur").textContent = pseudo;
    document.getElementById("drawer-pseudo").textContent = pseudo;
    const initiale = pseudo.trim().charAt(0) || "?";
    document.getElementById("avatar-utilisateur").textContent = initiale;
    document.getElementById("drawer-avatar").textContent = initiale;
  }
  appliquerTheme(themeInitial()); // rafraîchit l'icône une fois le bouton visible
  afficherOngletAdminSiBesoin();
  chargerMesSeries();
  chargerGenres();
}

document.getElementById("form-connexion").addEventListener("submit", async (e) => {
  e.preventDefault();
  const email = document.getElementById("login-email").value;
  const motDePasse = document.getElementById("login-motdepasse").value;
  try {
    const data = await appelApi(`${API}/auth/connexion`, {
      method: "POST",
      body: JSON.stringify({ email, motDePasse }),
    });
    setSession(data.token, data.utilisateurId, data.pseudo, data.role, data.statut);
    afficherApp(data.pseudo);
  } catch (err) {
    document.getElementById("login-erreur").textContent = err.message;
  }
});

document.getElementById("form-inscription").addEventListener("submit", async (e) => {
  e.preventDefault();
  const pseudo = document.getElementById("inscription-pseudo").value;
  const email = document.getElementById("inscription-email").value;
  const motDePasse = document.getElementById("inscription-motdepasse").value;
  const confirmation = document.getElementById("inscription-motdepasse-confirmation").value;

  const erreurEl = document.getElementById("login-erreur");
  const succesEl = document.getElementById("login-succes");
  erreurEl.textContent = "";
  succesEl.classList.add("hidden");
  succesEl.textContent = "";

  if (motDePasse !== confirmation) {
    erreurEl.textContent = "Les mots de passe ne correspondent pas.";
    return;
  }
  if (motDePasse.length < 8) {
    erreurEl.textContent = "Le mot de passe doit contenir au moins 8 caractères.";
    return;
  }

  try {
    const data = await appelApi(`${API}/auth/inscription`, {
      method: "POST",
      body: JSON.stringify({ pseudo, email, motDePasse }),
    });

    succesEl.textContent = data.message || "Compte créé. En attente d'approbation par un administrateur.";
    succesEl.classList.remove("hidden");
    e.target.reset();

    setTimeout(() => {
      document.getElementById("tab-connexion").click();
    }, 3000);

  } catch (err) {
    erreurEl.textContent = err.message;
  }
});

document.getElementById("btn-deconnexion")?.addEventListener("click", deconnexion);

// --- Main tabs ---

let ongletDecouvrirCharge = false;

function activerOnglet(nom, { chargerDefaut = true } = {}) {
  document.querySelectorAll(".tab-btn").forEach((b) => {
    const actif = b.dataset.tab === nom;
    b.classList.toggle("active", actif);
    if (actif) b.setAttribute("aria-current", "page"); else b.removeAttribute("aria-current");
  });
  document.querySelectorAll(".drawer-link[data-tab]").forEach((l) => {
    const actif = l.dataset.tab === nom && !l.dataset.cat && !l.dataset.focus && !("statut" in l.dataset);
    l.classList.toggle("active", actif);
  });
  document.querySelectorAll(".tab-panel").forEach((p) => {
    p.classList.remove("active");
    p.setAttribute("hidden", "");
  });
  const panneau = document.getElementById(nom);
  panneau.classList.add("active");
  panneau.removeAttribute("hidden");

  if (nom === "decouvrir" && !ongletDecouvrirCharge && chargerDefaut) {
    ongletDecouvrirCharge = true;
    chargerCategorieTmdb("populaires", document.querySelector(".souscat-btn[data-cat='populaires']"));
  }
  if (nom === "admin") {
    chargerStatsAdmin();
    chargerListeAdmin();
  }
  if (nom === "stats") {
    chargerMesStats();
  }
}

document.querySelectorAll(".tab-btn, .app-header .brand[data-tab]").forEach((btn) => {
  btn.addEventListener("click", (e) => {
    e.preventDefault();
    activerOnglet(btn.dataset.tab);
  });
});

// --- Navigation drawer ---

const drawer = document.getElementById("drawer");
const drawerOverlay = document.getElementById("drawer-overlay");
const btnMenu = document.getElementById("btn-menu");

function ouvrirDrawer() {
  drawerOverlay.hidden = false;
  drawer.removeAttribute("inert");
  document.body.classList.add("drawer-ouvert");
  btnMenu.setAttribute("aria-expanded", "true");
  requestAnimationFrame(() => {
    drawer.classList.add("open");
    drawerOverlay.classList.add("open");
  });
  document.getElementById("btn-menu-fermer").focus();
}

function fermerDrawer({ rendreFocus = true } = {}) {
  if (!drawer.classList.contains("open")) return;
  drawer.classList.remove("open");
  drawerOverlay.classList.remove("open");
  drawer.setAttribute("inert", "");
  document.body.classList.remove("drawer-ouvert");
  btnMenu.setAttribute("aria-expanded", "false");
  setTimeout(() => { if (!drawer.classList.contains("open")) drawerOverlay.hidden = true; }, 260);
  if (rendreFocus) btnMenu.focus();
}

btnMenu.addEventListener("click", ouvrirDrawer);
document.getElementById("btn-menu-fermer").addEventListener("click", () => fermerDrawer());
drawerOverlay.addEventListener("click", () => fermerDrawer());

drawer.addEventListener("keydown", (e) => {
  if (e.key === "Escape") { e.stopPropagation(); fermerDrawer(); return; }
  if (e.key !== "Tab") return;
  // Focus trap inside the drawer
  const focusables = drawer.querySelectorAll("button:not([disabled]):not(.hidden)");
  const visibles = [...focusables].filter((el) => el.offsetParent !== null);
  const premier = visibles[0], dernier = visibles[visibles.length - 1];
  if (e.shiftKey && document.activeElement === premier) { e.preventDefault(); dernier.focus(); }
  else if (!e.shiftKey && document.activeElement === dernier) { e.preventDefault(); premier.focus(); }
});

/** Shared in-app navigation used by the drawer and the footer (data-tab / data-cat / data-statut / data-mode / data-focus). */
function naviguerVers({ tab, cat, statut, focus, mode }) {
  activerOnglet(tab, { chargerDefaut: !cat });
  if (cat) {
    ongletDecouvrirCharge = true;
    chargerCategorieTmdb(cat, document.querySelector(`.souscat-btn[data-cat='${cat}']`));
  }
  if (statut !== undefined) {
    const sel = document.getElementById("select-filtre-statut");
    sel.value = statut;
    sel.dispatchEvent(new Event("change"));
  }
  if (mode) definirModeRecherche(mode);
  if (focus) document.getElementById(focus)?.focus();
  else document.getElementById("contenu-principal").focus({ preventScroll: true });
  window.scrollTo({ top: 0, behavior: reduireMouvement() ? "auto" : "smooth" });
}

function reduireMouvement() {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

drawer.querySelectorAll(".drawer-link").forEach((lien) => {
  lien.addEventListener("click", () => {
    fermerDrawer({ rendreFocus: false });
    naviguerVers(lien.dataset);
  });
});

// --- Footer ---

document.querySelectorAll(".app-footer a[data-tab]").forEach((lien) => {
  lien.addEventListener("click", (e) => {
    e.preventDefault();
    naviguerVers(lien.dataset);
  });
});

document.getElementById("footer-annee").textContent = new Date().getFullYear();

document.getElementById("btn-haut-page").addEventListener("click", () => {
  window.scrollTo({ top: 0, behavior: reduireMouvement() ? "auto" : "smooth" });
  document.getElementById("contenu-principal").focus({ preventScroll: true });
});

document.getElementById("btn-theme-drawer").addEventListener("click", basculerTheme);
document.getElementById("btn-deconnexion-drawer").addEventListener("click", () => {
  fermerDrawer({ rendreFocus: false });
  deconnexion();
});

// --- My series ---

let seriesEnMemoire = [];
let filtreStatut = "";

const LIBELLES_STATUT = {
  A_VOIR: "À voir",
  EN_COURS: "En cours",
  TERMINEE: "Terminée",
  ABANDONNEE: "Abandonnée",
};

function statutAuto(serie, progression) {
  if (!progression || progression.episodesTotal === 0) return "A_VOIR";
  if (progression.pourcentage >= 100) return "TERMINEE";
  if (progression.episodesVus > 0) return "EN_COURS";
  return "A_VOIR";
}

function statutEffectif(serie, progression) {
  return serie.statutVisionnage || statutAuto(serie, progression);
}

async function chargerMesSeries() {
  const conteneur = document.getElementById("liste-series");
  conteneur.innerHTML = messageChargement();

  let series;
  try {
    series = await appelApi(`${API}/utilisateurs/${getUtilisateurId()}/series`);
  } catch (err) {
    conteneur.innerHTML = messageErreur(err.message);
    return;
  }

  if (!Array.isArray(series) || series.length === 0) {
    conteneur.innerHTML = messageVide(
      "Aucune série dans votre liste pour l'instant. Ajoutez-en une ci-dessus ou explorez l'onglet Découvrir."
    );
    seriesEnMemoire = [];
    return;
  }

  seriesEnMemoire = [];
  for (const serie of series) {
    let progression = null;
    try {
      progression = await appelApi(`${API}/utilisateurs/${getUtilisateurId()}/progression/${serie.id}`);
      if (!progression || progression.episodesTotal === 0) progression = null;
    } catch { /* pas de progression */ }
    seriesEnMemoire.push({ serie, progression });
  }

  rendreMesSeries();
}

function trierEtFiltrerSeries() {
  const tri = document.getElementById("select-tri").value;
  const filtreGenre = document.getElementById("input-filtre-genre").value.trim().toLowerCase();

  let liste = [...seriesEnMemoire];
  if (filtreGenre) {
    liste = liste.filter((item) => (item.serie.genre || "").toLowerCase().includes(filtreGenre));
  }
  if (filtreStatut) {
    liste = liste.filter((item) => statutEffectif(item.serie, item.progression) === filtreStatut);
  }

  const comparateurs = {
    "titre-asc": (a, b) => a.serie.titre.localeCompare(b.serie.titre),
    "titre-desc": (a, b) => b.serie.titre.localeCompare(a.serie.titre),
    "annee-desc": (a, b) => (b.serie.anneeSortie || 0) - (a.serie.anneeSortie || 0),
    "annee-asc": (a, b) => (a.serie.anneeSortie || 0) - (b.serie.anneeSortie || 0),
    "note-desc": (a, b) => (b.serie.note || 0) - (a.serie.note || 0),
    "progression-desc": (a, b) => (b.progression?.pourcentage || 0) - (a.progression?.pourcentage || 0),
  };
  liste.sort(comparateurs[tri] || comparateurs["titre-asc"]);
  return liste;
}

function rendreMesSeries() {
  const conteneur = document.getElementById("liste-series");
  const liste = trierEtFiltrerSeries();

  if (liste.length === 0) {
    conteneur.innerHTML = messageVide("Aucune série ne correspond à ce filtre.");
    return;
  }

  conteneur.innerHTML = "";
  for (const { serie, progression } of liste) {
    const statut = statutEffectif(serie, progression);
    const statutForce = !!serie.statutVisionnage;
    const carte = creerElement(`
      <div class="serie-card carte-gestion" data-id="${serie.id}">
        <div class="poster-zone">
          <img src="${esc(serie.imageUrl || IMAGE_PLACEHOLDER)}" alt="Affiche de ${esc(serie.titre)}" loading="lazy">
          ${serie.notePersonnelle ? `<span class="badge-note" title="Ma note"><i class="bi bi-star-fill" aria-hidden="true"></i> ${esc(serie.notePersonnelle)}/10</span>` : ""}
        </div>
        <div class="contenu">
          <h3>${esc(serie.titre)}</h3>
          <span class="meta">${esc(serie.genre || "")} ${serie.anneeSortie ? "· " + esc(serie.anneeSortie) : ""}</span>
          <span class="badge-statut statut-${statut.toLowerCase()}" title="${statutForce ? "Statut choisi manuellement" : "Statut calculé automatiquement"}">
            ${LIBELLES_STATUT[statut] || statut}${statutForce ? "" : " (auto)"}
          </span>
          ${progression ? `
            <div class="barre-progression" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${progression.pourcentage}" aria-label="Progression">
              <div class="remplissage" style="width:${progression.pourcentage}%"></div>
            </div>
            <span class="meta">${progression.episodesVus}/${progression.episodesTotal} épisodes</span>
          ` : ""}
          <div class="statut-selecteur">
            <label for="statut-${serie.id}" class="sr-only">Changer le statut</label>
            <select class="select-statut-carte" data-id="${serie.id}" id="statut-${serie.id}">
              <option value="">Auto</option>
              <option value="A_VOIR" ${statut === "A_VOIR" && statutForce ? "selected" : ""}>À voir</option>
              <option value="EN_COURS" ${statut === "EN_COURS" && statutForce ? "selected" : ""}>En cours</option>
              <option value="TERMINEE" ${statut === "TERMINEE" && statutForce ? "selected" : ""}>Terminée</option>
              <option value="ABANDONNEE" ${statut === "ABANDONNEE" && statutForce ? "selected" : ""}>Abandonnée</option>
            </select>
          </div>
          <div class="actions-carte">
            <button type="button" class="btn-editer" data-id="${serie.id}" data-action="editer"><i class="bi bi-pencil" aria-hidden="true"></i> Éditer</button>
            <button type="button" class="btn-supprimer" data-id="${serie.id}" data-action="supprimer"><i class="bi bi-trash" aria-hidden="true"></i> Supprimer</button>
          </div>
        </div>
      </div>
    `);

    attacherPlaceholder(carte.querySelector("img"));
    carte.querySelector(".select-statut-carte").addEventListener("change", async (e) => {
      e.stopPropagation();
      const valeur = e.target.value;
      try {
        await appelApi(
          `${API}/utilisateurs/${getUtilisateurId()}/series/${serie.id}/statut${valeur ? `?statut=${valeur}` : ""}`,
          { method: "PATCH" }
        );
        chargerMesSeries();
      } catch (err) {
        afficherToast(err.message, "erreur");
        chargerMesSeries();
      }
    });

    carte.querySelector('[data-action="supprimer"]').addEventListener("click", async (e) => {
      e.stopPropagation();
      if (!confirm(`Supprimer "${serie.titre}" ?`)) return;
      try {
        await appelApi(`${API}/utilisateurs/${getUtilisateurId()}/series/${serie.id}`, { method: "DELETE" });
        chargerMesSeries();
      } catch (err) { afficherToast(err.message, "erreur"); }
    });

    carte.querySelector('[data-action="editer"]').addEventListener("click", (e) => {
      e.stopPropagation();
      ouvrirEditionSerie(serie);
    });

    carte.addEventListener("click", (e) => {
      if (e.target.closest(".statut-selecteur")) return;
      ouvrirDetailSerie(serie);
    });
    conteneur.appendChild(carte);
  }
}

document.getElementById("select-tri").addEventListener("change", rendreMesSeries);
document.getElementById("input-filtre-genre").addEventListener("input", rendreMesSeries);
document.getElementById("select-filtre-statut").addEventListener("change", (e) => {
  filtreStatut = e.target.value;
  rendreMesSeries();
});

document.getElementById("form-ajout-serie").addEventListener("submit", async (e) => {
  e.preventDefault();
  const titre = document.getElementById("input-titre").value;
  const genre = document.getElementById("input-genre").value;
  try {
    await appelApi(`${API}/utilisateurs/${getUtilisateurId()}/series`, {
      method: "POST",
      body: JSON.stringify({ titre, genre }),
    });
    e.target.reset();
    chargerMesSeries();
  } catch (err) { afficherToast(err.message, "erreur"); }
});

// --- Edition modal ---

function ouvrirEditionSerie(serie) {
  const contenu = document.getElementById("detail-contenu");
  contenu.innerHTML = `
    <h2 id="titre-detail">Éditer « ${esc(serie.titre)} »</h2>
    <form id="form-edition-serie" class="form-edition">
      <div class="champ champ-large">
        <label for="edit-titre">Titre</label>
        <input id="edit-titre" type="text" value="${esc(serie.titre || '')}" required>
      </div>
      <div class="champ">
        <label for="edit-genre">Genre</label>
        <input id="edit-genre" type="text" value="${esc(serie.genre || '')}" placeholder="Drame, Comédie…">
      </div>
      <div class="champ">
        <label for="edit-annee">Année de sortie</label>
        <input id="edit-annee" type="number" value="${serie.anneeSortie || ''}" placeholder="ex. 2024" min="1900" max="2100">
      </div>
      <div class="champ">
        <label for="edit-note">Note du public (0–10)</label>
        <input id="edit-note" type="number" value="${serie.note ?? ''}" placeholder="ex. 8.2" min="0" max="10" step="0.1">
      </div>
      <div class="champ">
        <label for="edit-statut">Statut de visionnage</label>
        <select id="edit-statut">
          <option value="">Auto (calculé)</option>
          <option value="A_VOIR" ${serie.statutVisionnage === "A_VOIR" ? "selected" : ""}>À voir</option>
          <option value="EN_COURS" ${serie.statutVisionnage === "EN_COURS" ? "selected" : ""}>En cours</option>
          <option value="TERMINEE" ${serie.statutVisionnage === "TERMINEE" ? "selected" : ""}>Terminée</option>
          <option value="ABANDONNEE" ${serie.statutVisionnage === "ABANDONNEE" ? "selected" : ""}>Abandonnée</option>
        </select>
      </div>
      <div class="champ champ-large">
        <label for="edit-image">URL de l'affiche</label>
        <input id="edit-image" type="url" value="${esc(serie.imageUrl || '')}" placeholder="https://…">
      </div>
      <div class="champ">
        <label for="edit-note-perso">Ma note (1–10)</label>
        <input id="edit-note-perso" type="number" min="1" max="10" step="1" value="${serie.notePersonnelle ?? ''}" placeholder="ex. 9">
      </div>
      <div class="champ champ-large">
        <label for="edit-critique">Ma critique</label>
        <textarea id="edit-critique" maxlength="500" rows="3" placeholder="500 caractères maximum">${esc(serie.critique || '')}</textarea>
      </div>
      <div class="form-actions">
        <button type="submit" class="btn-primaire">Enregistrer</button>
      </div>
    </form>
  `;
  ouvrirModale();

  document.getElementById("form-edition-serie").addEventListener("submit", async (e) => {
    e.preventDefault();
        const body = {
      titre: document.getElementById("edit-titre").value,
      genre: document.getElementById("edit-genre").value || null,
      anneeSortie: document.getElementById("edit-annee").value || null,
      note: document.getElementById("edit-note").value || null,
      imageUrl: document.getElementById("edit-image").value || null,
      statutVisionnage: document.getElementById("edit-statut").value || null,
      notePersonnelle: document.getElementById("edit-note-perso").value
        ? parseInt(document.getElementById("edit-note-perso").value, 10)
        : null,
      critique: document.getElementById("edit-critique").value.trim() || null,
    };
    try {
      await appelApi(`${API}/utilisateurs/${getUtilisateurId()}/series/${serie.id}`, {
        method: "PUT",
        body: JSON.stringify(body),
      });
      fermerModale();
      chargerMesSeries();
    } catch (err) { afficherToast(err.message, "erreur"); }
  });
}

// --- Detail panel (accessible modal) ---

let elementDeclencheur = null;

function ouvrirModale() {
  const panneau = document.getElementById("panneau-detail");
  elementDeclencheur = document.activeElement;
  panneau.classList.remove("hidden");
  panneau.removeAttribute("inert");
  document.body.style.overflow = "hidden";
  const premierFocusable = panneau.querySelector("button, [href], input, select, textarea, [tabindex]");
  premierFocusable?.focus();
  document.addEventListener("keydown", gererClavierModale);
}

function fermerModale() {
  const panneau = document.getElementById("panneau-detail");
  panneau.classList.add("hidden");
  panneau.setAttribute("inert", "");
  document.body.style.overflow = "";
  document.removeEventListener("keydown", gererClavierModale);
  elementDeclencheur?.focus();
}

function gererClavierModale(e) {
  const panneau = document.getElementById("panneau-detail");
  if (e.key === "Escape") { fermerModale(); return; }
  if (e.key === "Tab") {
    const focusables = panneau.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );
    if (focusables.length === 0) return;
    const premier = focusables[0];
    const dernier = focusables[focusables.length - 1];
    if (e.shiftKey && document.activeElement === premier) { e.preventDefault(); dernier.focus(); }
    else if (!e.shiftKey && document.activeElement === dernier) { e.preventDefault(); premier.focus(); }
  }
}

document.getElementById("panneau-detail").addEventListener("click", (e) => {
  if (e.target.id === "panneau-detail") fermerModale();
});

// --- Formattage dates / infos saisons ---

/** Formate une date ISO (YYYY-MM-DD) en français. Retourne null si invalide. */
function formatDate(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  if (isNaN(d)) return null;
  return d.toLocaleDateString("fr-FR", { day: "2-digit", month: "long", year: "numeric" });
}

/**
 * Construit la ligne d'infos "saisons + épisodes + statut" d'une série TMDB.
 * Gère les cas : terminée, en cours, annulée, inconnue.
 */
function construireInfosSerie(detail) {
  const parties = [];

  // Saisons / épisodes
  if (detail.nombreSaisons) {
    parties.push(`${detail.nombreSaisons} saison${detail.nombreSaisons > 1 ? "s" : ""}`);
  }
  if (detail.nombreEpisodes) {
    parties.push(`${detail.nombreEpisodes} épisode${detail.nombreEpisodes > 1 ? "s" : ""}`);
  }

  const ligneSaisons = parties.join(" · ");

  // Statut + date du dernier épisode
  const dateDebut = formatDate(detail.dateDiffusion);
  const dateFin = formatDate(detail.dateDernierEpisode);
  const statut = (detail.statut || "").toLowerCase();

  let ligneStatut = "";
  if (statut === "ended") {
    ligneStatut = dateFin
      ? `🟢 Terminée — dernier épisode le ${dateFin}`
      : "🟢 Terminée";
  } else if (statut === "running" || statut === "returning series") {
    if (dateFin) {
      // La dernière diffusion peut être dans le futur pour un prochain épisode
      const estFutur = new Date(detail.dateDernierEpisode) > new Date();
      ligneStatut = estFutur
        ? `🟡 En cours — prochain épisode le ${dateFin}`
        : `🟡 En cours — dernier épisode le ${dateFin}`;
    } else {
      ligneStatut = "🟡 En cours de diffusion";
    }
  } else if (statut === "canceled") {
    ligneStatut = dateFin
      ? `🔴 Annulée — dernier épisode le ${dateFin}`
      : "🔴 Annulée";
  } else if (statut === "in production") {
    ligneStatut = "🟠 En production";
  } else {
    ligneStatut = dateFin ? `Dernier épisode le ${dateFin}` : "";
  }

  return {
    ligneSaisons,
    ligneStatut,
    dateDebut,
  };
}

async function chargerFournisseurs(serie, tmdbId) {
  const conteneur = document.getElementById("fournisseurs-detail");
  if (!conteneur) return;

  if (!tmdbId) {
    conteneur.innerHTML = messageVide("Aucune plateforme connue pour cette série (ajoutée manuellement).");
    return;
  }

  conteneur.innerHTML = messageChargement("Recherche des plateformes...");

  let parPays;
  try {
    parPays = await appelApi(`${API}/tmdb/serie/${tmdbId}/fournisseurs`);
  } catch (err) {
    conteneur.innerHTML = messageErreur(err.message);
    return;
  }

  const pays = parPays.FR ? "FR" : Object.keys(parPays)[0];
  const fournisseurs = pays ? parPays[pays] : null;

  if (!fournisseurs || fournisseurs.length === 0) {
    conteneur.innerHTML = messageVide("Aucune plateforme de streaming référencée pour cette série.");
    return;
  }

  const libelles = { FLATRATE: "Abonnement", RENT: "Location", BUY: "Achat" };

  conteneur.innerHTML = `
    <div class="fournisseurs-liste">
      ${fournisseurs.map((f) => `
        <a class="fournisseur" href="${f.lien}" target="_blank" rel="noopener noreferrer"
           title="${esc(f.nom)} — ${libelles[f.type] || f.type}">
          ${f.logoUrl
            ? `<img src="${esc(f.logoUrl)}" alt="${esc(f.nom)}">`
            : `<span class="fournisseur-nom">${esc(f.nom)}</span>`}
          <span class="fournisseur-type">${libelles[f.type] || f.type}</span>
        </a>
      `).join("")}
    </div>
    <p class="fournisseurs-source">Données TMDB · <a href="https://www.themoviedb.org" target="_blank" rel="noopener">themoviedb.org</a></p>
  `;
}

async function chargerVideos(serie, tmdbId) {
  const conteneur = document.getElementById("videos-detail");
  if (!conteneur) return;

  if (!tmdbId) {
    conteneur.innerHTML = `<p class="aucun-resultat" role="status">Aucune bande-annonce disponible pour cette série ajoutée manuellement.</p>`;
    return;
  }

  conteneur.innerHTML = messageChargement("Recherche de la bande-annonce...");

  let videos;
  try {
    videos = await appelApi(`${API}/tmdb/serie/${tmdbId}/videos`);
  } catch (err) {
    conteneur.innerHTML = messageErreur(err.message);
    return;
  }

  if (!Array.isArray(videos) || videos.length === 0) {
    const recherche = encodeURIComponent(`${serie.titre} bande annonce VF`);
    conteneur.innerHTML = `
      <p class="aucun-resultat" role="status">
        TMDB ne référence pas de bande-annonce officielle pour cette série.
      </p>
      <p style="text-align:center;margin-top:0.5rem">
        <a class="btn-youtube-externe"
           href="https://www.youtube.com/results?search_query=${recherche}"
           target="_blank" rel="noopener">
          🔍 Chercher sur YouTube
        </a>
      </p>
    `;
    return;
  }

  const principale = videos[0];
  const autres = videos.slice(1, 4);

  conteneur.innerHTML = `
    <div class="video-principale">
      <button type="button" class="video-apercu" data-video-key="${principale.cle}" data-video-embed="${principale.embedUrl}">
        <img src="https://img.youtube.com/vi/${principale.cle}/hqdefault.jpg" alt="Aperçu : ${esc(principale.nom)}">
        <span class="play-overlay" aria-hidden="true">▶</span>
        <span class="video-titre">${esc(principale.nom)}</span>
      </button>
    </div>
    ${autres.length > 0 ? `
      <div class="videos-alternatives">
        <span class="meta">Autres vidéos :</span>
        ${autres.map(v => `
          <button type="button" class="video-miniature" data-video-key="${v.cle}" data-video-embed="${v.embedUrl}" title="${esc(v.nom)}">
            ${v.type}
          </button>
        `).join("")}
      </div>
    ` : ""}
  `;

  conteneur.querySelectorAll("[data-video-embed]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const embedUrl = btn.dataset.videoEmbed;
      const key = btn.dataset.videoKey;
      conteneur.innerHTML = `
        <div class="video-embed">
          <iframe
            src="${embedUrl}?autoplay=1&rel=0"
            title="Bande-annonce"
            loading="lazy"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            allowfullscreen>
          </iframe>
          <p class="video-source">
            <a href="https://www.youtube.com/watch?v=${key}" target="_blank" rel="noopener">Ouvrir sur YouTube ↗</a>
          </p>
        </div>
      `;
    });
  });
}

async function chargerAvis(serie) {
  const conteneur = document.getElementById("avis-detail");
  if (!conteneur) return;

  function rendre() {
    conteneur.innerHTML = `
      <div class="avis-form">
        <label for="avis-note">Ma note (1 à 10) :</label>
        <input type="number" id="avis-note" min="1" max="10" step="1"
               value="${serie.notePersonnelle ?? ""}" placeholder="—">
        <span class="avis-etoiles">${afficherEtoiles(serie.notePersonnelle)}</span>

        <label for="avis-critique">Ma critique :</label>
        <textarea id="avis-critique" maxlength="500" rows="3"
                  placeholder="Qu'as-tu pensé de cette série ? (500 caractères max)">${serie.critique || ""}</textarea>
        <small class="compteur" id="avis-compteur">${(serie.critique || "").length}/500</small>

        <div class="avis-actions">
          <button type="button" id="btn-avis-save">Enregistrer mon avis</button>
          ${serie.notePersonnelle || serie.critique ? `
            <button type="button" id="btn-avis-clear" class="btn-effacer">Effacer</button>
          ` : ""}
        </div>
        <p id="avis-message" class="avis-message"></p>
      </div>
    `;

    const noteInput = document.getElementById("avis-note");
    const critiqueInput = document.getElementById("avis-critique");
    const compteur = document.getElementById("avis-compteur");
    const message = document.getElementById("avis-message");

    noteInput?.addEventListener("input", () => {
      const v = parseInt(noteInput.value, 10);
      conteneur.querySelector(".avis-etoiles").textContent =
        (v >= 1 && v <= 10) ? afficherEtoiles(v) : "";
    });

    critiqueInput?.addEventListener("input", () => {
      compteur.textContent = `${critiqueInput.value.length}/500`;
    });

    document.getElementById("btn-avis-save").addEventListener("click", async () => {
      const note = noteInput.value ? parseInt(noteInput.value, 10) : null;
      const critique = critiqueInput.value.trim() || null;

      if (note !== null && (note < 1 || note > 10)) {
        message.textContent = "La note doit être entre 1 et 10.";
        message.className = "avis-message erreur";
        return;
      }

      try {
        const updated = await appelApi(
          `${API}/utilisateurs/${getUtilisateurId()}/series/${serie.id}/avis`,
          {
            method: "PATCH",
            body: JSON.stringify({ notePersonnelle: note, critique }),
          }
        );
        serie.notePersonnelle = updated.notePersonnelle;
        serie.critique = updated.critique;
        message.textContent = "Avis enregistré ✓";
        message.className = "avis-message succes";
        setTimeout(() => rendre(), 800);
        chargerMesSeries();
      } catch (err) {
        message.textContent = err.message;
        message.className = "avis-message erreur";
      }
    });

    document.getElementById("btn-avis-clear")?.addEventListener("click", async () => {
      if (!confirm("Effacer ta note et ta critique ?")) return;
      try {
        await appelApi(
          `${API}/utilisateurs/${getUtilisateurId()}/series/${serie.id}/avis`,
          {
            method: "PATCH",
            body: JSON.stringify({ notePersonnelle: null, critique: null }),
          }
        );
        serie.notePersonnelle = null;
        serie.critique = null;
        rendre();
        chargerMesSeries();
      } catch (err) {
        afficherToast(err.message, "erreur");
      }
    });
  }

  rendre();
}

async function ouvrirDetailSerie(serie) {
  const contenu = document.getElementById("detail-contenu");
  contenu.innerHTML = `<h2 id="titre-detail">${esc(serie.titre)}</h2>` + messageChargement();
  ouvrirModale();

  let saisons;
  try {
    saisons = await appelApi(`${API}/utilisateurs/${getUtilisateurId()}/series/${serie.id}/saisons`);
  } catch (err) {
    contenu.innerHTML = `<h2 id="titre-detail">${esc(serie.titre)}</h2>` + messageErreur(err.message);
    return;
  }

  if (!Array.isArray(saisons) || saisons.length === 0) {
    contenu.innerHTML = `<h2 id="titre-detail">${esc(serie.titre)}</h2>` + messageVide("Aucune saison enregistrée pour cette série.");
    return;
  }
    let html = `<h2 id="titre-detail">${esc(serie.titre)}</h2>
    <section class="bloc-avis">
      <h3>Mon avis</h3>
      <div id="avis-detail" aria-live="polite"></div>
    </section>
    <section class="bloc-video">
      <h3>Bande-annonce</h3>
      <div id="videos-detail" aria-live="polite"></div>
    </section>
    <section class="bloc-fournisseurs">
      <h3>Où regarder ?</h3>
      <div id="fournisseurs-detail" aria-live="polite"></div>
    </section>`;
  for (const saison of saisons) {
    let episodes = [];
    try {
      episodes = await appelApi(`${API}/saisons/${saison.id}/episodes`);
    } catch { /* leave empty */ }

    const nbEpisodes = Array.isArray(episodes) ? episodes.length : 0;
    html += `<section class="saison"><h3 class="saison-titre">Saison ${esc(saison.numero)} <span class="saison-compte">${nbEpisodes} épisode${nbEpisodes > 1 ? "s" : ""}</span></h3>`;
    if (nbEpisodes === 0) {
      html += messageVide("Aucun épisode enregistré pour cette saison.") + "</section>";
      continue;
    }
    for (const ep of episodes) {
      html += `
        <label class="episode-ligne" data-episode-id="${ep.id}" for="ep-${ep.id}">
          <span class="ep-num"><span class="sr-only">Épisode </span>${esc(ep.numero)}</span>
          <span class="ep-titre">${esc(ep.titre || "Sans titre")}</span>
          <span class="ep-duree">${ep.dureeMinutes ? esc(ep.dureeMinutes) + " min" : ""}</span>
          <input type="checkbox" class="check-vu" data-episode-id="${ep.id}" id="ep-${ep.id}">
        </label>`;
    }
    html += "</section>";
  }
  contenu.innerHTML = html;

   chargerAvis(serie);
  chargerFournisseurs(serie, serie.tmdbId);
  chargerVideos(serie, serie.tmdbId);

  contenu.querySelectorAll(".check-vu").forEach((checkbox) => {
    checkbox.addEventListener("change", async (e) => {
      const episodeId = e.target.dataset.episodeId;
      const ligne = e.target.closest(".episode-ligne");
      try {
        if (e.target.checked) {
          await appelApi(`${API}/utilisateurs/${getUtilisateurId()}/visionnages/${episodeId}`, { method: "POST" });
          ligne.classList.add("vu");
        } else {
          await appelApi(`${API}/utilisateurs/${getUtilisateurId()}/visionnages/${episodeId}`, { method: "DELETE" });
          ligne.classList.remove("vu");
        }
      } catch (err) {
        afficherToast(err.message, "erreur");
        e.target.checked = !e.target.checked;
      }
    });
  });
}

document.getElementById("btn-fermer-panneau").addEventListener("click", () => {
  fermerModale();
  chargerMesSeries();
});

// --- TMDB discovery (avec pagination) ---

let requeteTmdbCourante = null;

function afficherPagination(page, totalPages) {
  const nav = document.getElementById("pagination-tmdb");
  if (!totalPages || totalPages <= 1) {
    nav.classList.add("hidden");
    return;
  }
  nav.classList.remove("hidden");
  document.getElementById("info-page").textContent = `Page ${page} / ${totalPages}`;
  document.getElementById("btn-page-prec").disabled = page <= 1;
  document.getElementById("btn-page-suiv").disabled = page >= totalPages;
}

function cacherPagination() {
  document.getElementById("pagination-tmdb").classList.add("hidden");
}

function afficherResultatsTmdb(series, conteneurId = "resultats-tmdb", options = {}) {
  const conteneur = document.getElementById(conteneurId);
  const { avecImport = true } = options;

  if (!Array.isArray(series) || series.length === 0) {
    conteneur.innerHTML = messageVide("Aucun résultat pour cette recherche.");
    return;
  }

  conteneur.innerHTML = "";
  for (const serie of series) {
    const annee = serie.dateDiffusion ? String(serie.dateDiffusion).slice(0, 4) : "Date inconnue";
    const note = typeof serie.note === "number" && serie.note > 0 ? serie.note.toFixed(1) : null;
    const carte = creerElement(`
      <article class="serie-card poster" tabindex="0" aria-label="${esc(serie.titre)}">
        <img src="${esc(serie.imageUrl || IMAGE_PLACEHOLDER)}" alt="Affiche de ${esc(serie.titre)}" loading="lazy">
        ${note ? `<span class="badge-note" title="Note TMDB"><i class="bi bi-star-fill" aria-hidden="true"></i> ${note}</span>` : ""}
        <div class="poster-overlay">
          <h3>${esc(serie.titre)}</h3>
          <span class="poster-meta">${esc(annee)}</span>
          ${avecImport ? htmlBoutonImport(serie.tmdbId) : ""}
        </div>
      </article>
    `);
    attacherPlaceholder(carte.querySelector("img"));

    if (avecImport) brancherBoutonImport(carte.querySelector(".btn-importer"), serie.titre);

    carte.addEventListener("click", (e) => {
      if (e.target.closest(".btn-importer")) return;
      ouvrirDetailTmdb(serie);
    });
    carte.addEventListener("keydown", (e) => {
      if (e.target !== carte || (e.key !== "Enter" && e.key !== " ")) return;
      e.preventDefault();
      ouvrirDetailTmdb(serie);
    });

    conteneur.appendChild(carte);
  }
}

async function ouvrirDetailTmdb(serieTmdb) {
  const contenu = document.getElementById("detail-contenu");
  contenu.innerHTML = `<h2 id="titre-detail">${esc(serieTmdb.titre)}</h2>` + messageChargement();
  ouvrirModale();

  // 1. Charger les détails et le casting EN PARALLÈLE
  let detail, credits;
  try {
    [detail, credits] = await Promise.all([
      appelApi(`${API}/tmdb/serie/${serieTmdb.tmdbId}`),
      appelApi(`${API}/tmdb/serie/${serieTmdb.tmdbId}/credits`),
    ]);
  } catch (err) {
    contenu.innerHTML = `<h2 id="titre-detail">${esc(serieTmdb.titre)}</h2>` + messageErreur(err.message);
    return;
  }

  const imageUrl = detail.imageUrl || IMAGE_PLACEHOLDER;
  const description = detail.description && detail.description.trim()
    ? detail.description
    : "Aucune description disponible pour cette série.";

  // Infos saisons / épisodes / dates
  const { ligneSaisons, ligneStatut, dateDebut } = construireInfosSerie(detail);

  const casting = Array.isArray(credits) ? credits.slice(0, 8) : [];

  contenu.innerHTML = `
    <h2 id="titre-detail">${esc(detail.titre)}</h2>

    <div class="tmdb-fiche">
      <img src="${esc(imageUrl)}" alt="Affiche de ${esc(detail.titre)}" class="tmdb-fiche-affiche">
      <div class="tmdb-fiche-meta">
        <ul class="chips">
          ${detail.note
            ? `<li class="chip chip-note"><i class="bi bi-star-fill" aria-hidden="true"></i> ${detail.note.toFixed(1)} <span class="chip-sub">/ 10</span></li>`
            : `<li class="chip">Pas encore noté</li>`}
          ${dateDebut ? `<li class="chip"><i class="bi bi-calendar-event" aria-hidden="true"></i><span class="sr-only">Première diffusion : </span> ${dateDebut}</li>` : ""}
          ${ligneSaisons ? `<li class="chip"><i class="bi bi-collection-play" aria-hidden="true"></i> ${ligneSaisons}</li>` : ""}
        </ul>
        ${ligneStatut ? `<p class="tmdb-fiche-statut">${ligneStatut}</p>` : ""}
        <p class="tmdb-fiche-desc">${esc(description)}</p>
        <div class="tmdb-fiche-actions">
          ${htmlBoutonImport(detail.tmdbId, "Importer dans ma liste", "btn-importer-modal")}
          <a class="btn-tmdb-externe"
             href="https://www.themoviedb.org/tv/${detail.tmdbId}"
             target="_blank" rel="noopener noreferrer">
            Voir sur TMDB <i class="bi bi-box-arrow-up-right" aria-hidden="true"></i><span class="sr-only">(nouvel onglet)</span>
          </a>
        </div>
      </div>
    </div>

    ${casting.length > 0 ? `
      <section class="bloc-casting">
        <h3>Casting principal</h3>
        <div class="casting-liste">
          ${casting.map((m) => `
            <div class="casting-item">
              <img src="${esc(m.photoUrl || IMAGE_PLACEHOLDER)}" alt="${esc(m.nom)}">
              <div class="casting-nom">${esc(m.nom)}</div>
              <div class="casting-role">${esc(m.personnage || "")}</div>
            </div>
          `).join("")}
        </div>
      </section>
    ` : ""}
  `;

  contenu.querySelectorAll("img").forEach(attacherPlaceholder);

  brancherBoutonImport(document.getElementById("btn-importer-modal"), detail.titre);
}

function construireUrlTmdb(requete, page) {
  const p = new URLSearchParams(requete.params || {});
  p.set("page", page);

  switch (requete.type) {
    case "categorie": return `${API}/tmdb/${requete.categorie}?${p.toString()}`;
    case "recherche": return `${API}/tmdb/recherche?${p.toString()}`;
    case "decouvrir": return `${API}/tmdb/decouvrir?${p.toString()}`;
  }
  return null;
}

async function chargerTmdb(url, requete) {
  const conteneur = document.getElementById("resultats-tmdb");
  conteneur.innerHTML = squelettes(10);
  conteneur.setAttribute("aria-busy", "true");
  cacherPagination();

  let reponse;
  try {
    reponse = await appelApi(url);
  } catch (err) {
    conteneur.innerHTML = messageErreur(err.message);
    return;
  } finally {
    conteneur.setAttribute("aria-busy", "false");
  }

  if (Array.isArray(reponse)) {
    afficherResultatsTmdb(reponse);
    return;
  }

  requeteTmdbCourante = { ...requete, page: reponse.page };
  afficherResultatsTmdb(reponse.resultats);
  afficherPagination(reponse.page, reponse.totalPages);
}

const ROUTES_TMDB = {
  populaires: "populaires",
  tendances: "tendances",
  "mieux-notees": "mieux-notees",
  "diffusees-bientot": "diffusees-bientot",
};

const TITRES_CATEGORIES = {
  populaires: "Séries populaires",
  tendances: "Séries tendances",
  "mieux-notees": "Séries les mieux notées",
  "diffusees-bientot": "Bientôt à l'écran",
};

function definirTitreResultats(texte) {
  document.getElementById("titre-resultats").textContent = texte;
}

function desactiverCategories() {
  document.querySelectorAll(".souscat-btn").forEach((b) => {
    b.classList.remove("active");
    b.setAttribute("aria-pressed", "false");
  });
}

function viderActeurs() {
  const zone = document.getElementById("resultats-acteurs");
  zone.innerHTML = "";
  zone.classList.add("grid");
}

async function chargerCategorieTmdb(cat, bouton) {
  desactiverCategories();
  if (bouton) {
    bouton.classList.add("active");
    bouton.setAttribute("aria-pressed", "true");
  }
  viderActeurs();
  definirTitreResultats(TITRES_CATEGORIES[cat] || "");

  const requete = { type: "categorie", categorie: cat, params: {}, page: 1 };
  await chargerTmdb(construireUrlTmdb(requete, 1), requete);
}

document.querySelectorAll(".souscat-btn").forEach((btn) => {
  btn.addEventListener("click", () => chargerCategorieTmdb(btn.dataset.cat, btn));
});

// --- Unified hero search (series or actors) ---

let modeRecherche = "series";

function definirModeRecherche(mode) {
  modeRecherche = mode;
  document.querySelectorAll(".mode-btn").forEach((b) => {
    const actif = b.dataset.mode === mode;
    b.classList.toggle("active", actif);
    b.setAttribute("aria-pressed", String(actif));
  });
  document.getElementById("input-recherche").placeholder =
    mode === "acteurs" ? "Rechercher un acteur..." : "Rechercher une série...";
}

document.querySelectorAll(".mode-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    definirModeRecherche(btn.dataset.mode);
    document.getElementById("input-recherche").focus();
  });
});

async function rechercherSeries(titre) {
  desactiverCategories();
  viderActeurs();
  definirTitreResultats(`Résultats pour « ${titre} »`);
  const requete = { type: "recherche", params: { titre }, page: 1 };
  await chargerTmdb(construireUrlTmdb(requete, 1), requete);
}

document.getElementById("form-recherche-tmdb").addEventListener("submit", async (e) => {
  e.preventDefault();
  const terme = document.getElementById("input-recherche").value.trim();
  if (!terme) return;
  if (modeRecherche === "acteurs") await rechercherActeurs(terme);
  else await rechercherSeries(terme);
});

// --- Advanced filters ---

// Collapse the filter panel by default on small screens
if (window.matchMedia("(max-width: 720px)").matches) {
  document.querySelector(".filtres")?.removeAttribute("open");
}

document.getElementById("form-decouvrir").addEventListener("submit", async (e) => {
  e.preventDefault();
  const genre = document.getElementById("select-genre").value;
  const annee = document.getElementById("input-annee").value;
  const noteMin = document.getElementById("input-note-min").value;

  const params = {};
  if (genre) params.genre = genre;
  if (annee) params.annee = annee;
  if (noteMin) params.noteMin = noteMin;

  desactiverCategories();
  viderActeurs();
  definirTitreResultats("Séries filtrées");
  const requete = { type: "decouvrir", params, page: 1 };
  await chargerTmdb(construireUrlTmdb(requete, 1), requete);
});

document.getElementById("btn-reset-filtres").addEventListener("click", () => {
  document.getElementById("form-decouvrir").reset();
  chargerCategorieTmdb("populaires", document.querySelector(".souscat-btn[data-cat='populaires']"));
});

document.getElementById("btn-page-prec").addEventListener("click", () => {
  if (!requeteTmdbCourante || requeteTmdbCourante.page <= 1) return;
  const page = requeteTmdbCourante.page - 1;
  chargerTmdb(construireUrlTmdb(requeteTmdbCourante, page), requeteTmdbCourante);
});

document.getElementById("btn-page-suiv").addEventListener("click", () => {
  if (!requeteTmdbCourante) return;
  const page = requeteTmdbCourante.page + 1;
  chargerTmdb(construireUrlTmdb(requeteTmdbCourante, page), requeteTmdbCourante);
});

// --- Genres ---

async function chargerGenres() {
  const select = document.getElementById("select-genre");
  select.innerHTML = '<option value="">Tous les genres</option>';

  try {
    const genres = await appelApi(`${API}/tmdb/genres`);
    if (!Array.isArray(genres)) return;
    for (const genre of genres) {
      const option = document.createElement("option");
      option.value = genre.id;
      option.textContent = genre.nom;
      select.appendChild(option);
    }
  } catch { /* TMDB unreachable */ }
}

// --- Actor search + filmography ---

let acteursEnMemoire = [];

async function rechercherActeurs(nom) {
  if (!nom) return;

  const conteneur = document.getElementById("resultats-acteurs");
  conteneur.classList.add("grid");
  conteneur.innerHTML = messageChargement();
  definirTitreResultats("");

  let acteurs;
  try {
    acteurs = await appelApi(`${API}/tmdb/recherche-acteur?nom=${encodeURIComponent(nom)}`);
  } catch (err) {
    conteneur.innerHTML = messageErreur(err.message);
    return;
  }

  acteursEnMemoire = Array.isArray(acteurs) ? acteurs : [];

  if (acteursEnMemoire.length === 0) {
    conteneur.innerHTML = messageVide("Aucun acteur trouvé pour cette recherche.");
    return;
  }

  rendreActeurs();
}

function rendreActeurs() {
  const conteneur = document.getElementById("resultats-acteurs");
  conteneur.classList.add("grid");
  conteneur.innerHTML = "";

  for (const acteur of acteursEnMemoire) {
    const carte = creerElement(`
      <div class="serie-card acteur-card" data-acteur-id="${acteur.id}">
        <img src="${esc(acteur.photoUrl || IMAGE_PLACEHOLDER)}" alt="Photo de ${esc(acteur.nom)}">
        <div class="contenu"><h3>${esc(acteur.nom)}</h3></div>
      </div>
    `);
    attacherPlaceholder(carte.querySelector("img"));
    carte.addEventListener("click", () => afficherFilmographie(acteur));
    conteneur.appendChild(carte);
  }
}

async function afficherFilmographie(acteur) {
  const conteneur = document.getElementById("resultats-acteurs");
  conteneur.innerHTML = messageChargement(`Chargement de la filmographie de ${esc(acteur.nom)}...`);

  let series;
  try {
    series = await appelApi(`${API}/tmdb/acteur/${acteur.id}/series`);
  } catch (err) {
    conteneur.innerHTML = messageErreur(err.message);
    return;
  }

  conteneur.classList.remove("grid");
  conteneur.innerHTML = `
    <button type="button" class="btn-retour" id="btn-retour-acteurs">← Retour aux acteurs</button>
    <h3 class="titre-section">Séries avec ${esc(acteur.nom)}</h3>
    <div class="grid" id="resultats-filmographie"></div>
  `;
  document.getElementById("btn-retour-acteurs").addEventListener("click", () => {
    conteneur.classList.add("grid");
    rendreActeurs();
  });

  afficherResultatsTmdb(series, "resultats-filmographie", { avecImport: true });
}

// --- ADMIN ---

function afficherOngletAdminSiBesoin() {
  const badge = document.getElementById("badge-role");
  const elementsAdmin = document.querySelectorAll(".admin-only");
  const estAdmin = getRole() === "ADMIN";
  badge.textContent = estAdmin ? "ADMIN" : "";
  badge.classList.toggle("badge-admin", estAdmin);
  elementsAdmin.forEach((el) => el.classList.toggle("hidden", !estAdmin));
}

async function chargerStatsAdmin() {
  const conteneur = document.getElementById("admin-stats");
  conteneur.innerHTML = messageChargement();
  try {
    const s = await appelApi(`${API}/admin/utilisateurs/statistiques`);
    const total = s.totalUtilisateurs || 0;
    const pct = (n) => (total ? Math.round((n / total) * 100) : 0);
    const segments = [
      { nom: "Approuvés", n: s.approuves, couleur: "var(--c-vu)" },
      { nom: "En attente", n: s.enAttente, couleur: "var(--c-avoir)" },
      { nom: "Refusés", n: s.refuses, couleur: "var(--c-abandon)" },
    ];
    const resume = segments.map((x) => `${x.nom} ${x.n}`).join(", ");

    conteneur.innerHTML = `
      ${s.enAttente > 0 ? `
        <div class="alerte" role="status">
          <i class="bi bi-hourglass-split" aria-hidden="true"></i>
          <span><strong>${s.enAttente}</strong> compte${s.enAttente > 1 ? "s" : ""} en attente d'approbation</span>
          <button type="button" id="btn-voir-attente" class="btn-secondaire">Voir</button>
        </div>` : ""}
      <div class="kpi-grid">
        <div class="kpi"><i class="bi bi-people" aria-hidden="true"></i><div><span class="kpi-valeur">${s.totalUtilisateurs}</span><span class="kpi-label">Utilisateurs</span></div></div>
        <div class="kpi kpi-vu"><i class="bi bi-patch-check" aria-hidden="true"></i><div><span class="kpi-valeur">${s.approuves}</span><span class="kpi-label">Approuvés</span></div></div>
        <div class="kpi kpi-avoir"><i class="bi bi-hourglass-split" aria-hidden="true"></i><div><span class="kpi-valeur">${s.enAttente}</span><span class="kpi-label">En attente</span></div></div>
        <div class="kpi kpi-abandon"><i class="bi bi-slash-circle" aria-hidden="true"></i><div><span class="kpi-valeur">${s.refuses}</span><span class="kpi-label">Refusés</span></div></div>
        <div class="kpi kpi-admin"><i class="bi bi-shield-check" aria-hidden="true"></i><div><span class="kpi-valeur">${s.admins}</span><span class="kpi-label">Administrateurs</span></div></div>
      </div>
      <section class="box">
        <h3 class="box-titre"><i class="bi bi-pie-chart" aria-hidden="true"></i> Répartition des comptes</h3>
        <div class="bar-stack" role="img" aria-label="${esc(resume)}">
          ${segments.filter((x) => x.n > 0).map((x) => `<span style="width:${(x.n / total) * 100}%;background:${x.couleur}"></span>`).join("")}
        </div>
        <ul class="legende">
          ${segments.map((x) => `<li><i style="background:${x.couleur}"></i>${x.nom}<strong>${x.n}</strong><span class="meta">${pct(x.n)} %</span></li>`).join("")}
        </ul>
      </section>`;

    document.getElementById("btn-voir-attente")?.addEventListener("click", () => {
      document.getElementById("select-statut-admin").value = "EN_ATTENTE";
      chargerListeAdmin();
    });
  } catch (err) {
    conteneur.innerHTML = messageErreur(esc(err.message));
  }
}

async function chargerListeAdmin() {
  const conteneur = document.getElementById("admin-liste");
  const statut = document.getElementById("select-statut-admin").value;
  conteneur.innerHTML = messageChargement();

  const url = statut
    ? `${API}/admin/utilisateurs?statut=${statut}`
    : `${API}/admin/utilisateurs`;

  let utilisateurs;
  try {
    utilisateurs = await appelApi(url);
  } catch (err) {
    conteneur.innerHTML = messageErreur(err.message);
    return;
  }

  if (!Array.isArray(utilisateurs) || utilisateurs.length === 0) {
    conteneur.innerHTML = messageVide("Aucun utilisateur à afficher.");
    return;
  }

  const libellesStatut = { EN_ATTENTE: "En attente", APPROUVE: "Approuvé", REFUSE: "Refusé" };
  const nbAdminsActifs = utilisateurs.filter((x) => x.role === "ADMIN" && x.statut === "APPROUVE").length;

  conteneur.innerHTML = "";
  for (const u of utilisateurs) {
    const dernierAdmin = u.role === "ADMIN" && nbAdminsActifs <= 1;
    const carte = creerElement(`
      <article class="user-row">
        <span class="user-avatar lg" aria-hidden="true">${esc((u.pseudo || "?").charAt(0))}</span>
        <div class="user-main">
          <h3>${esc(u.pseudo)} <span class="badge-role-inline badge-${esc(u.role.toLowerCase())}">${esc(u.role)}</span></h3>
          <p class="meta">${esc(u.email)}</p>
        </div>
        <div class="user-meta">
          <span class="pill pill-${esc(u.statut.toLowerCase())}">${esc(libellesStatut[u.statut] || u.statut)}</span>
          <span class="meta">Inscrit le ${u.dateInscription ? new Date(u.dateInscription).toLocaleDateString("fr-FR") : "—"}</span>
        </div>
        <div class="admin-actions">
          ${u.statut !== "APPROUVE" ? `<button type="button" class="btn-approuver" data-id="${u.id}" data-action="approuver"><i class="bi bi-check-lg" aria-hidden="true"></i> Approuver</button>` : ""}
          ${u.statut !== "REFUSE" ? `<button type="button" class="btn-refuser" data-id="${u.id}" data-action="refuser" ${dernierAdmin ? "disabled" : ""}><i class="bi bi-x-lg" aria-hidden="true"></i> Refuser</button>` : ""}
          ${u.role !== "ADMIN" ? `<button type="button" class="btn-promouvoir" data-id="${u.id}" data-action="promouvoir"><i class="bi bi-arrow-up-circle" aria-hidden="true"></i> Promouvoir</button>` : ""}
          ${u.role === "ADMIN" ? `<button type="button" class="btn-retrograder" data-id="${u.id}" data-action="retrograder" ${dernierAdmin ? "disabled" : ""}><i class="bi bi-arrow-down-circle" aria-hidden="true"></i> Rétrograder</button>` : ""}
          <button type="button" class="btn-motdepasse" data-id="${u.id}" data-action="motdepasse"><i class="bi bi-key" aria-hidden="true"></i> Mot de passe</button>
          <button type="button" class="btn-supprimer" data-id="${u.id}" data-action="supprimer" ${dernierAdmin ? "disabled" : ""}><i class="bi bi-trash" aria-hidden="true"></i> Supprimer</button>
        </div>
      </article>
    `);

    carte.querySelectorAll("[data-action]").forEach((btn) => {
      btn.addEventListener("click", () => actionAdmin(btn.dataset.action, u));
    });

    conteneur.appendChild(carte);
  }
}

async function actionAdmin(action, u) {
  try {
    switch (action) {
      case "approuver":
        await appelApi(`${API}/admin/utilisateurs/${u.id}/approuver`, { method: "PATCH" });
        break;
      case "refuser":
        if (!confirm(`Refuser le compte de ${u.pseudo} ?`)) return;
        await appelApi(`${API}/admin/utilisateurs/${u.id}/refuser`, { method: "PATCH" });
        break;
      case "promouvoir":
        await appelApi(`${API}/admin/utilisateurs/${u.id}/promouvoir`, { method: "PATCH" });
        break;
      case "retrograder":
        if (!confirm(`Rétrograder ${u.pseudo} en USER ?`)) return;
        await appelApi(`${API}/admin/utilisateurs/${u.id}/retrograder`, { method: "PATCH" });
        break;
      case "supprimer":
        if (!confirm(`Supprimer DÉFINITIVEMENT ${u.pseudo} ? Toutes ses séries seront perdues.`)) return;
        await appelApi(`${API}/admin/utilisateurs/${u.id}`, { method: "DELETE" });
        break;
      case "motdepasse": {
        const nouveau = prompt(`Nouveau mot de passe pour ${u.pseudo} (min. 8 caractères) :`);
        if (!nouveau || nouveau.length < 8) {
          if (nouveau) afficherToast("Le mot de passe doit faire au moins 8 caractères.", "erreur");
          return;
        }
        await appelApi(`${API}/admin/utilisateurs/${u.id}/mot-de-passe`, {
          method: "PATCH",
          body: JSON.stringify({ nouveauMotDePasse: nouveau }),
        });
        afficherToast(`Mot de passe de ${u.pseudo} changé avec succès.`, "succes");
        break;
      }
    }
    chargerListeAdmin();
    chargerStatsAdmin();
  } catch (err) {
    afficherToast(err.message, "erreur");
  }
}

document.getElementById("select-statut-admin").addEventListener("change", chargerListeAdmin);
document.getElementById("btn-rafraichir-admin").addEventListener("click", () => {
  chargerStatsAdmin();
  chargerListeAdmin();
});

// --- Écoute du bouton thème ---

document.getElementById("btn-theme")?.addEventListener("click", basculerTheme);

// --- Statistiques utilisateur ---

function formatDuree(minutes) {
  if (!minutes || minutes <= 0) return "0 h";
  const heures = Math.floor(minutes / 60);
  const jours = Math.floor(heures / 24);
  const resteH = heures % 24;
  if (jours > 0) return `${jours} j ${resteH} h`;
  return `${heures} h`;
}

function formatDateRelative(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  const diff = Date.now() - d.getTime();
  const min = Math.floor(diff / 60000);
  if (min < 60) return `il y a ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `il y a ${h} h`;
  const j = Math.floor(h / 24);
  if (j < 30) return `il y a ${j} j`;
  return d.toLocaleDateString("fr-FR");
}

/** Builds a CSS conic-gradient donut background from {v, c} segments. */
function degradeDonut(segments) {
  const total = segments.reduce((t, x) => t + x.v, 0);
  if (!total) return "var(--couleur-surface)";
  let cumul = 0;
  const parts = segments.filter((x) => x.v > 0).map((x) => {
    const debut = (cumul / total) * 100;
    cumul += x.v;
    return `${x.c} ${debut}% ${(cumul / total) * 100}%`;
  });
  return `conic-gradient(${parts.join(", ")})`;
}

function boite(icone, titre, contenu, classe = "") {
  return `<section class="box ${classe}"><h3 class="box-titre"><i class="bi bi-${icone}" aria-hidden="true"></i> ${titre}</h3>${contenu}</section>`;
}

async function chargerMesStats() {
  const conteneur = document.getElementById("stats-contenu");
  conteneur.innerHTML = messageChargement();

  let s;
  try {
    s = await appelApi(`${API}/utilisateurs/${getUtilisateurId()}/stats`);
  } catch (err) {
    conteneur.innerHTML = messageErreur(esc(err.message));
    return;
  }

  const fmt = (n) => Number(n || 0).toLocaleString("fr-FR");
  const completion = s.episodesTotal > 0 ? Math.round((s.episodesVus / s.episodesTotal) * 100) : 0;
  const moyEpisodes = s.seriesTotal > 0 ? Math.round(s.episodesTotal / s.seriesTotal) : 0;
  const maxEpisodes = s.topGenres && s.topGenres.length > 0
    ? Math.max(...s.topGenres.map((g) => g.episodesVus), 1)
    : 1;

  const segments = [
    { nom: "Terminées", v: s.seriesTerminees, c: "var(--c-vu)" },
    { nom: "En cours", v: s.seriesEnCours, c: "var(--c-cours)" },
    { nom: "À voir", v: s.seriesAVoir, c: "var(--c-avoir)" },
    { nom: "Abandonnées", v: s.seriesAbandonnees, c: "var(--c-abandon)" },
  ];
  const totalSeg = segments.reduce((t, x) => t + x.v, 0);
  const resumeDonut = segments.map((x) => `${x.nom} ${x.v}`).join(", ");

  const donut = boite("pie-chart", "Ma collection", `
    <div class="donut-zone">
      <div class="donut" role="img" aria-label="${esc(resumeDonut)}" style="background:${degradeDonut(segments)}">
        <div class="donut-centre"><strong>${fmt(s.seriesTotal)}</strong><span>séries</span></div>
      </div>
      <ul class="legende">
        ${segments.map((x) => `<li><i style="background:${x.c}"></i>${x.nom}<strong>${x.v}</strong><span class="meta">${totalSeg ? Math.round((x.v / totalSeg) * 100) : 0} %</span></li>`).join("")}
      </ul>
    </div>`);

  const progression = boite("speedometer2", "Progression globale", `
    <div class="progress-big" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${completion}" aria-label="Épisodes vus">
      <span style="width:${completion}%"></span>
    </div>
    <p class="progress-legende"><strong>${completion} %</strong> · ${fmt(s.episodesVus)} épisodes vus sur ${fmt(s.episodesTotal)}</p>
    <div class="mini-stats">
      <div><strong>${formatDuree(s.tempsRestantMinutes)}</strong><span>Temps restant</span></div>
      <div><strong>${moyEpisodes}</strong><span>Épisodes par série</span></div>
    </div>`);

  const notes = boite("star", "Mes notes", s.seriesNotees === 0
    ? messageVide("Aucune série notée pour l'instant.")
    : `<div class="note-grande"><strong>${s.notePersonnelleMoyenne ?? "—"}</strong><span>/ 10</span></div>
       <p class="meta">Moyenne sur ${s.seriesNotees} série${s.seriesNotees > 1 ? "s" : ""} notée${s.seriesNotees > 1 ? "s" : ""} · ${s.seriesNonNotees} sans note</p>`);

  const genres = boite("emoji-smile", "Top genres", (!s.topGenres || s.topGenres.length === 0)
    ? messageVide("Aucun épisode visionné pour l'instant.")
    : `<div class="stats-genres">${s.topGenres.map((g) => `
        <div class="genre-barre">
          <span class="genre-nom">${esc(g.genre)}</span>
          <div class="genre-barre-fond"><div class="genre-barre-remplissage" style="width:${(g.episodesVus / maxEpisodes) * 100}%"></div></div>
          <span class="genre-valeur">${g.episodesVus} ép. · ${formatDuree(g.minutesVisionnees)}</span>
        </div>`).join("")}</div>`);

  const activite = boite("clock-history", "Activité récente", (!s.activiteRecente || s.activiteRecente.length === 0)
    ? messageVide("Aucune activité pour l'instant.")
    : `<ul class="stats-activite">${s.activiteRecente.map((a) => `
        <li>
          <span class="act-badge">S${a.saisonNumero}·E${a.episodeNumero}</span>
          <div><strong>${esc(a.serieTitre)}</strong><span class="meta">${esc(a.episodeTitre || "Sans titre")}</span></div>
          <span class="meta">${formatDateRelative(a.dateVisionnage)}</span>
        </li>`).join("")}</ul>`, "pleine");

  conteneur.innerHTML = `
    <div class="kpi-grid">
      <div class="kpi"><i class="bi bi-collection-play" aria-hidden="true"></i><div><span class="kpi-valeur">${fmt(s.seriesTotal)}</span><span class="kpi-label">Séries</span></div></div>
      <div class="kpi kpi-cours"><i class="bi bi-clock" aria-hidden="true"></i><div><span class="kpi-valeur">${formatDuree(s.tempsTotalVisionneMinutes)}</span><span class="kpi-label">Temps visionné</span></div></div>
      <div class="kpi kpi-vu"><i class="bi bi-play-circle" aria-hidden="true"></i><div><span class="kpi-valeur">${fmt(s.episodesVus)}</span><span class="kpi-label">Épisodes vus</span></div></div>
      <div class="kpi kpi-avoir"><i class="bi bi-check2-circle" aria-hidden="true"></i><div><span class="kpi-valeur">${completion} %</span><span class="kpi-label">Complétion</span></div></div>
    </div>
    <div class="stats-layout">${donut}${progression}${notes}${genres}${activite}</div>`;
}

// --- Startup ---

if (getToken()) {
  afficherApp(getPseudo());
} else {
  afficherLogin();
}