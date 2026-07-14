// Re-encodes a list of banner files to WebP using the app's BannerImageProcessor,
// so batch output is identical to what a live upload produces (EXIF auto-rotation,
// max-width downscale, quality). Driven by reprocess-banners.sh.
//
// Reads:
//   LIST_FILE env var -> path to a file containing one input banner path per line.
// For each input it writes <dir>/banner.webp, removes the original if it had a
// different name, and prints a tab-separated result line: OK/ERR, path, sizes.

var listFile = System.getenv("LIST_FILE");
for (var line : Files.readAllLines(Path.of(listFile))) {
    if (line.isBlank()) continue;
    try {
        var in = Path.of(line.trim());
        var bytes = Files.readAllBytes(in);
        var out = com.flow2.repository.media.BannerImageProcessor.INSTANCE.process(bytes);
        var outPath = in.getParent().resolve("banner.webp");
        Files.write(outPath, out);
        if (!in.getFileName().toString().equals("banner.webp")) {
            Files.deleteIfExists(in);
        }
        System.out.println("OK\t" + in + "\t" + bytes.length + "\t" + out.length);
    } catch (Exception e) {
        System.out.println("ERR\t" + line + "\t" + e.getMessage());
    }
}
/exit
