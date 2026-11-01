# JPEG XL and WebP Libraries

* WebP 1.6.0: https://storage.googleapis.com/downloads.webmproject.org/releases/webp/index.html, Copyright (c) 2010, Google Inc. All rights reserved.
* libjxl 0.12.0: https://github.com/libjxl/libjxl/releases/, BSD-3-Clause license

# Setup

**Windows 11**

**Start via:**
```
powershell -ExecutionPolicy Bypass -File .\energy_benchmark.ps1
```
**Adjust the folder paths**


# JPEG XL and WebP with Default Settings: File Size and Energy Use

While developing a WebP community module for GeoServer, I unfortunately found that the
energy saved by transferring smaller files over the network was more than cancelled out by
the costly compression.

The JPEG XL image format is now supported by many browsers: https://caniuse.com/jpegxl

This project examines whether JPEG XL (JXL) is better suited than WebP as a output format
for GeoServer WMS.

Only the **default settings** of the encoders are compared, each using the same PNGs as input.

## Summary

- **With default settings, WebP is both smaller and cheaper to encode than JPEG XL for all
  12 test images.**
- Over all images: WebP produces 12% of the PNG size, JPEG XL 22%. WebP needs about
  a quarter of the CPU time (969 ms vs. 4,000 ms).
- **Limitation:** The default settings are **not equivalent in quality** (both are lossy,
  on different scales).

## Default Settings Used

| Encoder | Call | Default behavior |
|---|---|---|
| `cjxl` | `cjxl image.png image.jxl` | Effort 7, distance 1.0 ("visually lossless", but lossy); multithreaded by default according to the documentation |
| `cwebp` | `cwebp image.png -o image.webp` | lossy, quality 75, method 4 (to the best of my knowledge, not verified) |

## Running the Measurement

In the folder, in PowerShell:

```
powershell -ExecutionPolicy Bypass -File .\energy_benchmark.ps1
powershell -ExecutionPolicy Bypass -File .\energy_benchmark.ps1 -Runs 10 -CoreWatts 10
```

The script `energy_benchmark.ps1` encodes all PNGs from `images/` and writes the results
to `output_images/`. There are 5 measurement runs per image and encoder (+1 warmup);
the median is used.

**What is measured:** The CPU time of the encoder process (user + kernel, summed over all
threads). This is the basis for the energy estimate, because `cjxl` uses several cores and
the measured runtime would otherwise underestimate the effort.

**What is estimated (assumptions, not measurements):**
- Encoding energy = CPU time x 15 W per active core (`-CoreWatts`)
- Transfer energy = file size x 0.01 / 0.06 / 0.1 kWh per GB (`-KWhPerGB`)

## Results (12 Images, Default Settings)

| Image | PNG [KB] | JXL [KB] | JXL % | WebP [KB] | WebP % | JXL CPU [ms] | WebP CPU [ms] |
|---|---:|---:|---:|---:|---:|---:|---:|
| dop_l | 2,089.7 | 307.5 | 15% | 198.5 | 9% | 766 | 203 |
| dop_m | 651.6 | 96.1 | 15% | 61.2 | 9% | 250 | 78 |
| dop_s | 70.7 | 11.5 | 16% | 7.6 | 11% | 31 | 31 |
| lines_l | 245.8 | 82.1 | 33% | 41.4 | 17% | 656 | 125 |
| lines_m | 90.2 | 32.0 | 36% | 16.1 | 18% | 156 | 47 |
| lines_point_l | 428.1 | 138.1 | 32% | 69.1 | 16% | 703 | 125 |
| lines_point_m | 194.1 | 64.2 | 33% | 32.5 | 17% | 156 | 47 |
| lines_point_s | 75.3 | 24.9 | 33% | 13.7 | 18% | 78 | 31 |
| lines_s | 13.6 | 5.4 | 40% | 2.5 | 19% | 31 | 31 |
| poly_l | 495.6 | 172.9 | 35% | 67.7 | 14% | 719 | 125 |
| poly_m | 193.4 | 70.9 | 37% | 27.7 | 14% | 281 | 78 |
| poly_s | 77.7 | 28.0 | 36% | 11.0 | 14% | 172 | 47 |

Total over all images:

| Variant | Size [KB] | vs. PNG | CPU [ms] | Runtime [ms] | Encoding [J] |
|---|---:|---:|---:|---:|---:|
| PNG (input) | 4,625.8 | 100% | – | – | – |
| JPEG XL | 1,033.6 | 22% | 4,000 | 1,761 | 60.00 |
| WebP | 549.1 | 12% | 969 | 1,262 | 14.53 |

