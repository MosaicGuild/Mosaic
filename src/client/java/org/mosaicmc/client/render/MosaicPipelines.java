package org.mosaicmc.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import org.mosaicmc.Mosaic;

/**
 * Custom render pipelines owned by Mosaic.
 */
public final class MosaicPipelines {
    /**
     * GUI rounded-rectangle pipeline. Uses {@code POSITION_TEX_COLOR} vertices where
     * UV encodes the SDF coordinates (see {@link RoundedRectRenderState#buildVertices}).
     */
    public static final RenderPipeline ROUNDED_RECT = RenderPipeline.builder()
            .withLocation(Mosaic.id("pipeline/rounded_rect_gui"))
            .withVertexShader(Mosaic.id("core/rounded_rect"))
            .withFragmentShader(Mosaic.id("core/rounded_rect"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    private MosaicPipelines() {
    }
}
