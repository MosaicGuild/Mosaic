package org.mosaicmc.test;

import org.mosaicmc.core.Extension;
import org.mosaicmc.core.ExtensionMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestExtension extends Extension {
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
		LOGGER.info("[Mosaic Test] enabled: {}", getMetadata().getId());
		try {
			getContext().getScheduler().execute(() -> LOGGER.info(
					"[Mosaic Test] onEnable scheduled task running on thread {}",
					Thread.currentThread().getName()));
		} catch (IllegalStateException e) {
			LOGGER.warn("[Mosaic Test] onEnable has no context yet", e);
		}
	}

	@Override
	public void onDisable() {
		LOGGER.info("[Mosaic Test] disabled: {}", getMetadata().getId());
		try {
			getContext().getScheduler().execute(() -> LOGGER.info(
					"[Mosaic Test] onDisable scheduled task running on thread {}",
					Thread.currentThread().getName()));
		} catch (IllegalStateException e) {
			LOGGER.warn("[Mosaic Test] onDisable has no context yet", e);
		}
	}

	@Override
	public void onLoad() {
		LOGGER.info("[Mosaic Test] loaded: {} on thread {}",
				getMetadata().getId(), Thread.currentThread().getName());
		try {
			var scheduler = getContext().getScheduler();
			LOGGER.info("[Mosaic Test] context OK, scheduler={}",
					scheduler.getClass().getSimpleName());

			// Server-safe self-check: hop from a background thread through the
			// ExtensionScheduler. At onLoad time the client initializer has not
			// run yet, so the scheduler is still the inline default and the
			// runner is expected to be the bg thread itself. After
			// MosaicClient installs the real client scheduler,
			// TestExtensionClient repeats the check with visible chat output.
			String callerThread = Thread.currentThread().getName();
			Thread bg = new Thread(() -> {
				String bgThread = Thread.currentThread().getName();
				try {
					getContext().getScheduler().execute(() -> LOGGER.info(
							"[Mosaic Test] early (pre-client) hop: caller={} bg={} runner={} scheduler={}",
							callerThread, bgThread, Thread.currentThread().getName(),
							getContext().getScheduler().getClass().getSimpleName()));
				} catch (Exception e) {
					LOGGER.error("[Mosaic Test] scheduler hop FAILED", e);
				}
			}, "mosaic-test-bg");
			bg.setDaemon(true);
			bg.start();
		} catch (IllegalStateException e) {
			LOGGER.error("[Mosaic Test] context MISSING at onLoad (was ExtensionManager bypassed?)", e);
		}
	}
}