Total energy for all 12 images: encode once plus N deliveries [J]
(assumptions: 15 W per core, transfer as stated):

| Transfer | N | JPEG XL [J] | WebP [J] |
|---|---:|---:|---:|
| 0.01 kWh/GB | 1 | 98 | 35 |
| 0.01 kWh/GB | 100 | 3,870 | 2,039 |
| 0.01 kWh/GB | 10,000 | 381,076 | 202,450 |
| 0.06 kWh/GB | 1 | 289 | 136 |
| 0.06 kWh/GB | 100 | 22,921 | 12,161 |
| 0.06 kWh/GB | 10,000 | 2,286,154 | 1,214,630 |
| 0.1 kWh/GB | 1 | 441 | 217 |
| 0.1 kWh/GB | 100 | 38,162 | 20,258 |
| 0.1 kWh/GB | 10,000 | 3,810,217 | 2,024,374 |

## Analysis

- **WebP wins in every respect that was measured.** For no image and under none of the
  three transfer assumptions is JPEG XL cheaper. The per-image break-even analysis
  yields "WebP better (encoding and size)" everywhere.
- **The ratio is similar for all image types.** WebP is about 35% smaller than JXL for
  aerial images (`dop`) and about 50% smaller for line and polygon maps (`lines`, `poly`).
- **Encoding effort:** JPEG XL uses about 2.3 cores at the same time on average (CPU time
  divided by runtime). WebP reaches about 0.8; here the runtime also includes the process
  start. The difference in runtime (1,761 vs. 1,262 ms) is therefore much smaller than
  the difference in CPU time. For energy use, the CPU time is what counts.
- **The CPU times are coarsely quantized.** Many values are multiples of about 15.6 ms
  (31, 47, 78, 125, ...). This is the time resolution of the Windows process time. For
  small images (`*_s`) the differences in CPU time are therefore not reliable, e.g.
  31 vs. 31 ms for `dop_s` and `lines_s`. The statement "WebP needs less" rests mainly
  on the medium and large images.

## Limitations

- **Quality is not equivalent.** JPEG XL with distance 1.0 and WebP with quality 75 are
  not equivalent settings. WebP may compress more aggressively.
- **Energy is estimated.** The power per core (15 W) and the energy per transferred GB
  are assumptions.
- **The PNG reference has no encoding effort.** The PNG row only shows the size of the
  input files. How much energy the original PNG encoding in GeoServer costs has not been
  measured.
- **External processes.** Both encoders run as separate processes; the CPU time includes
  process start and file I/O. A plugin with a direct integration (e.g. the JNI bindings
  in the libjxl release) would have less overhead.
- **Only 12 images** from one dataset, 5 runs per image.
- **Decoding on the client** and **network energy in detail** are not covered.


## Earlier Pre-Measurement (Not Default Settings)

A single tile (`tile.png`), Java benchmark `JxlVsPngBenchmark.java`, runtime,
one thread, lossless or with explicit levels. These values are **not** comparable with the
default settings above:

| Variant | Bytes | vs. PNG | ms |
|---|---:|---:|---:|
| PNG (ImageIO) | 85,363 | 100% | 28.7 |
| PNG8 | 68,452 | 80% | 36.5 – 41.2 |
| JXL e1 / e3 / e5 / e7 / e9 lossless | 78,105 / 74,463 / 47,208 / 37,770 / 33,933 | 91 / 87 / 55 / 44 / 40% | 35 / 118 / 210 / 258 / 890 |
| JXL e3 d=1 | 101,702 | 119% | 88.8 |
| WebP lossless z1 / z6 / z9 | 50,708 / 42,594 / 41,528 | 59 / 50 / 49% | 70 / 108 / 3,765 |
| WebP q90 | 101,774 | 119% | 113.4 |

This mainly shows how strongly the encoder setting determines the effort: between the
fastest and the slowest level there is a factor of about 50 for WebP and about 25 for JXL,
and the highest levels bring only a few percent of additional savings. For this
tile the lossy variants were larger than the PNG, whereas for the 12 images above
they were much smaller. The result therefore depends strongly on the image content.

## Files

| File | Purpose |
|---|---|
| `energy_benchmark.ps1` | Default settings: size, CPU time, energy estimate for all images |
| `JxlVsPngBenchmark.java` | Earlier pre-measurement with explicit effort/quality levels for one tile |
| `JxlEncodeExample.java`, `benchmark_effort.bat` | Small usage examples for `cjxl` |
| `images/`, `output_images/` | Input PNGs, encoded `.jxl` and `.webp` files |
| `libjxl_0_12_0/`, `libwebp-1_6_0/` | Releases of libjxl and libwebp |
