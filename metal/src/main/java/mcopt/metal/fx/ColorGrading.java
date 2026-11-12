package mcopt.metal.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mcopt.metal.Profile;
import mcopt.metal.mixin.PostChainCacheAccessor;
import mcopt.metal.mixin.ShaderManagerPostChainAccessor;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class ColorGrading {
	public static final String ENABLED_KEY = "alloyum.fx.grade";
	public static final String STRENGTH_KEY = "alloyum.fx.grade.strength";
	public static final Identifier CHAIN_ID = Identifier.fromNamespaceAndPath("alloyum", "color_grading");
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Set<GpuBuffer> UNIFORMS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static boolean enabled;
	private static int strength = 50;
	private static boolean created;
	private static ShaderManager lastShaderManager;

	static {
		try {
			String enabledValue = Profile.configuredSetting(ENABLED_KEY, "false");
			if (!enabledValue.equals("true") && !enabledValue.equals("false")) throw new IllegalArgumentException("Expected true or false");
			enabled = Boolean.parseBoolean(enabledValue);
			strength = parseStrength(Profile.configuredSetting(STRENGTH_KEY, "50"));
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not read Alloyum color-grading settings; using defaults", e);
			enabled = false;
			strength = 50;
		}
	}

	private ColorGrading() {
	}

	public static boolean enabled() {
		return enabled;
	}

	public static int strength() {
		return strength;
	}

	public static void setEnabled(boolean value, ShaderManager shaderManager) throws IOException {
		if (enabled == value) return;
		Profile.saveSetting(ENABLED_KEY, Boolean.toString(value));
		enabled = value;
		if (!value) remove(shaderManager);
	}

	public static void setStrengthLive(int value) {
		strength = Math.max(0, Math.min(100, value));
		try {
			updateUniforms();
		} catch (RuntimeException e) {
			disableAfterFailure(lastShaderManager, e);
		}
	}

	public static void saveStrength() throws IOException {
		Profile.saveSetting(STRENGTH_KEY, Integer.toString(strength));
	}

	public static void registerUniform(GpuBuffer buffer) {
		UNIFORMS.add(buffer);
		writeUniform(buffer);
	}

	public static void unregisterUniform(GpuBuffer buffer) {
		UNIFORMS.remove(buffer);
	}

	public static void apply(ShaderManager shaderManager, RenderTarget target, GraphicsResourceAllocator allocator) {
		if (!enabled) return;
		lastShaderManager = shaderManager;
		try {
			if (!shaderManager.isPostEffectValid(CHAIN_ID, LevelTargetBundle.MAIN_TARGETS)) {
				throw new IllegalStateException("Minecraft rejected the Alloyum color-grading post chain");
			}
			PostChain chain = shaderManager.getPostChain(CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
			if (chain == null) throw new IllegalStateException("Minecraft could not load the Alloyum color-grading post chain");
			chain.process(target, allocator);
			if (!created) {
				created = true;
				LOGGER.info("Alloyum: color grading effect created");
			}
		} catch (RuntimeException e) {
			disableAfterFailure(shaderManager, e);
		}
	}

	private static int parseStrength(String value) {
		int result = Integer.parseInt(value);
		if (result < 0 || result > 100) throw new IllegalArgumentException("Color-grading strength must be from 0 to 100");
		return result;
	}

	private static void updateUniforms() {
		for (GpuBuffer buffer : UNIFORMS) writeUniform(buffer);
	}

	private static void writeUniform(GpuBuffer buffer) {
		try (GpuBufferSlice.MappedView mapped = buffer.map(false, true)) {
			Std140Builder.intoBuffer(mapped.data()).putFloat(strength / 100.0F);
		}
	}

	private static void disableAfterFailure(ShaderManager shaderManager, RuntimeException error) {
		if (!enabled) return;
		enabled = false;
		LOGGER.error("Alloyum: color grading failed; disabling the effect", error);
		try {
			Profile.saveSetting(ENABLED_KEY, "false");
		} catch (IOException | RuntimeException saveError) {
			LOGGER.error("Could not save the disabled color-grading setting", saveError);
		}
		remove(shaderManager);
	}

	private static void remove(ShaderManager shaderManager) {
		if (shaderManager != null) {
			ShaderManager.PostChainCache cache = ((ShaderManagerPostChainAccessor) (Object) shaderManager).alloyum$postChainCache();
			if (cache != null) {
				Map<Identifier, Optional<PostChain>> chains = ((PostChainCacheAccessor) cache).alloyum$postChains();
				Optional<PostChain> chain = chains.remove(CHAIN_ID);
				if (chain != null) chain.ifPresent(PostChain::close);
			}
		}
		if (created) {
			created = false;
			LOGGER.info("Alloyum: color grading effect removed");
		}
	}
}
