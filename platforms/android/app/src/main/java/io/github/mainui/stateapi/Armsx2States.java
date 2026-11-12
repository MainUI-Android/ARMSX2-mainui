package io.github.mainui.stateapi;

import java.io.File;

import com.armsx2.runtime.MainActivityRuntime;

import kr.co.iefriends.pcsx2.NativeApp;

/**
 * ARMSX2's side of the MainUI State API ({@link MainUIStateApi}). MainUI's slots 0-9 are the
 * save state picker's own; MainUI's resume state is slot 99 ("&lt;serial&gt; (&lt;crc&gt;).99.p2s"),
 * which the picker doesn't show. Saving and loading go through the picker's own calls
 * (NativeApp.saveStateToSlot / loadStateFromSlot: VM parked, CPU-thread marshalling, the
 * memory card guard).
 */
public final class Armsx2States implements MainUIStateApi.Emulator {
    public static final Armsx2States INSTANCE = new Armsx2States();

    private static final int SLOTS = 10;
    private static final int RESUME = 99;

    private Armsx2States() {}

    private static int index(int slot) { return slot == MainUIStateApi.RESUME_SLOT ? RESUME : slot; }

    @Override public boolean isRunning() { return NativeApp.mainuiRunning(); }

    @Override public boolean isReady() { return NativeApp.mainuiReady(); }

    @Override public int slotCount() { return SLOTS; }

    // The quick menu's pause: the audio stream stays open (a stopped one is reclaimed by Android
    // and can come back silent). Held, so the stuck-pause backstop doesn't resume it.
    @Override
    public boolean pause() {
        // (The user's own pause, from the second screen, is theirs: MainUI's resume keeps it.)
        if (Boolean.TRUE.equals(MainActivityRuntime.Companion.getUserHeldPause().getValue())) return true;
        MainActivityRuntime.Companion.getUserHeldPause().setValue(true);
        MainActivityRuntime.Companion.pauseForOverlay();
        pausedByMainUi = true;
        return true;
    }

    /** MainUI's pause took effect. */
    private static volatile boolean pausedByMainUi;

    @Override
    public void resume() {
        if (!pausedByMainUi) return;
        pausedByMainUi = false;
        MainActivityRuntime.Companion.resume();
    }

    // Saving and loading park the VM, which pauses the audio stream unless suppressed (as behind
    // the quick menu): a stream paused through a slow save is reclaimed and stays silent.
    @Override
    public String save(int slot) throws Exception {
        int index = index(slot);
        boolean saved;
        NativeApp.setOutputPauseSuppressed(true);
        try {
            saved = NativeApp.saveStateToSlot(index);
        } finally {
            NativeApp.setOutputPauseSuppressed(false);
        }
        if (!saved) {
            throw new IllegalStateException(NativeApp.isMemcardBusy()
                ? "the memory card is being written: try again in a moment" : "ARMSX2 couldn't save the state");
        }
        String path = NativeApp.getGamePathSlot(index);
        return path != null ? path : "";
    }

    @Override
    public void load(int slot) throws Exception {
        boolean loaded;
        NativeApp.setOutputPauseSuppressed(true);
        try {
            loaded = NativeApp.loadStateFromSlot(index(slot));
        } finally {
            NativeApp.setOutputPauseSuppressed(false);
        }
        if (!loaded) throw new IllegalStateException("ARMSX2 couldn't load the state");
    }

    @Override
    public long slotTime(int slot) {
        String path = NativeApp.getGamePathSlot(index(slot));
        if (path == null || path.isEmpty()) return 0;
        File f = new File(path);
        return f.isFile() ? f.lastModified() : 0;
    }
}
