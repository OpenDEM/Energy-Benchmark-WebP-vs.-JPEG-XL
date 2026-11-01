# JPEG XL und WebP Bibiliotheken

* Webp 1.6.0: https://storage.googleapis.com/downloads.webmproject.org/releases/webp/index.html, Copyright (c) 2010, Google Inc. All rights reserved. 
* LibJXL 0.12.0: https://github.com/libjxl/libjxl/releases/, BSD-3-Clause license 

# Setup

**Windows 11**

**Start via:** 
```
powershell -ExecutionPolicy Bypass -File .\energy_benchmark.ps1
```
**Ordnerpfade anpassen**


# JPEG XL und WebP mit Standardeinstellungen: Dateigröße und Energiebedarf

Bei der Entwicklung eines WebP Community Modules für den Geoserver mußte ich leider feststellen, 
das der Energiegewinn durch die Übertragung kleinerer Dateien über das Netz durch die Aufwendige Komprimierung mehr
als zunichte.

Das Bildfromat JPEG XL wird inzwischen von vielen Brpwsern unterstütz: https://caniuse.com/jpegxl

Hier wurde untersucht ob sich JPEG XL (JXL) gegenüber WebP als Ausgabeformat für den GeoServer WMS beser eigent.

Verglichen werden nur die **Standardeinstellungen** der Encoder, jeweils mit demselben
PNGs als Eingabe.

## Kurzfazit

- **Mit Standardeinstellungen ist WebP bei allen 12 Test-Bildern sowohl kleiner als auch
  billiger zu encodieren als JPEG XL.**
- Über alle Bilder: WebP erzeugt 12 % der PNG-Größe, JPEG XL 22 %. WebP braucht etwa
  ein Viertel der CPU-Zeit (969 ms gegen 4.000 ms).
- **Einschränkung:** Die Standardeinstellungen sind **nicht qualitätsgleich** (beide
  verlustbehaftet, unterschiedliche Skalen). 

## Verwendete Standardeinstellungen

| Encoder | Aufruf | Standardverhalten |
|---|---|---|
| `cjxl` | `cjxl bild.png bild.jxl` | Effort 7, Distanz 1.0 („visuell verlustfrei“, aber verlustbehaftet); nach Doku standardmäßig multithreaded |
| `cwebp` | `cwebp bild.png -o bild.webp` | verlustbehaftet, Qualität 75, Methode 4 (nach meinem Kenntnisstand, nicht nachgeprüft) |

## Messung ausführen

Im Ordner, in PowerShell:

```
powershell -ExecutionPolicy Bypass -File .\energy_benchmark.ps1
powershell -ExecutionPolicy Bypass -File .\energy_benchmark.ps1 -Runs 10 -CoreWatts 10
```

Das Skript `energy_benchmark.ps1` encodiert alle PNGs aus `images/` und legt die
Ergebnisse in `output_images/` ab. Pro Bild und Encoder gibt es 5 Messläufe (+1 Warmup),
ausgewertet wird der Median.

**Was gemessen wird:** CPU-Zeit des Encoder-Prozesses (User + Kernel, alle Threads
summiert). Das ist die Grundlage für die Energieabschätzung, weil `cjxl` mehrere Kerne
nutzt und die gemessen Laufzeit den Aufwand sonst unterschätzen würde.

**Was abgeschätzt wird (Annahmen, keine Messungen):**
- Encoding-Energie = CPU-Zeit × 15 W pro aktivem Kern (`-CoreWatts`)
- Übertragungsenergie = Dateigröße × 0,01 / 0,06 / 0,1 kWh pro GB (`-KWhPerGB`)

## Ergebnisse (12 Bilder, Standardeinstellungen)

