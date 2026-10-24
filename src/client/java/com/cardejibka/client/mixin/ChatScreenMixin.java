package com.cardejibka.client.mixin;

import com.cardejibka.client.ChatSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Shadow
    protected EditBox input;

    @Unique
    private final ChatSelection chatselect$selection = new ChatSelection();
    @Unique
    private boolean chatselect$replaying;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void chatselect$onClick(MouseButtonEvent event, boolean doubleClick,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (chatselect$replaying || event.button() != 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ChatSelection.Hit hit = chatselect$selection.hit(mc, event.x(), event.y(), false);
        if (hit == null) {
            chatselect$selection.clear();
            return;
        }

        int clicks = chatselect$selection.registerClick(event.x(), event.y());
        if (clicks == 2) {
            chatselect$selection.selectWord(hit);
        } else if (clicks >= 3) {
            chatselect$selection.selectMessage(mc, hit);
        } else {
            chatselect$selection.start(hit, event);
        }

        chatselect$clearInputSelection();
        cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void chatselect$onRender(GuiGraphics graphics, int mouseX, int mouseY,
                                     float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        MouseButtonEvent click = chatselect$selection.update(mc, mouseX, mouseY);
        if (click != null) {
            chatselect$replaying = true;
            try {
                this.mouseClicked(click, false);
            } finally {
                chatselect$replaying = false;
            }
        }

        chatselect$selection.render(graphics, mc);
        chatselect$selection.updateCursor(graphics, mc, mouseX, mouseY);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void chatselect$onKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!event.hasControlDown()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (event.key() == GLFW.GLFW_KEY_C && chatselect$selection.hasSelection()) {
            String text = chatselect$selection.getText(mc);
            if (!text.isEmpty()) {
                mc.keyboardHandler.setClipboard(text);
            }
            cir.setReturnValue(true);
        } else if (event.key() == GLFW.GLFW_KEY_A) {
            if (event.hasShiftDown()) {
                chatselect$selection.selectAll(mc);
                chatselect$clearInputSelection();
                cir.setReturnValue(true);
            } else {
                chatselect$selection.clear();
            }
        }
    }

    @Unique
    private void chatselect$clearInputSelection() {
        input.setHighlightPos(input.getCursorPosition());
    }
}