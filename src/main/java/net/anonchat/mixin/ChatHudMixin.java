package net.anonchat.mixin;

import net.anonchat.client.chat.ChatMessage;
import net.anonchat.client.chat.ChatRouter;
import net.anonlauncher.chatmod.AnonChatMod;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that intercepts ALL calls to {@link ChatHud#addMessage(Text)},
 * including debug toggle messages (F3+B, F3+G etc.) which bypass
 * Fabric's {@code ClientReceiveMessageEvents}.
 */
@Mixin(ChatHud.class)
public class ChatHudMixin {

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddMessage(final Text message, final CallbackInfo ci) {
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
