import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 
 * This code was generated using AI and manually reviewed.
 * 
 * Example: PNG -> JPEG XL via cjxl.exe from the libjxl release.
 *
 * Compile: javac JxlEncodeExample.java
 * Run:     java JxlEncodeExample tile.png tile.jxl
 */
public class JxlEncodeExample {

    // Path to cjxl.exe from the libjxl release
    private static final Path CJXL = Paths.get(
            "C:\\Users\\marti\\Desktop\\geoserver_jpegxl\\libjxl_0_12_0\\cjxl.exe");

    /**
     * @param input    input image (PNG, JPG, ...)
     * @param output   target file (.jxl)
     * @param effort   1 (fast) to 9 (slow, smallest file); 3-4 for on-the-fly encoding
     * @param distance 0 = lossless, 1 = visually almost lossless, higher = smaller/worse
     * @param threads  number of threads per encode (keep small with many parallel requests)
     */
    public static void encode(Path input, Path output, int effort, double distance, int threads)
            throws IOException, InterruptedException {

        List<String> cmd = new ArrayList<>();
        cmd.add(CJXL.toString());
        cmd.add(input.toString());
        cmd.add(output.toString());
        cmd.add("-e");
        cmd.add(Integer.toString(effort));
        cmd.add("-d");
        cmd.add(Double.toString(distance));
        cmd.add("--num_threads=" + threads);

        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();

        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("cjxl timeout");
        }
        if (p.exitValue() != 0) {
            throw new IOException("cjxl failed, exit code " + p.exitValue());
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java JxlEncodeExample <input.png> <output.jxl>");
            return;
        }
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);

        long t0 = System.nanoTime();
        encode(in, out, 3, 0.0, 1); // lossless, low effort, 1 thread
        long ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("%s: %d -> %d bytes in %d ms%n",
                in.getFileName(), Files.size(in), Files.size(out), ms);
    }
}
