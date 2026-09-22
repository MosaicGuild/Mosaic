package org.mosaicmc.client;

import org.mosaicmc.client.key.MosaicKeyEventHandler;
import org.mosaicmc.client.key.MosaicKeys;
import net.fabricmc.api.ClientModInitializer;
import org.mosaicmc.Mosaic;
import org.mosaicmc.extension.ExtensionManager;

public class MosaicClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ExtensionManager.setScheduler(ClientExtensionScheduler.INSTANCE);
		Mosaic.LOGGER.info("[Mosaic] client scheduler installed: {}",
				ClientExtensionScheduler.INSTANCE.getClass().getSimpleName());
		MosaicKeys.init();
		MosaicKeyEventHandler.init();
	}
}