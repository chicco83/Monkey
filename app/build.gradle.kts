// Subito+ — modulo app
// v0.1.0 (2026-08-30) — Fase 0 "Sonda": nessuna dipendenza esterna, solo framework Android.
//   Scelta voluta: la sonda deve compilare e installarsi ovunque, senza librerie che
//   possano introdurre variabili nel comportamento del WebView che stiamo misurando.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

    /*
     * v1.14.0 (2026-08-31) — CORREZIONE: applicationId/namespace non devono
     * contenere alcun dato personale del proprietario. Quello di partenza
     * lo faceva fin dalla v0.1.0; ora e' "it.monkey.app", legato al nome
     * dell'app (Monkey) e non a chi l'ha commissionata.
     *
     * ATTENZIONE — applicationId diverso vuol dire APP DIVERSA per Android:
     * questa versione NON aggiorna quella gia' installata, si affianca come
     * seconda app. La vecchia resta installata con dentro login, preferiti
     * live e le sue impostazioni; nella nuova si rifa' il login su Subito e
     * Vinted (i cookie non si spostano fra due app diverse) e si puo'
     * recuperare annunci nascosti + ricerche salvate + regole con "Salva
     * backup" nella vecchia app e "Importa file..." in questa. Fatto quello,
     * la vecchia si puo' disinstallare.
     *
     * (la versione precedente di namespace/applicationId non e' riportata
     * qui per lo stesso motivo della correzione: conteneva un dato personale)
     */
android {
    namespace = "it.monkey.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.monkey.app"
        minSdk = 29            // Android 10: richiesto da MediaStore.Downloads usato per l'export
        targetSdk = 34         // dispositivo di riferimento: Android 13 (API 33)
        versionCode = 38
        versionName = "1.14.0"  // <-- aggiornare a ogni consegna
    }

    // Firma: keystore generato per il progetto, da conservare per tutti gli aggiornamenti futuri.
    signingConfigs {
        create("subitoplus") {
            storeFile = file("../keystore/subitoplus.jks")
            storePassword = "subitoplus"
            keyAlias = "subitoplus"
            keyPassword = "subitoplus"
        }
    }

    buildTypes {
        release {
            /*
             * v0.5.0 — con Compose l'APK non sfoltito arrivava a 18 MB.
             * Si attiva la rimozione del codice e delle risorse inutilizzate,
             * ma senza rinominare nulla (vedi proguard-rules.pro): i metodi del
             * ponte JavaScript sono chiamati per nome dalla pagina.
             *
             * --- versione precedente (fino alla v0.4.6, 2026-08-30) ---
             * isMinifyEnabled = false
             * --- fine versione precedente ---
             */
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("subitoplus")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // v0.5.0 — Fase 4, punto 1: fondamenta grafiche
    // v1.6.0 — buildConfig serve per mostrare la versione nella barra:
    // il numero deve venire da una fonte sola, quella di questo file.
    buildFeatures { compose = true; buildConfig = true }
}

/*
 * v0.2.0 (2026-08-30) — prima dipendenza esterna del progetto.
 * androidx.webkit serve per addDocumentStartJavaScript: iniettare il CSS
 * PRIMA che la pagina esegua i suoi script, cosi' i banner non lampeggiano
 * e in Fase 2 le card scartate non vengono mai disegnate.
 */
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")

    /*
     * v0.5.0 — Compose e Material 3.
     * Il resto dell'interfaccia e' ancora a viste tradizionali e ci resta finche'
     * non viene riscritta (punti 2 e seguenti): Compose e le viste convivono
     * nella stessa schermata, e questo permette di procedere un pezzo per volta
     * senza mai avere una build inutilizzabile.
     */
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // v0.5.2 — caricamento immagini in Compose: cache, annullamento allo scorrimento,
    // priorita' a cio' che e' sullo schermo. Sostituisce il caricatore scritto a mano.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // v0.6.0 — controlli periodici delle ricerche salvate
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
