package net.anonchat.mixin;

import net.anonchat.client.AnonChatMod;
import net.anonchat.client.chat.ChatMessage;
import net.anonchat.client.chat.ChatRouter;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that intercepts {@link ChatComponent#addClientSystemMessage(Component)},
 * catching debug toggle messages (F3+B, F3+G etc.) which bypass
 * Fabric's {@code ClientReceiveMessageEvents}.
 */
@Mixin(ChatComponent.class)
public class ChatHudMixin {

    @Inject(
        method = "addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddClientSystemMessage(final Component message, final CallbackInfo ci) {
        if (message != null) {
            final String text = message.getString();
            if (text != null && !text.isEmpty()) {
                final ChatRouter router = AnonChatMod.getRouter();
                if (router != null) {
                    final ChatMessage chatMsg = new ChatMessage(message, text);
                    router.dispatchMessage(chatMsg);
                }
            }
        }
        // Always cancel — our overlay handles display
        ci.cancel();
    }
}
