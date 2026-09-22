package org.mosaicmc.core;

public interface ExtensionMetadata {

    /**
     * The ID of the extension
     *
     * @return String
     */
    String getId();

    /**
     * The name of the extension
     *
     * @return String
     */
    String getName();

    /**
     * The description of the extension
     *
     * @return String
     */
    String getDescription();

    /**
     * The version of the extension
     *
     * @return String
     */
    String getVersion();

    /**
     *  The authors of the extension
     *
     * @return String
     */
    String getAuthors();

    /**
     * The Website of the author or extension or source code
     *
     * @return String
     */
    String getWebsite();
}
