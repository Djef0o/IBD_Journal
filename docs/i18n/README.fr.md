# IBD Journal

[English](../../README.md) · **Français** · [Deutsch](README.de.md) · [Español](README.es.md) · [Italiano](README.it.md) · [Português](README.pt.md)

Un journal Android pour les personnes vivant avec la maladie de Crohn ou la rectocolite hémorragique. Notez vos selles et vos symptômes en quelques touches, suivez vos traitements et vos résultats d'analyses, préparez vos examens et apportez un compte rendu clair à votre médecin.

Vos données de santé restent sur votre téléphone.

<p>
  <img src="../screenshots/home.png" width="200" alt="Écran Aujourd'hui">
  <img src="../screenshots/history.png" width="200" alt="Calendrier de l'historique">
  <img src="../screenshots/trends.png" width="200" alt="Tendances">
  <img src="../screenshots/care.png" width="200" alt="Onglet Soins">
</p>

## Fonctionnalités

### Journal quotidien
- **Selles en deux touches** : échelle de Bristol (type 0 = pas de selle ce jour-là), sang, glaires, douleur et fatigue (0–5), impériosité, selles nocturnes.
- **Fausses envies** : envie d'aller sans évacuer de selle, fréquente pendant les poussées. Notées séparément, elles ne sont jamais comptées comme des selles.
- **Repas** : photo ou scan de code-barres (détails du produit depuis Open Food Facts).
- **Rappel du soir** : une seule notification pour noter le type de selle, la douleur et la fatigue sans ouvrir l'application. Elle reste affichée tant que les trois ne sont pas renseignés.

<img src="../screenshots/notification.png" width="400" alt="Notification interactive du soir">

### Historique et tendances
- Calendrier mensuel coloré selon la consistance des selles.
- Selles par jour, 30 jours / 90 jours / tout l'historique, jours avec chaque symptôme comparés à la période précédente.
- **Compte rendu médecin (PDF)** : résumé de la période, symptômes, traitements avec observance, derniers bilans sanguins avec valeurs normales.

### Soins
- **Traitements** : médicaments courants des MICI, rappels, « pris / oublié » depuis la notification, observance, alertes de renouvellement.
- **Bilans sanguins et examens de selles** programmés avec rappels.
- **Coloscopie et rectosigmoïdoscopie** : date de l'examen, régime sans résidu, doses de préparation intestinale (prise fractionnée), consultation d'anesthésie avec un avertissement si elle manque ou a lieu moins de 48 h avant l'examen. Un rappel pour chaque étape.
- **Résultats d'analyses** : 43 examens courants (CRP, calprotectine fécale, numération formule sanguine, bilan du fer, reins, foie, lipides…), valeurs de référence de votre laboratoire ou valeurs adultes par défaut (selon le sexe), valeurs hors norme mises en évidence, graphiques et tableau des bilans sanguins.
- **Import de comptes rendus d'analyses** (PDF ou photos, plusieurs à la fois) : la reconnaissance de texte s'effectue sur le téléphone, les valeurs sont converties dans les unités de l'application, et rien n'est enregistré avant votre vérification.

<p>
  <img src="../screenshots/labs.png" width="200" alt="Résultats d'analyses par examen">
  <img src="../screenshots/lab-panels.png" width="200" alt="Tableau des bilans sanguins">
  <img src="../screenshots/lab-chart.png" width="200" alt="Graphique de la CRP avec zone normale">
</p>

### Autres
- Rappels sous forme de notifications de l'application ou d'événements dans le calendrier du téléphone.
- Sauvegarde et restauration (export de fichier, sauvegarde optionnelle sur Google Drive).
- Premier lancement guidé, thèmes clair et sombre, plusieurs palettes de couleurs.
- Disponible en anglais, français, allemand, espagnol, italien et portugais.

## Confidentialité

- Le journal, les traitements et les résultats d'analyses sont stockés dans une base de données locale sur le téléphone. Il n'y a ni compte ni serveur d'application.
- Les comptes rendus d'analyses sont lus sur l'appareil (reconnaissance de texte Google ML Kit) ; les documents ne sont jamais envoyés en ligne. ML Kit peut transmettre à Google des statistiques d'utilisation anonymes.
- L'accès réseau sert uniquement aux recherches de codes-barres (Open Food Facts) et, si vous l'activez, à la sauvegarde sur Google Drive.

## Compilation

Prérequis : Android Studio (récent), JDK 17, Android SDK 37. L'application fonctionne sur Android 8.0 (API 26) et versions ultérieures.

```bash
./gradlew :app:installDebug          # compile et installe sur un appareil connecté
./gradlew :shared:testAndroidHostTest # tests unitaires du module partagé
```

La signature de version lit `keystore.properties` (non versionné).

## Structure du projet

| Module | Contenu |
|---|---|
| `shared/` | Kotlin Multiplatform : modèles de données, base Room, analyses, analyseur de comptes rendus d'analyses, règles de rappels et d'examens. Compile pour Android et iOS. |
| `app/` | Application Android : interface Jetpack Compose (Material 3), Hilt, notifications, synchronisation du calendrier, rapport PDF, OCR ML Kit. |

## Avertissement

IBD Journal est un outil de suivi personnel. Il ne remplace pas un avis médical : suivez toujours les instructions de votre médecin, en particulier pour la préparation des examens et les traitements.
