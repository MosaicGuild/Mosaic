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
		ExtensionManager.init();
		MosaicCommands.registerDefaults();
		Path settingsFile = FabricLoader.getInstance().getConfigDir().resolve("mosaic/settings.json");
		SettingsStore store = new SettingsStore(settingsFile);
		ExtensionManager.setSettingsStore(store);
		store.load();
		ExtensionManager.restoreEnabledState(store.loadedEnabledIds());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
