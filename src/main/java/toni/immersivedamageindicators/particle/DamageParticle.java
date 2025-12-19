package toni.immersivedamageindicators.particle;

import net.fabricmc.fabric.impl.client.indigo.renderer.helper.ColorHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.*;
import toni.immersivedamageindicators.api.CreateDamageIndicator;
import toni.immersivedamageindicators.foundation.CaxtonRenderer;
import toni.immersivedamageindicators.foundation.IrisCompat;
import toni.immersivedamageindicators.foundation.ParticleRegistry;
import toni.immersivedamageindicators.foundation.config.AllConfigs;
import toni.immersivemessages.ImmersiveFont;
import toni.immersivemessages.ImmersiveMessagesManager;
import toni.immersivemessages.api.ImmersiveMessage;
import toni.immersivemessages.api.OnRenderMessage;
import toni.immersivemessages.api.TextAnchor;
import toni.immersivemessages.util.AnimationUtil;
import toni.immersivemessages.util.RenderUtil;
import toni.lib.animation.AnimationKeyframe;
import toni.lib.animation.AnimationTimeline;
import toni.lib.animation.Binding;
import toni.lib.animation.PoseUtils;
import toni.lib.animation.easing.EasingType;
import toni.lib.utils.ColorUtils;
import toni.lib.utils.PlatformUtils;

import java.lang.Math;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

#if forge
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import xyz.flirora.caxton.layout.CaxtonText;
import xyz.flirora.caxton.render.CaxtonTextRenderer;
#elif neo
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import toni.lib.utils.VersionUtils;
#endif

// Credits https://github.com/MehVahdJukaar
// This class is from the Target Dummy mod
// https://github.com/MehVahdJukaar/DuMmmMmmy/blob/1.20/common/src/main/java/net/mehvahdjukaar/dummmmmmy/client/DamageNumberParticle.java
public class DamageParticle extends Particle {

    private static final List<Float> POSITIONS = new ArrayList<>(Arrays.asList(0f, -0.25f, 0.12f, -0.12f, 0.25f));
    private static final DecimalFormat DF2 = new DecimalFormat("#.##");
    private static final DecimalFormat DF1 = new DecimalFormat("#.#");

    private final Font fontRenderer = Minecraft.getInstance().font;

    public final ImmersiveMessage message;
    public float fadeout = -1;
    public float prevFadeout = -1;

    public float visualDY = 0;
    public float prevVisualDY = 0;
    public float visualDX = 0;
    public float prevVisualDX = 0;
    public int opacity;

    private static final java.util.Set<DamageParticle> ACTIVE_PARTICLES = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    
    private float fadePenalty = 0f;
    private float targetFadePenalty = 0f;

