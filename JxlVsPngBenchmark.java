import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *
 * # This code was generated using AI and manually reviewed.
 *  
 * Compares PNG (ImageIO, in-process) against JPEG XL (cjxl.exe, external process)
 * and WebP (cwebp.exe, external process).
 *
 * Run (Java 11+):
 *   java JxlVsPngBenchmark.java tile.png [runs]
 *
 * The elapsed (wall-clock) time per encoding is measured. All encodings run with a single
 * thread, so the elapsed time is a usable approximation of the CPU time.
 * Note: The JXL and WebP values include the process start of cjxl.exe / cwebp.exe. This is
 * intentional, because a plugin using ProcessBuilder would have the same overhead.
 */
public class JxlVsPngBenchmark {

    private static final Path CJXL = Paths.get(
            "C:\\Users\\marti\\Desktop\\geoserver_jpegxl\\libjxl_0_12_0\\cjxl.exe");

    // cwebp.exe from libwebp. Path can be overridden with -Dcwebp=... If the file is
    // missing, the WebP comparison is skipped.
    private static final Path CWEBP = Paths.get(System.getProperty("cwebp",
            "C:\\Users\\marti\\Desktop\\geoserver_jpegxl\\libwebp-1_6_0\\cwebp.exe"));
    private static final int[] WEBP_LOSSLESS_LEVELS = {1, 6, 9};

    private static final int WARMUP = 2;
    private static final int[] EFFORTS = {1, 3, 5, 7, 9};

    interface Job {
        long run() throws Exception; // returns the size of the result in bytes
    }

    static final class Result {
        private final String name;
        private final long bytes;
        private final double medianMs;

        Result(String name, long bytes, double medianMs) {
            this.name = name;
            this.bytes = bytes;
            this.medianMs = medianMs;
        }

        String name() { return name; }
        long bytes() { return bytes; }
        double medianMs() { return medianMs; }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java JxlVsPngBenchmark.java <image.png> [runs]");
            return;
        }
        int runs = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        BufferedImage src = ImageIO.read(Paths.get(args[0]).toFile());
        if (src == null) {
            throw new IOException("Could not read image: " + args[0]);
        }
        boolean alpha = src.getColorModel().hasAlpha();
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(),
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        // Raw data (PPM/PAM) as input for cjxl, so that cjxl does not have to decode a PNG
        Path raw = Files.createTempFile("jxlbench_in_", alpha ? ".pam" : ".ppm");
        writeRaw(img, raw, alpha);
        Path outJxl = Files.createTempFile("jxlbench_out_", ".jxl");

        System.out.printf("Image: %dx%d, alpha: %s, runs: %d (+%d warmup)%n%n",
                img.getWidth(), img.getHeight(), alpha ? "yes" : "no", runs, WARMUP);

        List<Result> results = new ArrayList<>();

