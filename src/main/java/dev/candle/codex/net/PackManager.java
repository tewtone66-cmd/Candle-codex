package dev.candle.codex.net;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;

/** Enables a freshly downloaded resource pack and reloads resources right away, without a restart. */
public final class PackManager {
    private PackManager() {}

    public static void enableAndReload(String fileName) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            PackRepository repo = mc.getResourcePackRepository();
            repo.reload();
            String id = "file/" + fileName;
            if (!repo.getAvailableIds().contains(id)) return;
            List<String> selected = new ArrayList<>(repo.getSelectedIds());
            boolean already = selected.contains(id);
            if (!already) selected.add(id); // last entry = highest priority
            repo.setSelected(selected);
            mc.options.updateResourcePacks(repo); // reloads resources itself when the selection changed
            if (already) mc.reloadResourcePacks();
        });
    }
}
