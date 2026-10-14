# XR18 Bubble — používateľská príručka

Vyvinul Pavol Škoda · podpora: pavolskoda@azet.sk · verzia 0.4.0

## Na čo aplikácia slúži

XR18 Bubble drží niekoľko dôležitých ovládačov mixu v malom plávajúcom paneli. Je samostatným doplnkom k Mixing Station a komunikuje priamo s mixom. Podporované sú Behringer XR18/X18 a Midas MR18; aplikácia vyžaduje Android 10 alebo novší.

## Prvé nastavenie

1. Pripoj telefón k Wi-Fi mixu a otvor kartu **Mixy**.
2. Obnovením vyhľadaj mix alebo zadaj jeho IP adresu ručne. Vyber iba svoj mix.
3. Povoľ zapamätanie Wi-Fi. Android na prečítanie názvu siete vyžaduje presnú polohu a zapnutú službu Poloha. Aplikácia nepoužíva GPS ani fyzickú polohu. Názov siete uloží až po odpovedi mixu.
4. Pri zabezpečenej Wi-Fi zadaj heslo raz. Aplikácia nečíta heslá sietí uložených v Androide. Zabezpečenie nastav podľa routera, pri otvorenej sieti heslo netreba.
5. V karte **Ovládače** zaškrtni položky, ktoré chceš mať v paneli. Odškrtnutím ich odstrániš. Pri kanáli, DCA alebo zbernici zvoľ hlasitosť, mute alebo oboje. Mute skupina má iba tlačidlo. Podržaním názvu ho premenuješ.
6. Stlač **Zobraziť bublinku**, povoľ zobrazovanie nad inými aplikáciami a podľa systému aj oznámenia. Bublinku presuň k okraju obrazovky.

## Otvorenie a pripojenie

Otvorenie bublinky pri zapnutej voľbe **Pripojiť pri otvorení** vyžiada uloženú Wi-Fi, spojí sa s mixom a načíta vybrané hodnoty. Zelený stav **Pripojené** znamená, že sú ovládače synchronizované a možno ich meniť. Pri výpadku sa zablokujú; staré príkazy sa pri návrate spojenia neprehrávajú.

Android môže pri prvej žiadosti ukázať systémové potvrdenie. Úplne tiché prepínanie sa nedá zaručiť na každom telefóne. Pri sekundárnom lokálnom pripojení môže Android ponechať inú predvolenú sieť pre ostatné aplikácie; pripojenie XR18 Bubble samo nezaručuje pripojenie Mixing Station.

## Návrat na pôvodnú Wi-Fi

Ak toto otvorenie vyžiadalo inú Wi-Fi, panel ponúkne tlačidlo **Späť na …**. Pri zatvorení sa tiež opýta na návrat.

- **Vrátiť sa** zbalí panel a uvoľní Wi-Fi požiadavku aplikácie. Android potom vyberie uloženú sieť; návrat k presnému názvu závisí od jej dostupnosti a systému.
- **Zostať pri mixe** ponechá spojenie. Ďalšie otvorenie a zatvorenie na rovnakej sieti už otázku nezopakuje. Ručné návratové tlačidlo ostáva dostupné.
- **Zrušiť** ponechá panel otvorený.

Aplikácia si pamätá názov predchádzajúcej siete, nie jej heslo. Ak sa pri otvorení sieť nezmenila, otázka sa nezobrazí.

## Merače a veľkosť panelu

Farebné stĺpce pri faderoch zobrazujú skutočnú úroveň signálu a krátko držanú špičku. Master má L/R merač; kanály, zbernice a DCA jeden stĺpec. Merače kanálov a zberníc sú pred faderom, master za faderom. Mute skupina nemá vlastný merač. Pri tichom signále môže byť stĺpec prázdny; po výpadku dát merač zmizne.

Panel sa prispôsobuje počtu aj druhu ovládačov. Samotná mute skupina má nízky panel, kanál s hlasitosťou vyšší fader. Tri veľkosti ovládačov a bublinky sa volia v nastaveniach. Viac faderov sa posúva vodorovne.

## Skratka na Mixing Station alebo inú aplikáciu

Tri bodky v paneli otvoria kartu **Mixy**. Vedľa obnovenia je malá ikona Mixing Station, ak je nainštalovaná. Ťuknutím ju otvoríš. Podržaním ikony vyberieš inú nainštalovanú aplikáciu; zoznam možno prehľadávať a voľba sa uloží. Ak cieľová aplikácia chýba, skratka ponúkne nový výber.

## Vzhľad, jazyk a kontakt

Karta **Nastavenia** obsahuje systémový, svetlý a tmavý vzhľad. Audio panel ostáva tmavý aj pri svetlých nastaveniach. Dostupná je angličtina, slovenčina, čeština, nemčina, poľština, maďarčina, francúzština, španielčina, taliančina, portugalčina, holandčina a ukrajinčina. Jazyk možno prevziať zo systému alebo zvoliť ručne; preklady sú dostupné aj offline.

V časti **O aplikácii** je autor Pavol Škoda, verzia a kontakt pavolskoda@azet.sk. Ťuknutie na kontakt otvorí e-mailový klient; správu aplikácia sama neposiela. Pri hlásení chyby uveď model telefónu, Android, model/firmvér mixu a presný postup. Neposielaj heslá Wi-Fi.

## Keď niečo nefunguje

| Problém | Čo preveriť |
|---|---|
| Mix sa nenašiel | Telefón musí byť na správnej Wi-Fi; over IP adresu a prístup lokálnych zariadení na routeri. |
| Wi-Fi sa nezapamätala | Povoľ presnú polohu, zapni systémovú službu Poloha a pri spojenom mixe stlač Zapamätať Wi-Fi mixu. Názov možno zadať aj ručne. |
| Žiadosť o inú Wi-Fi zlyhá | Over dostupnosť siete, SSID, zabezpečenie, heslo a povolenie Blízke zariadenia. Android môže vyžiadať potvrdenie. |
| Ovládače sú sivé | Ešte sa načítavajú alebo mix prestal odpovedať. Stav musí byť zelený. |
| Merač sa nehýbe | Over, že na danom mieste mixu je signál; polohou fadera samotnou sa merač nevytvorí. |
| Bublinka zmizne | Over povolenie prekrytia a obmedzenia aplikácie na pozadí v systéme výrobcu telefónu. |

Pred živým vystúpením otestuj konkrétny telefón, mix aj router. Aplikácia neobsahuje reklamy, analytiku ani cloudový účet. Nastavenia sú lokálne a heslo je šifrované pomocou Android Keystore; samotný lokálny protokol mixu nie je šifrovaný.
