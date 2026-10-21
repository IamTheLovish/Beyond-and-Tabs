package com.solegendary.reignofnether.unit.modelling.mesh;

import com.mojang.logging.LogUtils;
import com.solegendary.reignofnether.ReignOfNether;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A unit body as one triangle mesh per part - the .bmesh that tools/mesh_export.py writes (the format is in its
 * docstring) - and the ModelPart tree built from it (mesh bodies, inbox/spec-1-mesh-bodies-2026-10-17.md).
 *
 * buildTree(lod) returns a root whose parts carry the same names, parents, pivots and rest rotations as the unit's
 * generated cube model (step 1 exports exactly that tree; a sculpted body will export its DSL bones), so the generated
 * class - bones, lodParts, upgradeParts, SplitPasses' team and glow parts - and its hand-written animation subclass
 * take the mesh root unchanged: {@code new RadiantPaladinUnitModel(body.buildTree(0))}.
 *
 * Children are put in the order vanilla's PartDefinition.bake gives them (its children live in a HashMap filled in
 * source order, copied into an array map): parts draw in that order, and where two faces lie in one plane (a trim on
 * a tabard) the later one wins the depth test - the mesh body must draw in the cube body's order to look the same.
 *
 * Bodies load with the resources (Loader, a client reload listener - F3+T reloads them). A missing or broken file is
 * logged and its unit keeps its cube body (MeshModel.get returns null) - no silent failure, no invisible unit.
 */
@OnlyIn(Dist.CLIENT)
public final class MeshBody {
    /** The newest format read (tools/mesh_import.py writes it); version 1 (tools/mesh_export.py) still reads. */
    public static final int VERSION = 2;
    /** LOD flag bit (version 2): every vertex carries an RGBA colour after its uv. */
    static final int FLAG_COLOURS = 1;
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * One level of detail of one part: its mesh and bounds (model px, part-local), and - from a sculpt's bake - a
     * colour per vertex (r g b a, 0..1) that MeshCube multiplies into the draw colour, or null (drawn as version 1).
     */
    public static final class Lod {
        final float[] pos, nrm, uv;
        @Nullable final float[] col;
        final int[] quads;
        public final float minX, minY, minZ, maxX, maxY, maxZ;

        Lod(float[] pos, float[] nrm, float[] uv, int[] quads, float[] bounds) {
            this(pos, nrm, uv, null, quads, bounds);
        }

        Lod(float[] pos, float[] nrm, float[] uv, @Nullable float[] col, int[] quads, float[] bounds) {
            this.pos = pos;
            this.nrm = nrm;
            this.uv = uv;
            this.col = col;
            this.quads = quads;
            this.minX = bounds[0];
            this.minY = bounds[1];
            this.minZ = bounds[2];
            this.maxX = bounds[3];
            this.maxY = bounds[4];
            this.maxZ = bounds[5];
        }

        public int vertexCount() {
            return pos.length / 3;
        }

        public int quadCount() {
            return quads.length / 4;
        }

        public boolean hasColours() {
            return col != null;
        }
    }

    /** One part: where it hangs and its rest pose (PartPose: offset from the parent's pivot in model px, radians). */
    public static final class Part {
        public final String name;
        public final int parent;   // an earlier part's index, or -1 for a child of the root
        public final float x, y, z, xRot, yRot, zRot;
        final Lod[] lods;

        Part(String name, int parent, float[] pose, Lod[] lods) {
            this.name = name;
            this.parent = parent;
            this.x = pose[0];
            this.y = pose[1];
            this.z = pose[2];
            this.xRot = pose[3];
            this.yRot = pose[4];
            this.zRot = pose[5];
            this.lods = lods;
        }

        public int lodCount() {
            return lods.length;
        }

        /** The LOD, or the coarsest one this part has (a sculpt's small parts may stop at LOD1). */
        public Lod lod(int i) {
            return lods[Math.max(0, Math.min(i, lods.length - 1))];
        }
    }

    public final int atlasWidth, atlasHeight;
    private final Part[] parts;   // pre-order: a parent before its children, children in source order

    private MeshBody(int atlasWidth, int atlasHeight, Part[] parts) {
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        this.parts = parts;
    }

    public int partCount() {
        return parts.length;
    }

    public Part part(int i) {
        return parts[i];
    }

    // ---------------------------------------------------------------------------------------------------- reading --

    public static MeshBody read(InputStream in) throws IOException {
        return read(in.readAllBytes());
    }

