package sh.tinywifi.canvasglsl.ide;

import sh.tinywifi.canvasglsl.media.MediaEntry;

@FunctionalInterface
public interface MediaChangeListener {
    void onMediaSelected(MediaEntry entry);
}
