package org.mosaicmc.test;

import org.mosaicmc.core.Extension;
import org.mosaicmc.core.ExtensionMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestExtension implements Extension {
	public static final Logger LOGGER = LoggerFactory.getLogger("mosaic-testmod");

	private static final ExtensionMetadata METADATA = new ExtensionMetadata() {
		@Override
		public String getId() {
			return "mosaic-testmod";
		}

		@Override
		public String getName() {
			return "Mosaic Test Extension";
		}

		@Override
		public String getDescription() {
			return "Test mod for the Mosaic extension API.";
		}

		@Override
		public String getVersion() {
			return "1.0.0";
		}

		@Override
		public String getAuthors() {
			return "Me!";
		}

		@Override
		public String getWebsite() {
			return "https://fabricmc.net/";
		}
	};

	@Override
	public ExtensionMetadata getMetadata() {
		return METADATA;
	}

	@Override
	public void onEnable() {
		LOGGER.info("Test extension enabled: {}", getMetadata().getId());
	}

	@Override
	public void onDisable() {
		LOGGER.info("Test extension disabled: {}", getMetadata().getId());
	}

	@Override
	public void onLoad() {
		LOGGER.info("Test extension loaded: {}", getMetadata().getId());
	}
}