    public DamageParticle(ClientLevel clientLevel, double x, double y, double z, double amount, double dColor, double dz) {
        super(clientLevel, x, y, z);
        
        var offsetRadius = AllConfigs.client().offsetRadius.getF();
        this.x += (this.random.nextFloat() * 2 - 1) * offsetRadius;
        this.z += (this.random.nextFloat() * 2 - 1) * offsetRadius;
        this.xo = this.x;
        this.zo = this.z;
        this.lifetime = 160; 

        // Active particle tracking for overlapping fade API
        if (ACTIVE_PARTICLES.size() >= 2) {
             for (DamageParticle p : ACTIVE_PARTICLES) {
                 p.targetFadePenalty = Math.min(0.8f, p.targetFadePenalty + 0.15f);
             }
        }
        ACTIVE_PARTICLES.add(this);

        var isCrit = dz > 1.0f;

        MutableComponent text = Component.literal((amount < 0 ? "+" : "") + DF1.format(amount) + (isCrit && AllConfigs.client().showExclamationPoint.get() ? "!" : ""));
        this.xd = 0f;
        this.yd = 0.3;

        var sqDz = Math.sqrt(dz);

        var defaultColor = AllConfigs.client().textColor.get();
        var hurtColor = AllConfigs.client().hurtColor.get();
        opacity = AllConfigs.client().alpha.get();

        // Простая логика цвета: белый для обычных ударов, красный для критов
        var textColor = isCrit ? hurtColor : defaultColor;

        var endTime = 1f;

        this.message = ImmersiveMessage.builder(5f, text)
            .anchor(TextAnchor.CENTER_CENTER)
            .slideUp()
            .fadeIn()
            .size(2.5f)
            .animation(anim -> {
                if (AllConfigs.client().doSizeEffects.get())
                {
                    anim.transition(Binding.Size, 0, 1f, 0f, isCrit ? 3f : 2f, EasingType.EaseOutExpo);
                    anim.transition(Binding.Size, 1f, 2.5f, isCrit ? 3f : 2f, isCrit ? 1.5f : 1f, EasingType.EaseInOutSine);
                }

                if (AllConfigs.client().doColorEffects.get())
                    anim.transition(Binding.Color, 0f, endTime, textColor, textColor, EasingType.EaseOutSine);

                if (AllConfigs.client().doShakeEffects.get())
                    anim.waveEffect(Binding.zRot, (float) (isCrit ? 5f : 2F * sqDz), (float) (isCrit ? 5f : 5F * sqDz), 0.0F, 5f);
            })
            .fadeOut();

        if (PlatformUtils.isModLoaded("caxton") && !IrisCompat.areShadersEnabled())
            message.font(AllConfigs.client().getFont().toString());

        message.onPoseMessage = this::applyPose;

        CreateDamageIndicator.EVENT.invoker().onCreateDamageIndicator(this, message);
    }

