package com.cardejibka.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatSelectClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("chatselect");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Chat Select loaded");
	}
}