// Re-encodes every post banner under a media directory to WebP using the app's
// BannerImageProcessor, so batch output is identical to a live upload (EXIF
// auto-rotation, max-width downscale, quality). Self-contained: walks the tree
// itself so it can run either from the host wrapper or inside a one-off container.
//
// Reads env vars:
//   MEDIA_DIR  (required) root media directory; each subdirectory is one post.
//   DRY_RUN    (optional) "1"/"true" -> only report what would change.
//
// For each post dir it finds the first file named "banner*", writes
// <dir>/banner.webp, removes the original if it had a different name, and prints
// a tab-separated result line: OK/ERR/DRY, path, in_size, out_size.

var mediaDir = System.getenv("MEDIA_DIR");
var dryRun = "1".equals(System.getenv("DRY_RUN")) || "true".equalsIgnoreCase(System.getenv("DRY_RUN"));

if (mediaDir == null || mediaDir.isBlank()) {
    System.out.println("ERR\t(none)\tMEDIA_DIR env var not set\t0");
} else {
    try (var posts = Files.list(Path.of(mediaDir))) {
        for (var postDir : posts.filter(Files::isDirectory).sorted().toList()) {
            Path banner;
            try (var files = Files.list(postDir)) {
                banner = files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("banner"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
            }
            if (banner == null) continue;

            try {
                var inSize = Files.size(banner);
                if (dryRun) {
                    System.out.println("DRY\t" + banner + "\t" + inSize + "\t0");
                    continue;
                }
                var bytes = Files.readAllBytes(banner);
                var out = com.flow2.repository.media.BannerImageProcessor.INSTANCE.process(bytes);
                var outPath = banner.getParent().resolve("banner.webp");
                Files.write(outPath, out);
                if (!banner.getFileName().toString().equals("banner.webp")) {
                    Files.deleteIfExists(banner);
                }
                System.out.println("OK\t" + banner + "\t" + inSize + "\t" + out.length);
            } catch (Exception e) {
                System.out.println("ERR\t" + banner + "\t" + e.getMessage() + "\t0");
            }
        }
    }
}
/exit
