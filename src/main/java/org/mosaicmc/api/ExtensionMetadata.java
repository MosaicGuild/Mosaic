package org.mosaicmc.api;

public interface ExtensionMetadata {

    /**
     * The ID of the extension.
     *
     * <p>Must be non-{@code null} and non-blank; an extension with a missing
     * or blank id is skipped at discovery time. Use lowercase letters,
     * digits, {@code -} and {@code _} (the same charset as a mod id).
     *
     * @return the extension id, never {@code null} or blank
     */
    String getId();

    /**
     * The name of the extension.
     *
     * @return the display name, never {@code null}
     */
    String getName();

    /**
     * The description of the extension.
     *
     * @return the description, never {@code null}
     */
    String getDescription();

    /**
     * The version of the extension.
     *
     * @return the version string, never {@code null}
     */
    String getVersion();

    /**
     * The authors of the extension, comma-separated when there are several.
     *
     * @return the author list as a single string, never {@code null}
     */
    String getAuthors();

    /**
     * The website of the author, extension, or source code.
     *
     * @return the website URL, never {@code null} (may be blank if none)
     */
    String getWebsite();
}
