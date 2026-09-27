package com.github.dumann089.theatricalextralights.client.blockentities;

import com.github.dumann089.theatricalextralights.blockentities.PixelBarBlockEntity;
import com.github.dumann089.theatricalextralights.client.Beam2DRenderTypes;
import com.github.dumann089.theatricalextralights.client.ExtraLightsRenderTypes;
import com.github.dumann089.theatricalextralights.client.StrobeVisualEffects;
import com.github.dumann089.theatricalextralights.config.TheatricalExtraLightsConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.imabad.theatrical.client.LazyRenderers;
import dev.imabad.theatrical.config.TheatricalConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Rendu commun des barres LED a pixels (RGB Bar, Vertical RGB Bar, Moving Bar) : par pixel la
 * cellule faiblement eclairee, la LED en point net et additif, une lueur douce tournee vers la
 * camera, et une nappe (volumetrique, ou plate sans le volumetrique). La geometrie des pixels vient
 * de {@link #strip()} ; le placement de la tete de {@code preparePoseStack}, comme pour le modele.
 *
 * <p>Reference : barre LED au sol sur une video de concert, des points brillants dans un ruban de
 * lumiere, et la lumiere d'une barre qui se lit comme un eventail plat dans la haze.
 */
public abstract class PixelBarRenderer<T extends PixelBarBlockEntity> extends ExtraLightsFixtureRenderer<T> {

    /**
     * Geometrie des pixels dans le repere local de la tete, apres {@code preparePoseStack}.
     *
     * @param vertical   pixels le long de Y (barre verticale) au lieu de X
     * @param first      centre du premier pixel le long de l'axe, par rapport au centre de la face
     * @param pitch      pas entre deux pixels
     * @param halfAlong  demi-taille d'une cellule le long de l'axe
     * @param halfAcross demi-taille d'une cellule en travers
     * @param dotHalf    demi-taille du point LED, plus petit que la cellule
     * @param faceX      centre de la face
     * @param faceY      centre de la face
     * @param faceZ      face avant, -Z devant
     */
    protected record Strip(boolean vertical, float first, float pitch, float halfAlong, float halfAcross,
                           float dotHalf, float faceX, float faceY, float faceZ) {
        float along(int pixel) {
            return first + pixel * pitch;
        }

        /** Lueur proportionnelle a la cellule : 0.30 bloc pour une cellule de 4/16. */
        float glowRadius() {
            return halfAlong * 2.4f;
        }

        float x(float along, float across) {
            return vertical ? across : along;
        }

        float y(float along, float across) {
            return vertical ? along : across;
        }
    }

    /**
     * Nappe volumetrique par plage de LED : le moteur trace un cone de rayon max(rayon de base,
     * distance x tan(demi-angle)), etire par un facteur en U et en V. Le rayon de base est la
     * demi-largeur de la plage allumee et le demi-angle est petit : une LED seule fait une ligne
     * fine qui garde sa largeur sur plusieurs blocs, et chaque voisine allumee ajoute exactement la
     * sienne. (Avec un facteur U egal a la largeur de la plage, la divergence aurait ete multipliee
     * elle aussi : neuf LED faisaient un eventail de vingt blocs au plafond.)
     */
    /**
     * Quasi nul : le moteur trace un rayon max(rayon de base, distance x tan), une nappe garde donc
     * sa largeur puis s'ouvre en cone la ou le second terme depasse le premier, avec un coude
     * visible a mi-faisceau. Avec ce demi-angle le coude tombe a plus de cent blocs pour une LED
     * seule et la nappe reste parallele sur toute sa longueur.
     */
    private static final float PIXEL_HALF_ANGLE_DEG = 0.05f;
    /**
     * Intensite de base d'une nappe, multipliee par le reglage « Bar beam » de la config. Une nappe
     * n'a que 0.08 bloc d'epaisseur : un rayon y traverse bien moins de haze que dans un cone rond,
     * d'ou une base bien plus haute que les 8 % de l'ancien eventail.
     */
    private static final float PIXEL_BEAM_INTENSITY = 0.32f;
    /** Demi-epaisseur de la nappe en travers, en blocs : une ligne, pas un volume. */
    private static final float SHEET_HALF_THICKNESS = 0.04f;
    /**
     * Epaisseur minimale d'une nappe a l'ecran, en pixels. Une nappe de 0.08 bloc vue de loin ou de
     * face fait moins d'un pixel : la plupart des rayons la ratent, les autres n'y traversent presque
     * rien, et la ligne scintille puis disparait des qu'on s'eloigne de la barre. L'epaisseur suit
     * donc la distance a la camera, et l'intensite baisse d'autant pour garder la meme luminosite.
     */
    private static final float MIN_SHEET_PIXELS = 3.0f;
    /** Lueur large et douce autour de chaque LED ; les lueurs voisines se rejoignent en un ruban. */
    private static final float DOT_GLOW_STRENGTH = 0.65f;
    /** Bloom serre autour de la LED : un halo vif, a une fraction du rayon de la lueur large. */
    private static final float DOT_BLOOM_RADIUS_SCALE = 0.45f;
    private static final float DOT_BLOOM_STRENGTH = 1.0f;
    /**
     * Coeur de la LED : un point plus petit tire vers le blanc, par-dessus le point colore. Une LED
     * regardee en face sature au centre, la couleur reste sur le bord.
     */
    private static final float DOT_CORE_SCALE = 0.5f;
    private static final float DOT_CORE_WHITEN = 0.6f;
    /** La cellule (lentille) autour de la LED s'eclaire. */
    private static final float CELL_ALPHA = 0.40f;
    /**
     * Faisceau plat : une nappe fine par pixel, a sa couleur, couchee dans l'axe du faisceau et qui
     * s'eteint sur la longueur configuree. Pas de cone.
     */
    private static final float FLAT_BEAM_ALPHA = 0.28f;
    /** Demi-largeur de la nappe plate a son extremite, en multiple de la demi-cellule : 1 = parallele. */
    private static final float FLAT_BEAM_SPREAD = 1.0f;
    /** Demi-epaisseur de la nappe : deux feuilles rapprochees, pour un peu de corps de profil. */
    private static final float FLAT_BEAM_HALF_THICKNESS = 0.015f;

    protected PixelBarRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** Geometrie des pixels de cette barre. */
    protected abstract Strip strip();

    @Override
    public void beforeRenderBeam(T blockEntity, PoseStack poseStack, VertexConsumer vertexConsumer, MultiBufferSource multiBufferSource, Direction facing, float partialTicks, boolean isFlipped, BlockState blockstate, boolean isHanging, int packedLight, int packedOverlay) {
        if (blockEntity.getIntensity() <= 0) {
            return;
        }
        // En mode pixel il n'y a pas de dimmer general : chaque pixel porte le sien (dans son niveau
        // et dans getPixelDimmer), et intensity ne sert qu'a savoir si la barre est allumee.
        boolean pixelMode = blockEntity.isPixelMode();
        float master = pixelMode ? 1f : blockEntity.getIntensity() / 255f;
        boolean volumetric = TheatricalExtraLightsConfig.isVolumetricBeamEnabled();
        float beamGain = TheatricalExtraLightsConfig.getRgbBarBeamIntensity();
        Strip s = strip();
        int count = blockEntity.getPixelCount();

        if (volumetric) {
            // Une plage de LED voisines allumees = une seule nappe, quelles que soient leurs couleurs :
            // couleur moyenne ponderee par le niveau (blanche pour un arc-en-ciel, comme la lumiere
            // melangee d'une vraie barre dans la haze) et niveau moyen. Une LED seule fait une ligne
            // fine, chaque voisine allumee elargit la nappe, une LED eteinte la coupe. Une seule passe
            // de rendu par plage : la courbe de tonalite du shader s'applique a la nappe entiere, au
            // lieu d'additionner neuf nappes deja saturees quand on regarde le faisceau de cote.
            int[] index = {0};
            forEachLitSpan(blockEntity, (start, end, meanColour, meanLevel, colourRuns) ->
                    submitSheet(blockEntity, s, facing, partialTicks, isFlipped, blockstate, isHanging,
                            start, end, meanColour, master * meanLevel / 255f * PIXEL_BEAM_INTENSITY * beamGain,
                            index[0]++));
        }

        LazyRenderers.addLazyRender(new LazyRenderers.LazyRenderer() {
            @Override
            public void render(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, Camera camera, float partialTick) {
                poseStack.pushPose();
                Vec3 offset = Vec3.atLowerCornerOf(blockEntity.getBlockPos()).subtract(camera.getPosition());
                poseStack.translate(offset.x, offset.y, offset.z);
                preparePoseStack(blockEntity, poseStack, facing, partialTick, isFlipped, blockstate, isHanging);

                float intensity = (blockEntity.getPrevIntensity() + ((blockEntity.getIntensity()) - blockEntity.getPrevIntensity()) * partialTick);
                float alpha = pixelMode ? 1f : intensity / 255f;

                poseStack.translate(s.faceX(), s.faceY(), s.faceZ());
                Matrix4f m = poseStack.last().pose();
                Matrix3f normal = poseStack.last().normal();
                float hx = s.x(s.halfAlong(), s.halfAcross());
                float hy = s.y(s.halfAlong(), s.halfAcross());

                // Les deux render types partagent le tampon de repli du BufferSource : une passe
                // par type, sinon les quads d'une passe partent dans le lot de l'autre.
                VertexConsumer builder = bufferSource.getBuffer(Beam2DRenderTypes.getBeam());
                for (int i = 0; i < count; i++) {
                    int colour = blockEntity.getPixelColour(i);
                    if (blockEntity.getPixelLevel(i) <= 0) continue;
                    int r = (colour >> 16) & 0xFF;
                    int g = (colour >> 8) & 0xFF;
                    int b = colour & 0xFF;
                    float cx = s.x(s.along(i), 0f);
                    float cy = s.y(s.along(i), 0f);
                    int cellA = (int) (alpha * blockEntity.getPixelDimmer(i) / 255f * CELL_ALPHA * 255);
                    addVertex(builder, m, normal, r, g, b, cellA, cx - hx, cy + hy, 0f);
                    addVertex(builder, m, normal, r, g, b, cellA, cx + hx, cy + hy, 0f);
                    addVertex(builder, m, normal, r, g, b, cellA, cx + hx, cy - hy, 0f);
                    addVertex(builder, m, normal, r, g, b, cellA, cx - hx, cy - hy, 0f);
                }
                VertexConsumer dots = bufferSource.getBuffer(ExtraLightsRenderTypes.GLOW);
                float d = s.dotHalf();
                for (int i = 0; i < count; i++) {
                    int colour = blockEntity.getPixelColour(i);
                    if (blockEntity.getPixelLevel(i) <= 0) continue;
                    int r = (colour >> 16) & 0xFF;
                    int g = (colour >> 8) & 0xFF;
                    int b = colour & 0xFF;
                    float cx = s.x(s.along(i), 0f);
                    float cy = s.y(s.along(i), 0f);
                    int dotA = (int) (alpha * blockEntity.getPixelDimmer(i));
                    dots.vertex(m, cx - d, cy + d, -0.002f).color(r, g, b, dotA).endVertex();
                    dots.vertex(m, cx + d, cy + d, -0.002f).color(r, g, b, dotA).endVertex();
                    dots.vertex(m, cx + d, cy - d, -0.002f).color(r, g, b, dotA).endVertex();
                    dots.vertex(m, cx - d, cy - d, -0.002f).color(r, g, b, dotA).endVertex();
                    // Coeur surexpose, additif par-dessus le point.
                    int cr = r + (int) ((255 - r) * DOT_CORE_WHITEN);
                    int cg = g + (int) ((255 - g) * DOT_CORE_WHITEN);
                    int cb = b + (int) ((255 - b) * DOT_CORE_WHITEN);
                    float c = d * DOT_CORE_SCALE;
                    dots.vertex(m, cx - c, cy + c, -0.003f).color(cr, cg, cb, dotA).endVertex();
                    dots.vertex(m, cx + c, cy + c, -0.003f).color(cr, cg, cb, dotA).endVertex();
                    dots.vertex(m, cx + c, cy - c, -0.003f).color(cr, cg, cb, dotA).endVertex();
                    dots.vertex(m, cx - c, cy - c, -0.003f).color(cr, cg, cb, dotA).endVertex();
                }
                poseStack.popPose();

                // Lueurs : positions des LED dans le repere camera (sans la vue), disques dessines dans la vue.
                PoseStack world = new PoseStack();
                world.translate(offset.x, offset.y, offset.z);
                preparePoseStack(blockEntity, world, facing, partialTick, isFlipped, blockstate, isHanging);
                world.translate(s.faceX(), s.faceY(), s.faceZ() - 0.01f);
                Matrix4f wm = world.last().pose();
                for (int i = 0; i < count; i++) {
                    int colour = blockEntity.getPixelColour(i);
                    int level = blockEntity.getPixelLevel(i);
                    if (level <= 0) continue;
                    Vector4f c = wm.transform(new Vector4f(s.x(s.along(i), 0f), s.y(s.along(i), 0f), 0f, 1f));
                    Vector3f centre = new Vector3f(c.x, c.y, c.z);
                    // Deux disques : un bloom serre et vif contre la LED, puis la lueur large et douce.
                    StrobeVisualEffects.renderGlowDot(bufferSource, poseStack, camera, centre, colour,
                            s.glowRadius() * DOT_BLOOM_RADIUS_SCALE, alpha * level / 255f * DOT_BLOOM_STRENGTH);
                    StrobeVisualEffects.renderGlowDot(bufferSource, poseStack, camera, centre, colour,
                            s.glowRadius(), alpha * level / 255f * DOT_GLOW_STRENGTH);
                }

                if (!volumetric) {
                    // Sans volumetrique : nappes plates 2D, sans haze.
                    poseStack.pushPose();
                    poseStack.translate(offset.x, offset.y, offset.z);
                    preparePoseStack(blockEntity, poseStack, facing, partialTick, isFlipped, blockstate, isHanging);
                    poseStack.translate(s.faceX(), s.faceY(), s.faceZ());
                    renderFlatBeams(bufferSource, poseStack, blockEntity, s, alpha * beamGain);
                    poseStack.popPose();
                }
            }

            @Override
            public Vec3 getPos(float partialTick) {
                return blockEntity.getBlockPos().getCenter();
            }
        });
    }

    /** Nappes plates : une par plage de LED voisines allumees, couleur moyenne, alpha au depart, zero au bout. */
    private void renderFlatBeams(MultiBufferSource.BufferSource bufferSource, PoseStack stack,
                                 T blockEntity, Strip s, float alpha) {
        float length = TheatricalExtraLightsConfig.getRgbBarBeamLength();
        float opacity = (float) TheatricalConfig.INSTANCE.CLIENT.beamOpacity;
        VertexConsumer vc = bufferSource.getBuffer(Beam2DRenderTypes.getBeam());
        Matrix4f m = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        forEachLitSpan(blockEntity, (start, end, meanColour, meanLevel, colourRuns) -> {
            int colour = normalise(meanColour);
            int r = (colour >> 16) & 0xFF;
            int g = (colour >> 8) & 0xFF;
            int b = colour & 0xFF;
            int a = Math.min(255, (int) (alpha * meanLevel / 255f * FLAT_BEAM_ALPHA * opacity * 255));
            if (a <= 0) return;
            float c = (s.along(start) + s.along(end)) * 0.5f;
            float w0 = (s.along(end) - s.along(start)) * 0.5f + s.halfAlong();
            // La divergence est celle d'une cellule, quelle que soit la largeur de la plage.
            float w1 = w0 + s.halfAlong() * (FLAT_BEAM_SPREAD - 1f);
            for (float t : new float[]{FLAT_BEAM_HALF_THICKNESS, -FLAT_BEAM_HALF_THICKNESS}) {
                addVertex(vc, m, normal, r, g, b, a, s.x(c - w0, t), s.y(c - w0, t), 0f);
                addVertex(vc, m, normal, r, g, b, a, s.x(c + w0, t), s.y(c + w0, t), 0f);
                addVertex(vc, m, normal, r, g, b, 0, s.x(c + w1, t), s.y(c + w1, t), -length);
                addVertex(vc, m, normal, r, g, b, 0, s.x(c - w1, t), s.y(c - w1, t), -length);
            }
        });
    }

    /** Une nappe volumetrique couvrant les pixels {@code start} a {@code end}, dans la couleur donnee. */
    private void submitSheet(T blockEntity, Strip s, Direction facing, float partialTicks, boolean isFlipped,
                             BlockState blockstate, boolean isHanging, int start, int end, int colour,
                             float intensity, int beamIndex) {
        float centre = (s.along(start) + s.along(end)) * 0.5f;
        float halfRun = (s.along(end) - s.along(start)) * 0.5f + s.halfAlong();
        PoseStack beamPose = new PoseStack();
        preparePoseStack(blockEntity, beamPose, facing, partialTicks, isFlipped, blockstate, isHanging);
        beamPose.translate(s.faceX() + s.x(centre, 0f), s.faceY() + s.y(centre, 0f), s.faceZ());
        // Rayon de base = demi-largeur de la plage ; en travers, une epaisseur fixe, au moins
        // quelques pixels a l'ecran.
        float half = sheetHalfThickness(blockEntity);
        float thin = half / halfRun;
        float widthScale = s.vertical() ? thin : 1f;
        float heightScale = s.vertical() ? 1f : thin;
        // Couleur pleine, la luminosite passe dans l'intensite ; une nappe epaissie pour l'ecran
        // garde la meme lumiere, donc une densite moindre.
        float gain = SHEET_HALF_THICKNESS / half;
        submitVolumetricBeam(blockEntity, beamPose, partialTicks, PIXEL_HALF_ANGLE_DEG, PIXEL_HALF_ANGLE_DEG,
                null, 0, 0f, widthScale, heightScale, beamIndex, normalise(colour), intensity * gain, halfRun);
    }

    /** Demi-epaisseur de la nappe pour cette image : la valeur physique, ou ce que couvrent MIN_SHEET_PIXELS. */
    private static float sheetHalfThickness(PixelBarBlockEntity blockEntity) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double distance = cam.distanceTo(blockEntity.getBlockPos().getCenter());
        double fov = Math.toRadians(mc.options.fov().get());
        double blocksPerPixel = 2.0 * distance * Math.tan(fov * 0.5) / Math.max(1, mc.getWindow().getHeight());
        return (float) Math.max(SHEET_HALF_THICKNESS, blocksPerPixel * MIN_SHEET_PIXELS * 0.5);
    }

    /** Une plage de LED voisines allumees, toutes couleurs confondues. */
    @FunctionalInterface
    protected interface LitSpan {
        /**
         * @param meanColour couleur brute moyenne, ponderee par le niveau de chaque LED
         * @param colourRuns nombre de suites de couleur dans la plage (1 = plage unie)
         */
        void accept(int start, int end, int meanColour, float meanLevel, int colourRuns);
    }

    /** Parcourt les plages de LED voisines allumees, sans distinction de couleur. */
    protected static void forEachLitSpan(PixelBarBlockEntity blockEntity, LitSpan consumer) {
        int count = blockEntity.getPixelCount();
        int start = 0;
        while (start < count) {
            if (blockEntity.getPixelLevel(start) <= 0) {
                start++;
                continue;
            }
            int end = start;
            while (end + 1 < count && blockEntity.getPixelLevel(end + 1) > 0) {
                end++;
            }
            long r = 0, g = 0, b = 0, weight = 0;
            int colourRuns = 0;
            int last = -1;
            for (int i = start; i <= end; i++) {
                int colour = blockEntity.getPixelColour(i);
                int level = blockEntity.getPixelLevel(i);
                r += (long) ((colour >> 16) & 0xFF) * level;
                g += (long) ((colour >> 8) & 0xFF) * level;
                b += (long) (colour & 0xFF) * level;
                weight += level;
                if (colour != last) {
                    colourRuns++;
                    last = colour;
                }
            }
            int mean = weight > 0
                    ? ((int) (r / weight) << 16) | ((int) (g / weight) << 8) | (int) (b / weight)
                    : 0;
            consumer.accept(start, end, mean, weight / (float) (end - start + 1), colourRuns);
            start = end + 1;
        }
    }

    /** Ramene la composante la plus forte a 255 : la couleur reste saturee, la luminosite va dans l'intensite. */
    private static int normalise(int colour) {
        int r = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max <= 0) return 0;
        return (r * 255 / max << 16) | (g * 255 / max << 8) | (b * 255 / max);
    }

    @Override
    protected void addVertex(VertexConsumer builder, Matrix4f m, Matrix3f nm,
                             int r, int g, int b, int a,
                             float x, float y, float z) {
        if (Beam2DRenderTypes.isShadersActive()) {
            builder.vertex(m, x, y, z)
                    .color(r, g, b, a)
                    .uv(0f, 0f)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .endVertex();
        } else {
            super.addVertex(builder, m, nm, r, g, b, a, x, y, z);
        }
    }
}
