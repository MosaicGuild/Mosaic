package org.mosaicmc;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.mosaicmc.extension.ExtensionManager;
import org.mosaicmc.internal.MosaicCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mosaic implements ModInitializer {
	public static final String MOD_ID = "mosaic";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ExtensionManager.init();
		MosaicCommands.registerDefaults();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
