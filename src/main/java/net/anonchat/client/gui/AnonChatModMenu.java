package net.anonchat.client.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu integration for AnonChat.
 *
 * <p>Opens {@link AnonChatConfigScreen} from the ModMenu config button
 * in Minecraft's pause menu.
 */
public final class AnonChatModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new AnonChatConfigScreen(parent);
    }
}
