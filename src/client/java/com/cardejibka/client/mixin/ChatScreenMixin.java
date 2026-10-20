package com.cardejibka.client.mixin;

import com.cardejibka.client.ChatSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
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

    @Unique
    private boolean chatselect$inputDragging;
    @Unique
    private int chatselect$inputAnchor = -1;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void chatselect$onClick(double mouseX, double mouseY, int button,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (chatselect$replaying || button != 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ChatSelection.Hit hit = chatselect$selection.hit(mc, mouseX, mouseY, false);
        if (hit == null) {
            chatselect$selection.clear();
            chatselect$onInputClick(mouseX, mouseY, cir);
            return;
        }

        int clicks = chatselect$selection.registerClick(mouseX, mouseY);
        if (clicks == 2) {
            chatselect$selection.selectWord(hit);
        } else if (clicks >= 3) {
            chatselect$selection.selectMessage(mc, hit);
        } else {
            chatselect$selection.start(hit, mouseX, mouseY, button);
        }

        chatselect$clearInputSelection();
        cir.setReturnValue(true);
    }

    @Unique
    private void chatselect$onInputClick(double mouseX, double mouseY,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!input.isMouseOver(mouseX, mouseY)) {
            return;
        }

        int clicks = chatselect$selection.registerClick(mouseX, mouseY);
        if (clicks >= 2) {
            input.onClick(mouseX, mouseY);
            chatselect$selectInputText(clicks);
            chatselect$inputDragging = false;
            chatselect$inputAnchor = -1;
            cir.setReturnValue(true);
        } else {
            chatselect$inputDragging = true;
            chatselect$inputAnchor = -1;
        }
    }

    @Unique
    private void chatselect$selectInputText(int clicks) {
        String text = input.getValue();
        if (clicks >= 3) {
            input.setCursorPosition(text.length());
            input.setHighlightPos(0);
            return;
        }

        int[] range = ChatSelection.wordRange(text, input.getCursorPosition());
        input.setCursorPosition(range[1]);
        input.setHighlightPos(range[0]);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void chatselect$onRender(GuiGraphics graphics, int mouseX, int mouseY,
                                     float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        ChatSelection.Press click = chatselect$selection.update(mc, mouseX, mouseY);
        if (click != null) {
            chatselect$replaying = true;
            try {
                this.mouseClicked(click.x(), click.y(), click.button());
            } finally {
                chatselect$replaying = false;
            }
        }

        chatselect$dragInputSelection(mouseX, mouseY);

        chatselect$selection.render(graphics, mc);
        boolean overInput = chatselect$inputDragging || input.isMouseOver(mouseX, mouseY);
        chatselect$selection.updateCursor(mc, mouseX, mouseY, overInput);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void chatselect$onKey(int keyCode, int scanCode, int modifiers,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (!hasControlDown()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (keyCode == GLFW.GLFW_KEY_C && chatselect$selection.hasSelection()) {
            String text = chatselect$selection.getText(mc);
            if (!text.isEmpty()) {
                mc.keyboardHandler.setClipboard(text);
            }
            cir.setReturnValue(true);
        } else if (keyCode == GLFW.GLFW_KEY_A) {
            if (hasShiftDown()) {
                chatselect$selection.selectAll(mc);
                chatselect$clearInputSelection();
                cir.setReturnValue(true);
            } else {
                chatselect$selection.clear();
            }
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void chatselect$onRemoved(CallbackInfo ci) {
        chatselect$selection.resetCursor(Minecraft.getInstance());
    }

    @Unique
    private void chatselect$dragInputSelection(int mouseX, int mouseY) {
        if (!chatselect$inputDragging) {
            return;
        }

        if (!ChatSelection.isLeftDown()) {
            chatselect$inputDragging = false;
            chatselect$inputAnchor = -1;
            return;
        }

        if (chatselect$inputAnchor < 0) {
            chatselect$inputAnchor = input.getCursorPosition();
            return;
        }

        input.onClick(mouseX, mouseY);
        input.setHighlightPos(chatselect$inputAnchor);
    }

    @Unique
    private void chatselect$clearInputSelection() {
        input.setHighlightPos(input.getCursorPosition());
    }
}