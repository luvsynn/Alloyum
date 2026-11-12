package mcopt.metal.gui;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import mcopt.metal.fx.ColorGrading;
import mcopt.metal.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public final class AlloyumVideoScreen extends Screen {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final List<String> PROFILES = List.of("indie", "alpha", "recommended", "none");
	private static boolean bypassVanillaRoute;
	private final Screen parent;
	private Page page = Page.ALLOYUM;
	private String profile;
	private boolean farTerrain;
	private String status = "";

	public AlloyumVideoScreen(Screen parent) {
		super(Component.literal("Alloyum Settings"));
		LOGGER.info("Alloyum: opening custom video screen");
		this.parent = parent;
		try {
			this.profile = Profile.configuredProfile();
			if (!PROFILES.contains(this.profile)) this.profile = "indie";
			this.farTerrain = Profile.configuredFarTerrain(this.profile);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static boolean bypassesVanillaRoute() {
		return bypassVanillaRoute;
	}

	@Override
	protected void init() {
		int navWidth = Math.min(140, this.width / 3);
		int contentX = navWidth + 30;
		int contentWidth = Math.max(100, this.width - contentX - 20);
		int y = 50;
		this.addRenderableWidget(Button.builder(Component.literal("Alloyum"), button -> this.showPage(Page.ALLOYUM))
			.bounds(20, y, navWidth, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Shaders"), button -> this.showPage(Page.SHADERS))
			.bounds(20, y + 26, navWidth, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Vanilla"), button -> this.showPage(Page.VANILLA))
			.bounds(20, y + 52, navWidth, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
			.bounds(this.width - 110, this.height - 30, 90, 20).build());

		String heading = switch (this.page) {
			case ALLOYUM -> "Alloyum";
			case SHADERS -> "Shaders";
			case VANILLA -> "Vanilla";
		};
		this.addRenderableWidget(new StringWidget(contentX, y, contentWidth, 20, Component.literal(heading), this.font));

		switch (this.page) {
			case ALLOYUM -> this.addAlloyumOptions(contentX, contentWidth, y + 40);
			case SHADERS -> this.addShaderOptions(contentX, contentWidth, y + 40);
			case VANILLA -> this.addRenderableWidget(Button.builder(Component.literal("Open vanilla video settings"), button -> this.openVanilla())
				.bounds(contentX, y + 40, contentWidth, 20).build());
		}
		if (!this.status.isEmpty()) {
			this.addRenderableWidget(new StringWidget(contentX, this.height - 60, contentWidth, 20, Component.literal(this.status), this.font));
		}
	}

	private void addAlloyumOptions(int x, int width, int y) {
		this.addRenderableWidget(Button.builder(this.profileLabel(), button -> this.nextProfile())
			.bounds(x, y, width, 20).build());
		this.addRenderableWidget(Button.builder(this.farTerrainLabel(), button -> this.toggleFarTerrain())
			.bounds(x, y + 30, width, 20).build());
	}

	private void addShaderOptions(int x, int width, int y) {
		this.addRenderableWidget(Button.builder(Component.literal("Color grading: " + (ColorGrading.enabled() ? "On" : "Off")),
			button -> this.toggleColorGrading()).bounds(x, y, width, 20).build());
		this.addRenderableWidget(new StrengthSlider(x, y + 30, width));
	}

	private void toggleColorGrading() {
		try {
			ColorGrading.setEnabled(!ColorGrading.enabled(), this.minecraft.getShaderManager());
			this.status = "";
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not save Alloyum settings to config/mcopt.properties", e);
			this.status = "Could not save settings; see log";
		}
		this.rebuildWidgets();
	}

	private Component profileLabel() {
		return Component.literal("Performance profile: " + this.profile + " (restart required)");
	}

	private Component farTerrainLabel() {
		return Component.literal("Far terrain: " + (this.farTerrain ? "On" : "Off") + " (restart required)");
	}

	private void nextProfile() {
		String next = PROFILES.get((PROFILES.indexOf(this.profile) + 1) % PROFILES.size());
		try {
			boolean nextFarTerrain = Profile.configuredFarTerrain(next);
			Profile.saveSetting("profile", next);
			this.profile = next;
			this.farTerrain = nextFarTerrain;
			this.status = "";
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not save Alloyum settings to config/mcopt.properties", e);
			this.status = "Could not save settings; see log";
		}
		this.rebuildWidgets();
	}

	private void toggleFarTerrain() {
		boolean next = !this.farTerrain;
		try {
			Profile.saveSetting("mcopt.lod", Boolean.toString(next));
			this.farTerrain = next;
			this.status = "";
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not save Alloyum settings to config/mcopt.properties", e);
			this.status = "Could not save settings; see log";
		}
		this.rebuildWidgets();
	}

	private void showPage(Page page) {
		this.page = page;
		this.status = "";
		this.rebuildWidgets();
	}

	private void openVanilla() {
		// The vanilla screen returns to this screen, and the router skips this one opening.
		bypassVanillaRoute = true;
		try {
			this.minecraft.setScreenAndShow(new VideoSettingsScreen(this, this.minecraft, this.minecraft.options));
		} finally {
			bypassVanillaRoute = false;
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(this.parent);
	}

	private final class StrengthSlider extends AbstractSliderButton {
		private StrengthSlider(int x, int y, int width) {
			super(x, y, width, 20, Component.empty(), ColorGrading.strength() / 100.0);
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal("Strength: " + Math.round(this.value * 100) + "%"));
		}

		@Override
		protected void applyValue() {
			ColorGrading.setStrengthLive((int) Math.round(this.value * 100));
		}

		@Override
		public void onRelease(MouseButtonEvent event) {
			super.onRelease(event);
			this.saveStrength();
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			boolean changed = super.keyPressed(event);
			if (changed) this.saveStrength();
			return changed;
		}

		private void saveStrength() {
			try {
				ColorGrading.saveStrength();
				AlloyumVideoScreen.this.status = "";
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Could not save Alloyum color-grading strength", e);
				AlloyumVideoScreen.this.status = "Could not save settings; see log";
			}
		}
	}

	private enum Page {
		ALLOYUM,
		SHADERS,
		VANILLA
	}
}
