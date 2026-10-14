# Prvé bezplatné vydanie na Google Play

Produkt: XR18 Bubble · autor Pavol Škoda · podpora pavolskoda@azet.sk. Cieľom je užitočný, udržiavaný nástroj bez reklám a účtov; dôveryhodnosť má stáť na jasnom popise, doloženom testovaní a dostupnej podpore.

## Pripravené lokálne

Natívna Android aplikácia, 12 jazykov, svetlý/tmavý/systémový vzhľad, autorský kredit, kontakt, originálna ikona a feature graphic, popis obchodu EN/SK, dokumentácia, zásady súkromia a statický web v `site/`. Debug APK je určené na lokálne overovanie. Release build vytvára **nepodpísané AAB**, ktoré nie je pripravené na priamy upload bez podpisu.

## Čo treba dokončiť mimo projektu

1. Zvoliť a overiť vydavateľský účet v Play Console. Produktový podpis Pavol Škoda je pripravený podľa zadania; právne údaje účtu potvrď samostatne. [Registrácia](https://support.google.com/googleplay/android-developer/answer/6112435) uvádza jednorazový registračný poplatok 25 USD, ak účet ešte neexistuje.
2. Potvrdiť názov a balík `sk.palko.xr18bubble`. Po vydaní nemeníme identitu balíka tejto aplikácie.
3. Skontrolovať a zverejniť obsah `site/`, napríklad na GitHub Pages. Skutočnú HTTPS URL `privacy.html` uviesť v Play Console. Návod je v `site/README.md`; stránka zatiaľ nie je zverejnená.
4. Vytvoriť bezpečne zálohovaný upload kľúč, podpísať release AAB a zapnúť Play App Signing. Kľúč a heslá nepatria do repozitára. Postup podpisu je v `RELEASE_CHECKLIST.md`.
5. Vyplniť Data safety, reklamy (nie), hodnotenie obsahu, cieľovú skupinu, privacy URL a deklaráciu foreground služby typu `specialUse`. Oprávnenia sú rozobrané v `PERMISSIONS_DECLARATIONS.md`.
6. Nahrať originálne assety a aktuálne snímky aplikácie. Nastaviť kategóriu vhodnú pre zvukovú utility aplikáciu a **bezplatnú distribúciu**. Neuvádzať nepodložené hodnotenia, kompatibilitu ani spojenie s Behringer/Mixing Station.
7. Prejsť interným testovaním na reálnych telefónoch a mixoch. Pre nové osobné účty sa môže vyžadovať uzavretý test s najmenej 12 testermi nepretržite prihlásenými 14 dní a následná žiadosť o produkčný prístup. [Oficiálne podmienky testovania](https://support.google.com/googleplay/android-developer/answer/14151465).
8. Vyhodnotiť pre-launch report, doplniť dôkazy testovania a odoslať konkrétnu verziu na kontrolu. Vydanie nie je schválené tým, že lokálne vzniklo AAB.

## Rozhodnutie o bezplatnom modeli

Aktuálna aplikácia nemá reklamy, predplatné ani nákupy. Google uvádza, že aplikáciu už ponúknutú zadarmo nemožno neskôr prepnúť na platenú pod rovnakým balíkom; platená aplikácia by vyžadovala nový balík. [Pravidlá ceny](https://support.google.com/googleplay/android-developer/answer/6334373).

## Testovacia inštalácia a produkčný podpis

Terajší APK používa vývojový podpis, aby aktualizoval existujúcu skúšobnú inštaláciu. Distribúcia z Play používa produkčný podpis spravovaný cez Play App Signing. Pri prechode môže byť potrebné odinštalovať vývojovú aplikáciu a nastaviť profil znova. Odinštalovanie odstráni lokálne nastavenia aj kľúč pre heslo Wi-Fi; automatické cloudové zálohovanie je vypnuté.

## Dôveryhodnosť po vydaní

Udržiavať stručný changelog, odpovedať na hlásenia, uvádzať konkrétne otestované zariadenia a firmvéry, dopĺňať preklady podľa spätnej väzby a nepoužívať marketingové sľuby o zaručenom Wi-Fi prepnutí. Prvé širšie preklady je vhodné dať pred produkčným vydaním prečítať rodeným hovoriacim.

Aktuálne overené minimálne požiadavky a odkazy sú v `RELEASE_CHECKLIST.md`; Play Console môže pri konkrétnom účte vyžadovať ďalšie kroky.
