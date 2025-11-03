package sh.tinywifi.canvasglsl.media;

import java.nio.file.Path;

public final class MediaEntry {
    private final Path descriptorFile;
    private final Path sourcePath;
    private final MediaType mediaType;

    public MediaEntry(Path descriptorFile, Path sourcePath, MediaType mediaType) {
        this.descriptorFile = descriptorFile;
        this.sourcePath = sourcePath;
        this.mediaType = mediaType;
    }

    public Path descriptorFile() {
        return descriptorFile;
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public MediaType mediaType() {
        return mediaType;
    }
}