        results.add(measure("PNG (ImageIO)", runs, () -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.size();
        }));

        // PNG8: palette image (<= 256 colors). Lossless with <= 256 unique colors,
        // otherwise median-cut quantization without dithering.
        final BufferedImage indexed = quantize(img);
        System.out.printf("Unique colors: %d -> PNG8 is %s%n%n", uniqueColors,
                uniqueColors <= 256 ? "lossless" : "lossy (median cut, no dithering)");

        results.add(measure("PNG8 encode only", runs, () -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(indexed, "png", baos);
            return baos.size();
        }));

        results.add(measure("PNG8 quant.+encode", runs, () -> {
            BufferedImage q = quantize(img);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(q, "png", baos);
            return baos.size();
        }));

        for (int e : EFFORTS) {
            results.add(measure("JXL e" + e + " lossless", runs, () -> {
                encodeJxl(raw, outJxl, e, 0.0);
                return Files.size(outJxl);
            }));
        }
        // For comparison: visually almost lossless (d=1) at low effort
        results.add(measure("JXL e3 d=1", runs, () -> {
            encodeJxl(raw, outJxl, 3, 1.0);
            return Files.size(outJxl);
        }));

        // WebP via cwebp.exe (external process, like JXL), one thread
        if (Files.exists(CWEBP)) {
            Path outWebp = Files.createTempFile("webpbench_out_", ".webp");
            // cwebp reliably reads PNG, so PNG is used as input (decode time is included in the values)
            Path webpIn = Files.createTempFile("webpbench_in_", ".png");
            ImageIO.write(img, "png", webpIn.toFile());
            for (int z : WEBP_LOSSLESS_LEVELS) {
                results.add(measure("WebP lossless z" + z, runs, () -> {
                    encodeWebp(webpIn, outWebp, "-lossless", "-z", Integer.toString(z));
                    return Files.size(outWebp);
                }));
            }
            results.add(measure("WebP q90 (lossy)", runs, () -> {
                encodeWebp(webpIn, outWebp, "-q", "90");
                return Files.size(outWebp);
            }));
            Files.deleteIfExists(outWebp);
            Files.deleteIfExists(webpIn);
        } else {
            System.out.println("Note: " + CWEBP + " not found, skipping the WebP comparison.\n");
        }

        Result png = results.get(0);
        System.out.printf("%-22s %12s %10s %12s %10s%n",
                "Variant", "Bytes", "vs. PNG", "ms (median)", "vs. PNG");
        for (Result r : results) {
            System.out.printf("%-22s %12d %9.0f%% %12.1f %9.0f%%%n",
                    r.name(), r.bytes(),
                    100.0 * r.bytes() / png.bytes(),
                    r.medianMs(),
                    100.0 * r.medianMs() / png.medianMs());
        }

        Files.deleteIfExists(raw);
        Files.deleteIfExists(outJxl);
    }

    private static Result measure(String name, int runs, Job job) throws Exception {
        long bytes = 0;
        for (int i = 0; i < WARMUP; i++) {
            bytes = job.run();
        }
        double[] ms = new double[runs];
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            bytes = job.run();
            ms[i] = (System.nanoTime() - t0) / 1_000_000.0;
        }
        Arrays.sort(ms);
        double median = runs % 2 == 1 ? ms[runs / 2] : (ms[runs / 2 - 1] + ms[runs / 2]) / 2.0;
        return new Result(name, bytes, median);
    }

    private static void encodeJxl(Path in, Path out, int effort, double distance)
            throws IOException, InterruptedException {
        List<String> cmd = List.of(
                CJXL.toString(), in.toString(), out.toString(),
                "-e", Integer.toString(effort),
                "-d", Double.toString(distance),
                "--num_threads=1");
        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("cjxl timeout");
        }
        if (p.exitValue() != 0) {
            throw new IOException("cjxl failed, exit code " + p.exitValue());
        }
    }

    // ---------------------------------------------------------------------
    // PNG8: simple median-cut quantization (no dithering)
    // ---------------------------------------------------------------------

    private static int uniqueColors;

    /** Channel 0 = alpha, 1 = red, 2 = green, 3 = blue. */
    private static int channel(int c, int ch) {
        return (c >>> (24 - 8 * ch)) & 0xFF;
    }

    private static BufferedImage quantize(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        boolean alpha = img.getColorModel().hasAlpha();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);

        Map<Integer, Integer> counts = new HashMap<>();
        for (int p : px) {
            counts.merge(p, 1, Integer::sum);
        }
        int n = counts.size();
        uniqueColors = n;
        int[] cols = new int[n];
        int[] cnt = new int[n];
        int k = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            cols[k] = e.getKey();
            cnt[k] = e.getValue();
            k++;
        }

        int[] pal;
        if (n <= 256) {
            pal = cols.clone(); // exact palette, lossless
        } else {
            List<int[]> boxes = new ArrayList<>();
            int[] all = new int[n];
            for (int i = 0; i < n; i++) {
                all[i] = i;
            }
            boxes.add(all);

            while (boxes.size() < 256) {
                int best = -1;
                long bestW = -1;
                for (int i = 0; i < boxes.size(); i++) {
                    int[] b = boxes.get(i);
                    if (b.length < 2) {
                        continue;
                    }
                    long wt = 0;
                    for (int idx : b) {
                        wt += cnt[idx];
                    }
                    if (wt > bestW) {
                        bestW = wt;
                        best = i;
                    }
                }
                if (best < 0) {
                    break;
                }
                int[] b = boxes.remove(best);

                int[] mn = {255, 255, 255, 255};
                int[] mx = {0, 0, 0, 0};
                for (int idx : b) {
                    for (int ch = 0; ch < 4; ch++) {
                        int v = channel(cols[idx], ch);
                        if (v < mn[ch]) mn[ch] = v;
                        if (v > mx[ch]) mx[ch] = v;
                    }
                }
                int sc = 0;
                for (int ch = 1; ch < 4; ch++) {
                    if (mx[ch] - mn[ch] > mx[sc] - mn[sc]) {
                        sc = ch;
                    }
                }

                long[] keys = new long[b.length];
                for (int i = 0; i < b.length; i++) {
                    keys[i] = ((long) channel(cols[b[i]], sc) << 32) | b[i];
                }
                Arrays.sort(keys);
                int[] sorted = new int[b.length];
                for (int i = 0; i < b.length; i++) {
                    sorted[i] = (int) (keys[i] & 0xFFFFFFFFL);
                }

                long half = bestW / 2, acc = 0;
                int split = sorted.length - 1;
                for (int i = 0; i < sorted.length - 1; i++) {
                    acc += cnt[sorted[i]];
                    if (acc >= half) {
                        split = i + 1;
                        break;
                    }
                }
                boxes.add(Arrays.copyOfRange(sorted, 0, split));
                boxes.add(Arrays.copyOfRange(sorted, split, sorted.length));
            }

            pal = new int[boxes.size()];
            for (int i = 0; i < pal.length; i++) {
                int[] b = boxes.get(i);
                long[] sum = new long[4];
                long tot = 0;
                for (int idx : b) {
                    tot += cnt[idx];
                    for (int ch = 0; ch < 4; ch++) {
                        sum[ch] += (long) channel(cols[idx], ch) * cnt[idx];
                    }
                }
                int c = 0;
                for (int ch = 0; ch < 4; ch++) {
                    c = (c << 8) | (int) ((sum[ch] + tot / 2) / tot);
                }
                pal[i] = c;
            }
        }

        // Map each unique color to the nearest palette color
        Map<Integer, Integer> lookup = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int c = cols[i];
            int bestI = 0;
            long bestD = Long.MAX_VALUE;
            for (int j = 0; j < pal.length; j++) {
                long d = 0;
                for (int ch = 0; ch < 4; ch++) {
                    int dd = channel(c, ch) - channel(pal[j], ch);
                    d += (long) dd * dd;
                }
                if (d < bestD) {
                    bestD = d;
                    bestI = j;
                    if (d == 0) {
                        break;
                    }
                }
            }
            lookup.put(c, bestI);
        }

        int m = pal.length;
        byte[] r = new byte[m], g = new byte[m], b = new byte[m], a = new byte[m];
        for (int i = 0; i < m; i++) {
            a[i] = (byte) channel(pal[i], 0);
            r[i] = (byte) channel(pal[i], 1);
            g[i] = (byte) channel(pal[i], 2);
            b[i] = (byte) channel(pal[i], 3);
        }
        IndexColorModel icm = alpha
                ? new IndexColorModel(8, m, r, g, b, a)
                : new IndexColorModel(8, m, r, g, b);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, icm);
        byte[] data = ((DataBufferByte) out.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < px.length; i++) {
            data[i] = (byte) (int) lookup.get(px[i]);
        }
        return out;
    }

    private static void encodeWebp(Path in, Path out, String... opts)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(CWEBP.toString());
        cmd.addAll(Arrays.asList(opts));
        cmd.add("-quiet");
        cmd.add(in.toString());
        cmd.add("-o");
        cmd.add(out.toString());
        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("cwebp timeout");
        }
        if (p.exitValue() != 0) {
            throw new IOException("cwebp failed, exit code " + p.exitValue()
                    + "\nCommand: " + cmd + "\nOutput: " + output);
        }
    }

    /** Writes uncompressed pixel data: PAM (RGBA) with alpha, otherwise PPM (RGB). */
    private static void writeRaw(BufferedImage img, Path out, boolean alpha) throws IOException {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        int ch = alpha ? 4 : 3;
        byte[] header = (alpha
                ? "P7\nWIDTH " + w + "\nHEIGHT " + h + "\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n"
                : "P6\n" + w + " " + h + "\n255\n").getBytes(StandardCharsets.US_ASCII);
        byte[] data = new byte[header.length + w * h * ch];
        System.arraycopy(header, 0, data, 0, header.length);
        int o = header.length;
        for (int p : px) {
            data[o++] = (byte) (p >> 16);
            data[o++] = (byte) (p >> 8);
            data[o++] = (byte) p;
            if (alpha) {
                data[o++] = (byte) (p >>> 24);
            }
        }
        Files.write(out, data);
    }
}
