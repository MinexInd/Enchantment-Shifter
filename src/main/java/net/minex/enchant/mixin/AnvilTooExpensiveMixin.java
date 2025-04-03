package net.minex.enchant.mixin;

import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilScreenHandler.class)
public class AnvilTooExpensiveMixin {

    @Inject(method = "canTakeOutput", at = @At("HEAD"), cancellable = true)
    private void removeTooExpensive(PlayerEntity player, boolean present, CallbackInfoReturnable<Boolean> cir) {
        // Always allow the player to take the item from the anvil
        cir.setReturnValue(true);
    }
}
