package net.earthcomputer.clientcommands.mixin;

import dev.xpple.clientarguments.ClientArguments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = ClientArguments.class, remap = false)
public class MixinClientArguments {
    @Overwrite
    public void onInitializeClient() {
    }
}
