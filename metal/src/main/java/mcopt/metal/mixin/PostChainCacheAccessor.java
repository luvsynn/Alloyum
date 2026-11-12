package mcopt.metal.mixin;

import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$PostChainCache")
public interface PostChainCacheAccessor {
	@Accessor("postChains")
	Map<Identifier, Optional<PostChain>> alloyum$postChains();
}
