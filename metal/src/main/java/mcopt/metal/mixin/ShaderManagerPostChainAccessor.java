package mcopt.metal.mixin;

import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderManager.class)
public interface ShaderManagerPostChainAccessor {
	@Accessor("postChains")
	ShaderManager.PostChainCache alloyum$postChainCache();
}