    /**
     * Parses a whole .bmesh, version 1 or 2 (2 adds a flags byte before each LOD's vertices: bit 0, an RGBA byte
     * colour after every vertex's uv); anything malformed is an IOException that says what and where (never a half
     * body).
     */
    public static MeshBody read(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        try {
            if (b.get() != 'B' || b.get() != 'M' || b.get() != 'S' || b.get() != 'H')
                throw new IOException("not a .bmesh (no BMSH magic)");
            int version = u16(b);
            if (version < 1 || version > VERSION)
                throw new IOException(".bmesh version " + version + ", this game reads 1 to " + VERSION);
            int atlasW = u16(b), atlasH = u16(b), count = u16(b);
            Part[] parts = new Part[count];
            List<Set<String>> childNames = new ArrayList<>();
            Set<String> rootNames = new HashSet<>();
            for (int i = 0; i < count; i++) {
                byte[] nameBytes = new byte[b.get() & 0xFF];
                b.get(nameBytes);
                String name = new String(nameBytes, StandardCharsets.UTF_8);
                int parent = b.getShort();
                if (parent < -1 || parent >= i)
                    throw new IOException("part " + i + " '" + name + "': parent " + parent + " is not an earlier part");
                if (!(parent < 0 ? rootNames : childNames.get(parent)).add(name))
                    throw new IOException("part " + i + " '" + name + "': its parent already has a child of that name");
                childNames.add(new HashSet<>());
                float[] pose = floats(b, 6);
                int lodCount = b.get() & 0xFF;
                if (lodCount == 0)
                    throw new IOException("part '" + name + "' has no LOD");
                Lod[] lods = new Lod[lodCount];
                for (int l = 0; l < lodCount; l++) {
                    int flags = version >= 2 ? b.get() & 0xFF : 0;
                    if ((flags & ~FLAG_COLOURS) != 0)
                        throw new IOException("part '" + name + "' LOD " + l + ": unknown flags " + flags);
                    boolean colours = (flags & FLAG_COLOURS) != 0;
                    int vertices = b.getInt();
                    if (vertices < 0 || (long) vertices * (colours ? 36 : 32) > b.remaining())
                        throw new IOException("part '" + name + "' LOD " + l + ": " + Integer.toUnsignedString(vertices)
                                + " vertices, more than the file holds");
                    float[] pos = new float[vertices * 3], nrm = new float[vertices * 3], uv = new float[vertices * 2];
                    float[] col = colours ? new float[vertices * 4] : null;
                    for (int v = 0; v < vertices; v++) {
                        pos[v * 3] = b.getFloat();
                        pos[v * 3 + 1] = b.getFloat();
                        pos[v * 3 + 2] = b.getFloat();
                        nrm[v * 3] = b.getFloat();
                        nrm[v * 3 + 1] = b.getFloat();
                        nrm[v * 3 + 2] = b.getFloat();
                        uv[v * 2] = b.getFloat();
                        uv[v * 2 + 1] = b.getFloat();
                        if (col != null)
                            for (int c = 0; c < 4; c++)
                                col[v * 4 + c] = (b.get() & 0xFF) / 255.0F;
                    }
                    int faces = b.getInt();
                    if (faces < 0 || (long) faces * 16 > b.remaining())
                        throw new IOException("part '" + name + "' LOD " + l + ": " + Integer.toUnsignedString(faces)
                                + " quads, more than the file holds");
                    int[] quads = new int[faces * 4];
                    for (int q = 0; q < quads.length; q++) {
                        int index = b.getInt();
                        if (index < 0 || index >= vertices)
                            throw new IOException("part '" + name + "' LOD " + l + ": quad corner " + q + " is vertex "
                                    + Integer.toUnsignedString(index) + " of " + vertices);
                        quads[q] = index;
                    }
                    lods[l] = new Lod(pos, nrm, uv, col, quads, floats(b, 6));
                }
                parts[i] = new Part(name, parent, pose, lods);
            }
            if (b.hasRemaining())
                throw new IOException(b.remaining() + " bytes after the last part");
            return new MeshBody(atlasW, atlasH, parts);
        } catch (BufferUnderflowException e) {
            throw new IOException("the file ends early (truncated .bmesh)", e);
        }
    }

    private static int u16(ByteBuffer b) {
        return b.getShort() & 0xFFFF;
    }

    private static float[] floats(ByteBuffer b, int n) {
        float[] out = new float[n];
        for (int i = 0; i < n; i++)
            out[i] = b.getFloat();
        return out;
    }

    // ------------------------------------------------------------------------------------------------- the tree --

