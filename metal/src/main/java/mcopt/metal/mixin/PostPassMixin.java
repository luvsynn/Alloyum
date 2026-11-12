package mcopt.metal.mixin;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.Map;
import mcopt.metal.fx.ColorGrading;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostPass.class)
abstract class PostPassMixin {
	@Shadow
	@Final
	private Map<String, GpuBuffer> customUniforms;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void alloyum$registerGradeUniform(RenderPipeline pipeline, Identifier outputTarget,
			Map<String, java.util.List<UniformValue>> uniforms, java.util.List<PostPass.Input> inputs, CallbackInfo ci) {
		GpuBuffer buffer = this.customUniforms.get("GradeConfig");
		if (buffer != null) ColorGrading.registerUniform(buffer);
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void alloyum$unregisterGradeUniform(CallbackInfo ci) {
		GpuBuffer buffer = this.customUniforms.get("GradeConfig");
		if (buffer != null) ColorGrading.unregisterUniform(buffer);
	}
}
