package org.mosaicmc.client;

import org.mosaicmc.client.key.MosaicKeyEventHandler;
import org.mosaicmc.client.key.MosaicKeys;
import net.fabricmc.api.ClientModInitializer;
import org.mosaicmc.Mosaic;
import org.mosaicmc.extension.Extension;
import org.mosaicmc.extension.ExtensionManager;

public class MosaicClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ExtensionManager.setScheduler(ClientExtensionScheduler.INSTANCE);
		Mosaic.LOGGER.info("[Mosaic] client scheduler installed: {}",
				ClientExtensionScheduler.INSTANCE.getClass().getSimpleName());
		ManagedClientTick.installOnce();
		Mosaic.LOGGER.info("[Mosaic] managed client tick installed");

		// This code auto enables extension
		// TODO : remove this in favour of gui based enable system
		int enabled = 0;
		for (Extension extension : ExtensionManager.getExtensions()) {
			String id;
			try {
				id = extension.getMetadata().getId();
			} catch (Exception e) {
				Mosaic.LOGGER.error("[Mosaic] skipping auto-enable, broken metadata", e);
				continue;
			}
			ExtensionManager.enable(id);
			enabled++;
		}
		Mosaic.LOGGER.info("[Mosaic] auto-enabled {} extension(s) (no toggle UI yet)", enabled);


		MosaicKeys.init();
		MosaicKeyEventHandler.init();
	}
}