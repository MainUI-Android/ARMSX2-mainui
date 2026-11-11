package io.github.mainui.stateapi;

import java.io.File;

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

    @Override
    public String save(int slot) throws Exception {
        int index = index(slot);
        if (!NativeApp.saveStateToSlot(index)) {
            throw new IllegalStateException(NativeApp.isMemcardBusy()
                ? "the memory card is being written: try again in a moment" : "ARMSX2 couldn't save the state");
        }
        String path = NativeApp.getGamePathSlot(index);
        return path != null ? path : "";
    }

    @Override
    public void load(int slot) throws Exception {
        if (!NativeApp.loadStateFromSlot(index(slot))) throw new IllegalStateException("ARMSX2 couldn't load the state");
    }

    @Override
    public long slotTime(int slot) {
        String path = NativeApp.getGamePathSlot(index(slot));
        if (path == null || path.isEmpty()) return 0;
        File f = new File(path);
        return f.isFile() ? f.lastModified() : 0;
    }
}
