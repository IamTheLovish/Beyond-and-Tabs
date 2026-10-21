package com.solegendary.reignofnether.unit.modelling.mesh;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * A renderer's model on a mesh body: built once from the body's tree by the same constructor the cube model uses
 * (e.g. {@code RadiantPaladinUnitModel::new}), rebuilt after a resource reload, and null while the body has no .bmesh -
 * the renderer then keeps its cube model (MeshBody has logged why). One model per renderer, like the cube model.
 */
@OnlyIn(Dist.CLIENT)
public final class MeshModel<M> {
    private final ResourceLocation body;
    private final int lod;
    private final Function<ModelPart, M> factory;
    private M model;
    private int builtFor = -1;   // MeshBody.generation() the model was built for

    public MeshModel(ResourceLocation body, int lod, Function<ModelPart, M> factory) {
        this.body = body;
        this.lod = lod;
        this.factory = factory;
    }

    @Nullable
    public M get() {
        int generation = MeshBody.generation();
        if (generation != builtFor) {
            MeshBody b = MeshBody.get(body);
            model = b == null ? null : factory.apply(b.buildTree(lod));
            builtFor = generation;
        }
        return model;
    }
}
