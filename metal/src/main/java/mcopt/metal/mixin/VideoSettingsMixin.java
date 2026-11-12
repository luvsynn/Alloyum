package mcopt.metal.mixin;

import com.mojang.logging.LogUtils;
import mcopt.metal.gui.AlloyumVideoScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class VideoSettingsMixin {
	private static final Logger alloyum$LOGGER = LogUtils.getLogger();

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void alloyum$openSettings(Screen target, CallbackInfo ci) {
		if (!(target instanceof VideoSettingsScreen) || AlloyumVideoScreen.bypassesVanillaRoute()) return;
		Gui gui = (Gui) (Object) this;
		Screen parent = gui.screen();
		try {
			gui.setScreen(new AlloyumVideoScreen(parent));
			ci.cancel();
		} catch (Throwable error) {
			alloyum$LOGGER.error("Could not open Alloyum settings; opening vanilla video settings instead", error);
		}
	}
}
