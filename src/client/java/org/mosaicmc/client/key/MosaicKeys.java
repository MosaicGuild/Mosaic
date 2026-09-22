package org.mosaicmc.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import org.mosaicmc.Mosaic;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public class MosaicKeys {
    public static KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Mosaic.id("mosaic")
    );

    public static KeyMapping MosaicScreen;

    public static void init(){
        MosaicScreen = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.mosaic.screen",
                        InputConstants.Type.KEYBOARD,
                        InputConstants.KEY_LSHIFT,
                        CATEGORY
                ));
    }
}