    /**
     * A fresh ModelPart tree of this body at the given LOD (clamped per part to what it has): one part per body part,
     * its mesh as one MeshCube (none for a part without vertices - as vanilla, an empty cube list), its rest pose as
     * the initial pose (resetPose / restPose return to it).
     */
    public ModelPart buildTree(int lod) {
        List<List<Integer>> kids = new ArrayList<>(parts.length + 1);
        for (int i = 0; i <= parts.length; i++)
            kids.add(new ArrayList<>());
        for (int i = 0; i < parts.length; i++)
            kids.get(parts[i].parent < 0 ? parts.length : parts[i].parent).add(i);
        ModelPart[] built = new ModelPart[parts.length];
        for (int i = parts.length - 1; i >= 0; i--) {   // pre-order: every child has a higher index than its parent
            Part p = parts[i];
            Lod l = p.lod(lod);
            List<ModelPart.Cube> cubes = l.vertexCount() == 0 ? List.of() : List.of(new MeshCube(l));
            ModelPart part = new ModelPart(cubes, children(kids.get(i), built));
            PartPose pose = PartPose.offsetAndRotation(p.x, p.y, p.z, p.xRot, p.yRot, p.zRot);
            part.setInitialPose(pose);
            part.loadPose(pose);
            built[i] = part;
        }
        ModelPart root = new ModelPart(List.of(), children(kids.get(parts.length), built));
        root.setInitialPose(PartPose.ZERO);
        root.loadPose(PartPose.ZERO);
        return root;
    }

    /**
     * Vanilla's child order: PartDefinition keeps its children in a HashMap (Maps.newHashMap) filled in source order,
     * and bake() copies that map's entries, in its iteration order, into an Object2ObjectArrayMap. The same keys put
     * into a default HashMap in the same order iterate the same way.
     */
    private Map<String, ModelPart> children(List<Integer> kids, ModelPart[] built) {
        HashMap<String, ModelPart> source = new HashMap<>();
        for (int k : kids)
            source.put(parts[k].name, built[k]);
        Object2ObjectArrayMap<String, ModelPart> out = new Object2ObjectArrayMap<>(source.size());
        for (Map.Entry<String, ModelPart> e : source.entrySet())
            out.put(e.getKey(), e.getValue());
        return out;
    }

    // ---------------------------------------------------------------------------------- loaded bodies (client) --

    private static volatile Map<ResourceLocation, MeshBody> bodies = Map.of();
    private static volatile int generation = 0;   // reloads applied; 0 = the resources have not loaded yet
    private static final Set<ResourceLocation> REPORTED = ConcurrentHashMap.newKeySet();

    /** {@code reignofnether:meshes/<name>.bmesh} - a unit's body by its texture name (radiant_paladin_unit). */
    public static ResourceLocation location(String name) {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "meshes/" + name + ".bmesh");
    }

    /** The loaded body, or null when its file is missing or was refused (logged once per reload). */
    @Nullable
    public static MeshBody get(ResourceLocation location) {
        MeshBody body = bodies.get(location);
        if (body == null && generation > 0 && REPORTED.add(location))
            LOGGER.warn("[Mesh] no mesh body {} - its unit keeps its cube body (tools/mesh_export.py writes it; CI runs it "
                    + "before the build)", location);
        return body;
    }

    /** Changes whenever the bodies reload: a model built on an older body is rebuilt (MeshModel). */
    public static int generation() {
        return generation;
    }

    /** Reads every meshes/*.bmesh off the render thread; swaps them in on it. */
    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, MeshBody>> {
        @Override
        protected Map<ResourceLocation, MeshBody> prepare(ResourceManager resources, ProfilerFiller profiler) {
            Map<ResourceLocation, MeshBody> out = new HashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : resources.listResources("meshes",
                    location -> location.getPath().endsWith(".bmesh")).entrySet()) {
                try (InputStream in = e.getValue().open()) {
                    out.put(e.getKey(), read(in));
                } catch (IOException | RuntimeException ex) {
                    LOGGER.error("[Mesh] {} refused - its unit keeps its cube body: {}", e.getKey(), ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, MeshBody> loaded, ResourceManager resources, ProfilerFiller profiler) {
            bodies = Map.copyOf(loaded);
            REPORTED.clear();
            generation++;
            Map<String, String> summary = new TreeMap<>();
            loaded.forEach((k, v) -> summary.put(k.getPath(), v.partCount() + " parts"));
            LOGGER.info("[Mesh] {} mesh bodies loaded: {}", loaded.size(), summary);
        }
    }

    @Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() { }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent evt) {
            evt.registerReloadListener(new Loader());
        }
    }
}
