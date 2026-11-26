package nl.oorlogsim.rollback;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import nl.oorlogsim.storage.Region;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Alle aanroepen naar de WorldEdit-API (werkt met FAWE en WorldEdit).
 * Deze klasse wordt alleen geladen als een van beide plugins aanwezig is.
 */
public final class WorldEditBridge {
    public sealed interface Selection permits Selection.Ok, Selection.None, Selection.NotCuboid {
        record Ok(Region region) implements Selection {}
        record None() implements Selection {}
        record NotCuboid() implements Selection {}
    }

    private static final String[] WRITE_ALIASES = {"fast.3", "sponge.3", "fast", "sponge", "schem"};

    public Selection selection(Player player) {
        var session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        com.sk89q.worldedit.regions.Region sel;
        try {
            sel = session.getSelection(BukkitAdapter.adapt(player.getWorld()));
        } catch (IncompleteRegionException e) {
            return new Selection.None();
        }
        if (!(sel instanceof CuboidRegion)) return new Selection.NotCuboid();
        BlockVector3 a = sel.getMinimumPoint(), b = sel.getMaximumPoint();
        return new Selection.Ok(Region.of(player.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ()));
    }

    private static ClipboardFormat writeFormat() {
        for (String alias : WRITE_ALIASES) {
            ClipboardFormat f = ClipboardFormats.findByAlias(alias);
            if (f != null) return f;
        }
        throw new IllegalStateException("Geen schematic-formaat gevonden in WorldEdit/FAWE");
    }

    /** Kopieert het gebied naar een schematic-bestand. Blokkerend. */
    public void save(org.bukkit.World bukkitWorld, Region r, File file) throws Exception {
        var world = BukkitAdapter.adapt(bukkitWorld);
        CuboidRegion region = new CuboidRegion(world, BlockVector3.at(r.minX(), r.minY(), r.minZ()), BlockVector3.at(r.maxX(), r.maxY(), r.maxZ()));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(region.getMinimumPoint());
        try (EditSession session = WorldEdit.getInstance().newEditSession(world)) {
            ForwardExtentCopy copy = new ForwardExtentCopy(session, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(false);
            copy.setCopyingBiomes(false);
            Operations.complete(copy);
        }
        file.getParentFile().mkdirs();
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (ClipboardWriter writer = writeFormat().getWriter(new FileOutputStream(tmp))) {
            writer.write(clipboard);
        }
        if (file.exists() && !file.delete()) throw new IllegalStateException("Kan oud bestand niet vervangen: " + file);
        if (!tmp.renameTo(file)) throw new IllegalStateException("Kan " + tmp + " niet hernoemen");
    }

    /** Plakt een schematic terug op de plek waar hij vandaan kwam. Blokkerend. */
    public void paste(org.bukkit.World bukkitWorld, File file) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IllegalStateException("Onbekend schematic-formaat: " + file);
        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            clipboard = reader.read();
        }
        var world = BukkitAdapter.adapt(bukkitWorld);
        try (EditSession session = WorldEdit.getInstance().newEditSession(world)) {
            Operation op = new ClipboardHolder(clipboard).createPaste(session)
                    .to(clipboard.getOrigin())
                    .ignoreAirBlocks(false)
                    .copyEntities(false)
                    .build();
            Operations.complete(op);
        }
    }
}
