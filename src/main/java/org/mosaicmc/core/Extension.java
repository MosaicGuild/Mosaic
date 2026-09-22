package org.mosaicmc.core;

public interface Extension {

    /**
     * This provides all the metadata of the extension
     */
    ExtensionMetadata getMetadata();

    /**
     * This runs once the extension has been enabled by the user.
     * You may use this to register your changes
     */
    void onEnable();

    /**
     * This runs once the extension has been disabled by the user.
     * You shall unload any listeners that you have registered via the api here.
     */
    void onDisable();

    /**
     * This runs once the extension has been loaded by the extension manager.
     * Use this to add the extension menus and entry in the gui
     */
    void onLoad();
}
