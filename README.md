# Hyper Debloat

Mala Android aplikacija za bezbedniji HyperOS/MIUI debloat preko Shizuku.

## Šta radi

- skenira samo unapred definisan katalog kandidata;
- automatski označava samo reklamne/telemetrijske i OEM pomoćne stubove;
- kritični sistemski paketi su tvrdo blokirani;
- uklanja paket samo za Android korisnika 0: `pm uninstall --user 0 <paket>`;
- može da vrati pakete uklonjene u istoj sesiji: `cmd package install-existing --user 0 <paket>`.

## Važno

Aplikacija NE pokušava da briše APK fajlove iz /system i NE traži root. Potreban je pokrenut Shizuku i dozvola za Hyper Debloat.

## Pravljenje APK-a

Otvori GitHub Actions, izaberi **Build APK**, pa **Run workflow**. Kada se build završi, preuzmi artifact `HyperDebloat-debug-apk`; u njemu je `app-debug.apk`.