| Bild | PNG [KB] | JXL [KB] | JXL % | WebP [KB] | WebP % | JXL CPU [ms] | WebP CPU [ms] |
|---|---:|---:|---:|---:|---:|---:|---:|
| dop_l | 2.089,7 | 307,5 | 15 % | 198,5 | 9 % | 766 | 203 |
| dop_m | 651,6 | 96,1 | 15 % | 61,2 | 9 % | 250 | 78 |
| dop_s | 70,7 | 11,5 | 16 % | 7,6 | 11 % | 31 | 31 |
| lines_l | 245,8 | 82,1 | 33 % | 41,4 | 17 % | 656 | 125 |
| lines_m | 90,2 | 32,0 | 36 % | 16,1 | 18 % | 156 | 47 |
| lines_point_l | 428,1 | 138,1 | 32 % | 69,1 | 16 % | 703 | 125 |
| lines_point_m | 194,1 | 64,2 | 33 % | 32,5 | 17 % | 156 | 47 |
| lines_point_s | 75,3 | 24,9 | 33 % | 13,7 | 18 % | 78 | 31 |
| lines_s | 13,6 | 5,4 | 40 % | 2,5 | 19 % | 31 | 31 |
| poly_l | 495,6 | 172,9 | 35 % | 67,7 | 14 % | 719 | 125 |
| poly_m | 193,4 | 70,9 | 37 % | 27,7 | 14 % | 281 | 78 |
| poly_s | 77,7 | 28,0 | 36 % | 11,0 | 14 % | 172 | 47 |

Summe über alle Bilder:

| Variante | Größe [KB] | vs. PNG | CPU [ms] | Laufzeit [ms] | Encoding [J] |
|---|---:|---:|---:|---:|---:|
| PNG (Eingabe) | 4.625,8 | 100 % | – | – | – |
| JPEG XL | 1.033,6 | 22 % | 4.000 | 1.761 | 60,00 |
| WebP | 549,1 | 12 % | 969 | 1.262 | 14,53 |

Gesamtenergie für alle 12 Bilder: einmal encodieren plus N Auslieferungen [J]
(Annahmen: 15 W pro Kern, Übertragung wie angegeben):

| Übertragung | N | JPEG XL [J] | WebP [J] |
|---|---:|---:|---:|
| 0,01 kWh/GB | 1 | 98 | 35 |
| 0,01 kWh/GB | 100 | 3.870 | 2.039 |
| 0,01 kWh/GB | 10.000 | 381.076 | 202.450 |
| 0,06 kWh/GB | 1 | 289 | 136 |
| 0,06 kWh/GB | 100 | 22.921 | 12.161 |
| 0,06 kWh/GB | 10.000 | 2.286.154 | 1.214.630 |
| 0,1 kWh/GB | 1 | 441 | 217 |
| 0,1 kWh/GB | 100 | 38.162 | 20.258 |
| 0,1 kWh/GB | 10.000 | 3.810.217 | 2.024.374 |

## Auswertung

- **WebP gewinnt in jeder Hinsicht, die gemessen wurde.** Bei keinem Bild und bei keiner
  der drei Annahmen für die Übertragung ist JPEG XL günstiger. Die Break-even-Auswertung
  pro Bild ergibt überall „WebP besser (Encoding und Größe)“.
- **Das Verhältnis ist bei allen Bildtypen ähnlich.** WebP ist bei Luftbildern (`dop`) etwa
  35 % kleiner als JXL, bei Linien- und Flächenkarten (`lines`, `poly`) etwa 50 % kleiner.
- **Encoding-Aufwand:** JPEG XL nutzt im Schnitt etwa 2,3 Kerne gleichzeitig (CPU-Zeit
  geteilt durch Laufzeit). WebP kommt auf etwa 0,8, hier steckt in der Laufzeit auch
  der Prozessstart. Der Unterschied in der Laufzeit (1.761 gegen 1.262 ms) ist daher
  deutlich kleiner als der in der CPU-Zeit. Für den Energieverbrauch zählt die CPU-Zeit.
