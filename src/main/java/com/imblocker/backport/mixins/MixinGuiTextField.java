package com.imblocker.backport.mixins;

import net.minecraft.client.gui.GuiTextField;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.imblocker.backport.input.ImeStateManager;

/**
 * Every vanilla-derived text widget funnels through {@code setFocused}, including the ones hidden inside NEI, GT and
 * quest GUIs, so this single hook gives us dependable focus information without depending on GUI internals.
 */
@Mixin(GuiTextField.class)
public class MixinGuiTextField {

    @Inject(method = "setFocused", at = @At("HEAD"))
    private void imblocker$onSetFocused(boolean focused, CallbackInfo ci) {
        GuiTextField self = (GuiTextField) (Object) this;
        if (focused) {
            ImeStateManager.onTextFieldFocused(self);
        } else {
            ImeStateManager.onTextFieldBlurred(self);
        }
    }
}
