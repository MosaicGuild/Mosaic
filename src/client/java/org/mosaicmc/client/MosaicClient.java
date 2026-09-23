package org.mosaicmc.client;

import org.mosaicmc.client.key.MosaicKeyEventHandler;
import org.mosaicmc.client.key.MosaicKeys;
import net.fabricmc.api.ClientModInitializer;
import org.mosaicmc.Mosaic;
import org.mosaicmc.extension.ExtensionManager;
import org.mosaicmc.internal.ClientCommandAdapter;

public class MosaicClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ExtensionManager.setScheduler(ClientExtensionScheduler.INSTANCE);
		Mosaic.LOGGER.info("[Mosaic] client scheduler installed: {}",
				ClientExtensionScheduler.INSTANCE.getClass().getSimpleName());
		ManagedClientTick.installOnce();
		Mosaic.LOGGER.info("[Mosaic] managed client tick installed");
		ClientCommandAdapter.installOnce();
		Mosaic.LOGGER.info("[Mosaic] client command adapter installed (/mosaic)");

		MosaicKeys.init();
		MosaicKeyEventHandler.init();
	}
}