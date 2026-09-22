package org.mosaicmc.client.key;

import org.mosaicmc.client.screen.MosaicScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

public class MosaicKeyEventHandler {
    public static void init(){
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (MosaicKeys.MosaicScreen.consumeClick()){
                if (client.player == null) return;

                client.setScreenAndShow(new MosaicScreen(Component.literal("Mosaic Screen")));
            }
        });
    }
}
