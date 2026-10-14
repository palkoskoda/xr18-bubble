# Prvé bezplatné vydanie na Google Play

Produkt: XR18 Bubble · autor Pavol Škoda · podpora pavolskoda@azet.sk. Cieľom je užitočný, udržiavaný nástroj bez reklám a účtov; dôveryhodnosť má stáť na jasnom popise, doloženom testovaní a dostupnej podpore.

Aktuálne verejné vydanie 0.4.1 (version code 6) používa GitHub Releases a GitHub Pages. Google Play je plánovaný ďalší distribučný kanál. Registráciu účtu, overenie totožnosti a odoslanie v Play Console musí dokončiť vydavateľ; aplikácia zatiaľ nemá potvrdené schválenie Google Play.

## Pripravené lokálne

Natívna Android aplikácia, 12 jazykov, svetlý/tmavý/systémový vzhľad, autorský kredit, kontakt, originálna ikona a feature graphic, popis obchodu EN/SK, dokumentácia, zásady súkromia a statický web v `site/`. Verzia 0.4.1 má sedem veľkostí ovládačov až po Maximum (4XL) a vypnutie potiahnutím bublinky na spodný terč ×. Na [GitHub Releases](https://github.com/palkoskoda/xr18-bubble/releases/tag/v0.4.1-beta) je verejné APK podpísané vydavateľským kľúčom. Samostatné debug APK slúži na lokálne overovanie. Release build vytvára **nepodpísané AAB**, ktoré treba pred uploadom podpísať.

## Čo zdôrazniť v popise obchodu

Používateľ si pripraví celý mix na tablete, počítači alebo v inej ovládacej aplikácii. Na telefóne si potom nechá po ruke iba často používané prvky, napríklad master, DCA, vybraný kanál alebo mute skupinu. Sedem veľkostí zväčšuje aj písmo, číselné hodnoty, fadery, merače a mute tlačidlá. To môže pomôcť ľuďom, ktorým sa malé ovládače na telefóne ťažko čítajú alebo používajú.

Obe zariadenia ovládajú ten istý kompatibilný mix cez lokálnu sieť; aplikácia aktualizuje živé hodnoty z mixu. Profily a nastavenia aplikácií sa medzi telefónom a tabletom neprenášajú. Neuvádzať sľub certifikovanej prístupnosti, zdravotný prínos ani náhradu plnohodnotnej aplikácie na kompletné nastavenie mixu. Kompatibilný mix a prístup do jeho siete sú potrebné na živé ovládanie.

## Čo treba dokončiť mimo projektu

1. Zvoliť a overiť vydavateľský účet v Play Console. Produktový podpis Pavol Škoda je pripravený podľa zadania; právne údaje účtu potvrď samostatne. [Registrácia](https://support.google.com/googleplay/android-developer/answer/6112435) uvádza jednorazový registračný poplatok 25 USD, ak účet ešte neexistuje.
2. Potvrdiť názov a balík `sk.palko.xr18bubble`. Po vydaní nemeníme identitu balíka tejto aplikácie.
3. Skontrolovať a zverejniť obsah `site/`, napríklad na GitHub Pages. Skutočnú HTTPS URL `privacy.html` uviesť v Play Console. Návod je v `site/README.md`; stránka je už zverejnená na GitHub Pages.
4. Pred nastavením Play App Signing rozhodnúť o kontinuite podpisu s verejným GitHub APK. Ak majú aktualizácie z GitHubu a Google Play na seba nadväzovať pri rovnakom balíku, výsledný podpis distribučného APK z Play musí používať rovnaký podpisový certifikát ako existujúce verejné APK. Existujúci vydavateľský kľúč je uložený súkromne mimo repozitára. Upload kľúč na podpis AAB môže byť samostatný; nie je totožný s podpisom, ktorým Play podpisuje APK pre používateľov. Konkrétny postup registrácie existujúceho podpisového kľúča, zálohovania a podpisu AAB dokončiť podľa možností Play Console a `RELEASE_CHECKLIST.md`. Kľúče ani heslá nepatria do repozitára; táto príprava žiadne kľúče nevytvára ani neexportuje.
5. Vyplniť Data safety, reklamy (nie), hodnotenie obsahu, cieľovú skupinu, privacy URL a deklaráciu foreground služby typu `specialUse`. Oprávnenia sú rozobrané v `PERMISSIONS_DECLARATIONS.md`.
6. Nahrať originálne assety a aktuálne snímky aplikácie. Nastaviť kategóriu vhodnú pre zvukovú utility aplikáciu a **bezplatnú distribúciu**. Neuvádzať nepodložené hodnotenia, kompatibilitu ani spojenie s Behringer/Mixing Station.
7. Prejsť interným testovaním na reálnych telefónoch a mixoch. Pre nové osobné účty sa môže vyžadovať uzavretý test s najmenej 12 testermi nepretržite prihlásenými 14 dní a následná žiadosť o produkčný prístup. [Oficiálne podmienky testovania](https://support.google.com/googleplay/android-developer/answer/14151465).
8. Vyhodnotiť pre-launch report, doplniť dôkazy testovania a odoslať konkrétnu verziu na kontrolu. Vydanie nie je schválené tým, že lokálne vzniklo AAB.

## Rozhodnutie o bezplatnom modeli

Aktuálna aplikácia nemá reklamy, predplatné ani nákupy. Google uvádza, že aplikáciu už ponúknutú zadarmo nemožno neskôr prepnúť na platenú pod rovnakým balíkom; platená aplikácia by vyžadovala nový balík. [Pravidlá ceny](https://support.google.com/googleplay/android-developer/answer/6334373).

## Testovacia inštalácia a produkčný podpis

Verejné GitHub APK 0.4.1 používa vydavateľský podpis. Vývojové debug APK má odlišný podpis a slúži na lokálne testovanie. Pred uploadom porovnať certifikát verejného APK s certifikátom podpisového kľúča aplikácie uvedeným v Play Console; samostatne skontrolovať upload certifikát. Vyšší version code je tiež potrebný na bežnú aktualizáciu existujúcej inštalácie; pri pokračovaní po verejnej verzii s kódom 6 pripraviť nový kód, napríklad 7, a zodpovedajúce označenie vydania.

Pri odlišnom distribučnom podpise nie je možná priama aktualizácia pod rovnakým balíkom. Prechod z debug inštalácie môže vyžadovať odinštalovanie a opätovné nastavenie profilu. Odinštalovanie odstráni lokálne nastavenia aj kľúč pre heslo Wi-Fi; automatické cloudové zálohovanie je vypnuté. Existujúce inštalácie neodinštalovať bez vedomého rozhodnutia používateľa.

## Záznam pre kontrolu Google Play

Podľa `APP_ACCESS_AND_FGS.md` pripraviť krátke video na skutočnom podporovanom zariadení a kompatibilnom mixe. Ukázať spustenie bublinky, synchronizáciu stavu pred povolením ovládania, veľkosť Maximum (4XL), vybrané často používané prvky a vypnutie potiahnutím na × aj cez notifikáciu. Bez mixu ostávajú živé ovládače zablokované; v pokynoch pre kontrolóra túto závislosť jasne uviesť. Heslá Wi-Fi a súkromné notifikácie do videa nepatria.

## Dôveryhodnosť po vydaní

Udržiavať stručný changelog, odpovedať na hlásenia, uvádzať konkrétne otestované zariadenia a firmvéry, dopĺňať preklady podľa spätnej väzby a nepoužívať marketingové sľuby o zaručenom Wi-Fi prepnutí. Prvé širšie preklady je vhodné dať pred produkčným vydaním prečítať rodeným hovoriacim.

Aktuálne overené minimálne požiadavky a odkazy sú v `RELEASE_CHECKLIST.md`; Play Console môže pri konkrétnom účte vyžadovať ďalšie kroky.
