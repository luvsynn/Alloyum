package mcopt.metal.fx;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
	public static final Identifier CHAIN_ID = Identifier.fromNamespaceAndPath("alloyum", "color_grading");
	private static final Identifier OUTPUT_TARGET_ID = Identifier.fromNamespaceAndPath("alloyum", "grade_output");
	private static final Set<Identifier> ALLOWED_TARGETS = allowedTargets();
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Set<GpuBuffer> UNIFORMS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static boolean enabled;
	private static int saturation = 20;
	private static int contrast = 20;
	private static int warmth = 10;
	private static boolean created;
	private static TextureTarget outputTarget;

	static {
		enabled = readEnabled();
		saturation = readPercent(Control.SATURATION, saturation);
		contrast = readPercent(Control.CONTRAST, contrast);
		warmth = readPercent(Control.WARMTH, warmth);
	}

	private ColorGrading() {
	}

	public enum Control {
		SATURATION("Saturation", "alloyum.fx.grade.saturation"),
		CONTRAST("Contrast", "alloyum.fx.grade.contrast"),
		WARMTH("Warmth", "alloyum.fx.grade.warmth");

		private final String label;
		private final String key;

		Control(String label, String key) {
			this.label = label;
			this.key = key;
		}

		public String label() {
			return this.label;
		}
	}

	public static boolean enabled() {
		return enabled;
	}

	public static int value(Control control) {
		return switch (control) {
			case SATURATION -> saturation;
			case CONTRAST -> contrast;
			case WARMTH -> warmth;
		};
	}

	public static void setEnabled(boolean value, ShaderManager shaderManager) throws IOException {
		if (enabled == value) return;
		Profile.saveSetting(ENABLED_KEY, Boolean.toString(value));
		enabled = value;
		if (!value) releaseResources(shaderManager);
	}

	public static void setLive(Control control, int value, ShaderManager shaderManager) {
		int bounded = Math.max(0, Math.min(100, value));
		switch (control) {
			case SATURATION -> saturation = bounded;
			case CONTRAST -> contrast = bounded;
			case WARMTH -> warmth = bounded;
		}
		if (!enabled) return;
		if (isNeutral()) {
			releaseResources(shaderManager);
			return;
		}
		try {
			updateUniforms();
		} catch (RuntimeException e) {
			disableAfterFailure(shaderManager, e);
		}
	}

	public static void save(Control control) throws IOException {
		Profile.saveSetting(control.key, Integer.toString(value(control)));
	}

	public static void registerUniform(GpuBuffer buffer) {
		UNIFORMS.add(buffer);
		writeUniform(buffer);
	}

	public static void unregisterUniform(GpuBuffer buffer) {
		UNIFORMS.remove(buffer);
	}

	public static RenderTarget apply(ShaderManager shaderManager, RenderTarget source, GraphicsResourceAllocator allocator) {
		if (!enabled || isNeutral()) return null;
		try {
			if (!shaderManager.isPostEffectValid(CHAIN_ID, ALLOWED_TARGETS)) {
				throw new IllegalStateException("Minecraft rejected the Alloyum color-grading post chain");
			}
			PostChain chain = shaderManager.getPostChain(CHAIN_ID, ALLOWED_TARGETS);
			if (chain == null) throw new IllegalStateException("Minecraft could not load the Alloyum color-grading post chain");
			ensureOutputTarget(source.width, source.height);
			FrameGraphBuilder frame = new FrameGraphBuilder();
			Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();
			targets.put(PostChain.MAIN_TARGET_ID, frame.importExternal("Alloyum grading source", source));
			targets.put(OUTPUT_TARGET_ID, frame.importExternal("Alloyum grading output", outputTarget));
			chain.addToFrame(frame, source.width, source.height, targetBundle(targets));
			frame.execute(allocator);
			if (!created) {
				created = true;
				LOGGER.info("Alloyum: color grading effect created");
			}
			return outputTarget;
		} catch (RuntimeException e) {
			disableAfterFailure(shaderManager, e);
			return null;
		}
	}

	public static void close(ShaderManager shaderManager) {
		releaseResources(shaderManager);
	}

	private static boolean readEnabled() {
		try {
			String value = Profile.configuredSetting(ENABLED_KEY, "false");
			if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Expected true or false");
			return Boolean.parseBoolean(value);
		} catch (IOException | IllegalArgumentException e) {
			LOGGER.error("Could not read Alloyum color-grading toggle; using off", e);
			return false;
		}
	}

	private static int readPercent(Control control, int fallback) {
		try {
			int value = Integer.parseInt(Profile.configuredSetting(control.key, Integer.toString(fallback)));
			if (value < 0 || value > 100) throw new IllegalArgumentException("Value must be from 0 to 100");
			return value;
		} catch (IOException | IllegalArgumentException e) {
			LOGGER.error("Could not read {}; using {}%", control.key, fallback, e);
			return fallback;
		}
	}

	private static Set<Identifier> allowedTargets() {
		Set<Identifier> targets = new HashSet<>(LevelTargetBundle.MAIN_TARGETS);
		targets.add(OUTPUT_TARGET_ID);
		return Set.copyOf(targets);
	}

	private static PostChain.TargetBundle targetBundle(Map<Identifier, ResourceHandle<RenderTarget>> targets) {
		return new PostChain.TargetBundle() {
			@Override
			public void replace(Identifier id, ResourceHandle<RenderTarget> handle) {
				if (!targets.containsKey(id)) throw new IllegalArgumentException("Unexpected post-chain target " + id);
				targets.put(id, handle);
			}

			@Override
			public ResourceHandle<RenderTarget> get(Identifier id) {
				return targets.get(id);
			}
		};
	}

	private static boolean isNeutral() {
		return saturation == 0 && contrast == 0 && warmth == 0;
	}

	private static void ensureOutputTarget(int width, int height) {
		if (outputTarget == null) {
			outputTarget = new TextureTarget("Alloyum color grading", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
		} else if (outputTarget.width != width || outputTarget.height != height) {
			outputTarget.resize(width, height);
		}
	}

	private static void updateUniforms() {
		for (GpuBuffer buffer : UNIFORMS) writeUniform(buffer);
	}

	private static void writeUniform(GpuBuffer buffer) {
		try (GpuBufferSlice.MappedView mapped = buffer.map(false, true)) {
			Std140Builder builder = Std140Builder.intoBuffer(mapped.data());
			builder.putFloat(saturation / 100.0F);
			builder.putFloat(contrast / 100.0F);
			builder.putFloat(warmth / 100.0F);
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
		releaseResources(shaderManager);
	}

	private static void releaseResources(ShaderManager shaderManager) {
		try {
			if (shaderManager != null) {
				ShaderManager.PostChainCache cache = ((ShaderManagerPostChainAccessor) (Object) shaderManager).alloyum$postChainCache();
				if (cache != null) {
					Map<Identifier, Optional<PostChain>> chains = ((PostChainCacheAccessor) cache).alloyum$postChains();
					Optional<PostChain> chain = chains.remove(CHAIN_ID);
					if (chain != null) chain.ifPresent(PostChain::close);
				}
			}
		} catch (RuntimeException e) {
			LOGGER.error("Could not release the Alloyum color-grading post chain", e);
		}
		UNIFORMS.clear();
		if (outputTarget != null) {
			try {
				outputTarget.destroyBuffers();
			} catch (RuntimeException e) {
				LOGGER.error("Could not release the Alloyum color-grading target", e);
			}
			outputTarget = null;
		}
		if (created) {
			created = false;
			LOGGER.info("Alloyum: color grading effect removed");
		}
	}
}
