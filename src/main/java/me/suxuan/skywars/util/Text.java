package me.suxuan.skywars.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Text {
	private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

	private Text() {}

	public static Component mm(String input) {
		return MINI_MESSAGE.deserialize(input);
	}
}
