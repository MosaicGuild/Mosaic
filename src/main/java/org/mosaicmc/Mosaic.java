package org.mosaicmc;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import net.fabricmc.loader.api.FabricLoader;
import org.mosaicmc.extension.ExtensionManager;
import org.mosaicmc.internal.MosaicCommands;
import org.mosaicmc.internal.SettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class Mosaic implements ModInitializer {
	public static final String MOD_ID = "mosaic";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Startup sequence for settings: discovery runs onLoad() so all
		// setting declarations exist, then persisted values are applied
		// before any extension can be enabled or the settings UI can read
		// them. Enabling is user-driven and only possible afterwards.
		ExtensionManager.init();
		MosaicCommands.registerDefaults();
		Path settingsFile = FabricLoader.getInstance().getConfigDir().resolve("mosaic/settings.json");
		SettingsStore store = new SettingsStore(settingsFile);
		ExtensionManager.setSettingsStore(store);
		store.load();
		// Restore lifecycle state last: values are in place, so enabling
		// behavior now observes restored settings. Failed extensions stay
		// disabled per the usual enable semantics.
		ExtensionManager.restoreEnabledState(store.loadedEnabledIds());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
