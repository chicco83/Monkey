# Subito+ — regole di sfoltimento | v0.5.0 (2026-08-30)
#
# Si sfoltisce il codice inutilizzato (Compose ne porta molto) ma NON si
# rinomina nulla: i metodi del ponte JavaScript vengono chiamati per nome
# dalla pagina, e un rinominatore li romperebbe in modo silenzioso.
-dontobfuscate

# Cintura di sicurezza, nel caso un domani si volesse riattivare il rinominatore.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface, *Annotation*, Signature
