package mcopt.metal.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import mcopt.metal.fx.ColorGrading;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class ColorGradingMixin {
	@Shadow
	@Final
	@Mutable
	private RenderTarget mainRenderTarget;
	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@Unique
	private RenderTarget alloyum$sourceTarget;
	@Unique
	private RenderTarget alloyum$gradedTarget;

	@Inject(method = "render()V", at = @At("HEAD"))
	private void alloyum$restoreMainTarget(CallbackInfo ci) {
		if (this.alloyum$sourceTarget != null && this.mainRenderTarget == this.alloyum$gradedTarget) {
			this.mainRenderTarget = this.alloyum$sourceTarget;
		}
		this.alloyum$sourceTarget = this.mainRenderTarget;
	}

	@Inject(method = "applyPostEffects", at = @At("TAIL"))
	private void alloyum$applyColorGrading(CallbackInfo ci) {
		if (!ColorGrading.enabled()) return;
		RenderTarget source = this.mainRenderTarget;
		RenderTarget graded = ColorGrading.apply(Minecraft.getInstance().getShaderManager(), source, this.resourcePool);
		if (graded != null) {
			this.alloyum$sourceTarget = source;
			this.alloyum$gradedTarget = graded;
			this.mainRenderTarget = graded;
		}
	}

	@Inject(method = "close()V", at = @At("HEAD"))
	private void alloyum$closeColorGrading(CallbackInfo ci) {
		if (this.alloyum$sourceTarget != null) this.mainRenderTarget = this.alloyum$sourceTarget;
		ColorGrading.close(Minecraft.getInstance().getShaderManager());
	}
}
