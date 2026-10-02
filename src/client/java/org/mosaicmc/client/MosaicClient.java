package org.mosaicmc.client;

import org.mosaicmc.client.key.MosaicKeyEventHandler;
import org.mosaicmc.client.key.MosaicKeys;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
		ClientTickEvents.END_CLIENT_TICK.register(_ -> ExtensionManager.saveSettingsIfDirty());
		ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> ExtensionManager.saveSettings());

		MosaicKeys.init();
		MosaicKeyEventHandler.init();
	}
}