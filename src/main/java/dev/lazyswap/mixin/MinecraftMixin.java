package dev.lazyswap.mixin;

import dev.lazyswap.LazySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public LocalPlayer player;
    @Shadow public MultiPlayerGameMode gameMode;

    // resetAttackStrengthTicker is only called in the MISS branch of startAttack.
    @Inject(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"))
    private void lazyswap$onMiss(CallbackInfoReturnable<Boolean> cir) {
        if (!LazySwap.onMiss(player)) return;
        // No attack packet follows a miss, so push the slot now or instant mode returns before the server sees it.
        ((MultiPlayerGameModeAccessor) gameMode).lazyswap$ensureHasSentCarriedItem();
    }
}