    @Override
    public void remove() {
        super.remove();
        ACTIVE_PARTICLES.remove(this);
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTicks) {
        Vec3 cameraPos = camera.getPosition();
        float particleX = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cameraPos.x());
        float particleY = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cameraPos.y());
        float particleZ = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cameraPos.z());

        var buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        var graphics = new GuiGraphics(Minecraft.getInstance(), buffer);

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(particleX, particleY, particleZ);

        double distanceFromCam = new Vec3(particleX, particleY, particleZ).length();
        double inc = Mth.clamp(distanceFromCam / 32f, 0, 5f);

        poseStack.translate(0, 1.0 + (1 + inc / 4f) * Mth.lerp(partialTicks, this.prevVisualDY, this.visualDY), 0);

        float d2r = 0.017453292F;
        Quaternionf quat = (new Quaternionf()).rotationYXZ(camera.getYRot() * -d2r, camera.getXRot() * d2r, 0f);
        poseStack.mulPose(quat);

        float fadeout = Mth.lerp(partialTicks, this.prevFadeout, this.fadeout);
        float defScale = 0.006f;
        float scale = (float) (defScale * distanceFromCam);

        poseStack.translate((1 + inc) * Mth.lerp(partialTicks, this.prevVisualDX, this.visualDX), 0, 0);
        poseStack.scale(-scale, -scale, scale);
        // Removed explicit translate fadeout here if it conflicts, but visual movement is fine. 
        // Keeping vertical visual fadeout movement:
        poseStack.translate(0, (4d * (1 - fadeout)), 0); 
        poseStack.translate(0, -distanceFromCam / 10d, 0);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 0);

        message.onRenderMessage = this::renderMessage;
        if (PlatformUtils.isModLoaded("caxton"))
            message.onRenderMessageCaxton = (tooltip, g, r, t, y) -> CaxtonRenderer.renderMessageCaxton(this, tooltip,  g,  r,  t, y);

        var forceVanilla = IrisCompat.areShadersEnabled();
        if (forceVanilla) ImmersiveMessagesManager.forceVanillaRenderer = true;

        message.render(graphics, partialTicks);

        if (forceVanilla) ImmersiveMessagesManager.forceVanillaRenderer = false;
        
        buffer.endBatch();

        poseStack.popPose();
    }

    public void renderMessage(ImmersiveMessage tooltip, GuiGraphics graphics, FormattedText line, int yOffset) {
        Matrix4f mat = graphics.pose().last().pose();
        MultiBufferSource.BufferSource renderType = graphics.bufferSource();
        Font font = Minecraft.getInstance().font;
        float fade = FastColor.ARGB32.alpha(tooltip.animation.getColor()) / 255f;
        
        // Cumulative fade logic + End of life fadeout
        // We use 'this.fadeout' which we calculate in tick() for the smooth end-of-life fade
        float combinedAlpha = Math.max(0, (1f - fadePenalty) * fade * this.fadeout);
        int alpha = (int) (opacity * combinedAlpha);
        
        var color = tooltip.animation.getColor();

        // Shadow (drawn first, behind)
        graphics.pose().pushPose();
        graphics.pose().translate(0.5, 0.5, -0.03);
        font.drawInBatch(
            Component.literal(line.getString()),
            0,
            yOffset,
            FastColor.ARGB32.color(alpha, 40, 40, 40),
            false,
            graphics.pose().last().pose(),
            renderType,
            Font.DisplayMode.SEE_THROUGH,
            0,
            15728880);
        graphics.pose().popPose();

        // Main text (drawn second, on top)
        font.drawInBatch(
            Language.getInstance().getVisualOrder(line),
            0,
            yOffset,
            ColorUtils.color(alpha, ColorUtils.red(color), ColorUtils.green(color), ColorUtils.blue(color)),
            false,
            mat,
            renderType,
            Font.DisplayMode.SEE_THROUGH,
            0,
            15728880);
    }

    public AnimationKeyframe applyPose(ImmersiveMessage message, AnimationTimeline animation, GuiGraphics context, Vector2i bgOffset, TextAnchor anchor, TextAnchor align, float objectWidth, float objectHeight) {
        AnimationKeyframe key = animation.getKeyframe();
        if (key.size != 1.0F) {
            context.pose().translate(objectWidth / 2, objectHeight / 2, 0.0F);
            context.pose().scale(key.size, key.size, key.size);
            context.pose().translate(objectWidth / -2, objectHeight / -2, 0.0F);
        }

        if (key.rotY != 0.0F) {
            PoseUtils.applyYRotation(context, key.size, objectWidth, objectHeight, key.rotY);
        }

        if (key.rotX != 0.0F) {
            PoseUtils.applyXRotation(context, key.size, objectWidth, objectHeight, key.rotX);
        }

        if (key.rotZ != 0.0F) {
            PoseUtils.applyZRotation(context, key.size, objectWidth, objectHeight, key.rotZ);
        }

        return key;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        
        // Smoothly interpolate fade penalty
        this.fadePenalty = Mth.lerp(0.1f, this.fadePenalty, this.targetFadePenalty);
        
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            // New fade out logic: starts later (last 20 ticks) but faster
            float fadeLength = 20; 
            this.prevFadeout = this.fadeout;
            this.fadeout = this.age > (lifetime - fadeLength) ? ((float) lifetime - this.age) / fadeLength : 1;

            this.prevVisualDY = this.visualDY;
            this.visualDY += this.yd;
            this.prevVisualDX = this.visualDX;
            this.visualDX += this.xd;

            if (Math.sqrt(Mth.square(this.visualDX * 1.5) + Mth.square(this.visualDY - 1)) < 1.9 - 1) {

                this.yd = this.yd / 2;
            } else {
                this.yd = 0;
                this.xd = 0;
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }


    #if forgelike
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    #endif
    public static class Factory implements ParticleProvider<SimpleParticleType> {
        public Factory(SpriteSet spriteSet) {}

        #if forgelike
        @SubscribeEvent
        public static void register(final RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ParticleRegistry.DAMAGE_PARTICLE, Factory::new);
        }
        #endif

        @Override
        public Particle createParticle(SimpleParticleType typeIn, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new DamageParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}