- **Die CPU-Zeiten sind grob quantisiert.** Viele Werte sind Vielfache von etwa 15,6 ms
  (31, 47, 78, 125, ...). Das ist die Zeitauflösung der Windows-Prozesszeit. Bei kleinen
  Bildern (`*_s`) sind die Unterschiede in der CPU-Zeit daher nicht belastbar, z. B.
  31 gegen 31 ms bei `dop_s` und `lines_s`. Die Aussage „WebP braucht weniger“ stützt sich
  vor allem auf die mittleren und großen Bilder.

## Einschränkungen

- **Qualität nicht gleichwertig.** JPEG XL mit Distanz 1.0 und WebP mit Qualität 75 sind
  keine äquivalenten Einstellungen. WebP könnte stärker komprimieren.
- **Energie ist abgeschätzt.** Die Leistung pro Kern (15 W) und die Energie pro
  übertragenem GB sind Annahmen.
- **PNG-Referenz ohne Encoding-Aufwand.** Die PNG-Zeile zeigt nur die Größe der
  Eingabedateien. Wie viel Energie das ursprüngliche PNG-Encoding im GeoServer kostet,
  ist nicht gemessen.
- **Externe Prozesse.** Beide Encoder laufen als eigener Prozess, die CPU-Zeit enthält
  Prozessstart und Datei-Ein-/Ausgabe. Ein Plugin mit direkter Anbindung (z. B. die
  JNI-Bindings im libjxl-Release) hätte weniger Overhead.
- **Nur 12 Bilder** aus einem Datensatz, 5 Durchläufe pro Bild.
- **Dekodierung beim Client** und **Netzwerkenergie im Detail** sind nicht erfasst.
  

## Frühere Vormessung (nicht Standardeinstellungen)

Ein einzelnes Tile (`tile.png`), Java-Benchmark `JxlVsPngBenchmark.java`, Laufzeit,
ein Thread, verlustfrei bzw. mit expliziten Stufen. Diese Werte sind **nicht** mit den
Standardeinstellungen oben vergleichbar:

| Variante | Bytes | vs. PNG | ms |
|---|---:|---:|---:|
| PNG (ImageIO) | 85.363 | 100 % | 28,7 |
| PNG8 | 68.452 | 80 % | 36,5 – 41,2 |
| JXL e1 / e3 / e5 / e7 / e9 verlustfrei | 78.105 / 74.463 / 47.208 / 37.770 / 33.933 | 91 / 87 / 55 / 44 / 40 % | 35 / 118 / 210 / 258 / 890 |
| JXL e3 d=1 | 101.702 | 119 % | 88,8 |
| WebP verlustfrei z1 / z6 / z9 | 50.708 / 42.594 / 41.528 | 59 / 50 / 49 % | 70 / 108 / 3.765 |
| WebP q90 | 101.774 | 119 % | 113,4 |

Das zeigt vor allem, wie stark die Encoder-Einstellung den Aufwand bestimmt: Zwischen der
schnellsten und der langsamsten Stufe liegt bei WebP etwa der Faktor 50, bei JXL etwa 25,
und die höchsten Stufen bringen nur wenige Prozent zusätzliche Ersparnis. Bei diesem
Tile waren die verlustbehafteten Varianten größer als das PNG, bei den 12 Bildern oben
dagegen deutlich kleiner. Das Ergebnis hängt also stark vom Bildinhalt ab.

## Dateien

| Datei | Zweck |
|---|---|
| `energy_benchmark.ps1` | Standardeinstellungen: Größe, CPU-Zeit, Energieabschätzung für alle Bilder |
| `JxlVsPngBenchmark.java` | Frühere Vormessung mit expliziten Effort-/Qualitätsstufen für ein Tile |
| `JxlEncodeExample.java`, `benchmark_effort.bat` | Kleine Aufrufbeispiele für `cjxl` |
| `images/`, `output_images/` | Eingabe-PNGs, encodierte `.jxl` und `.webp` |
| `x64-windows-static/`, `cwebp.exe`, `libwebp-1.6.0-windows-x64/` | Releases von libjxl und libwebp |
