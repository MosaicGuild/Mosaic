package org.mosaicmc.client;

import org.mosaicmc.client.key.MosaicKeyEventHandler;
import org.mosaicmc.client.key.MosaicKeys;
import net.fabricmc.api.ClientModInitializer;

public class MosaicClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MosaicKeys.init();
		MosaicKeyEventHandler.init();
	}
}