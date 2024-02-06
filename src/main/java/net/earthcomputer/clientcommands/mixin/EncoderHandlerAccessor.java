package net.earthcomputer.clientcommands.mixin;

import net.minecraft.network.NetworkState;
import net.minecraft.network.handler.EncoderHandler;
import net.minecraft.network.listener.PacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EncoderHandler.class)
public interface EncoderHandlerAccessor<T extends PacketListener> {
    @Accessor
    NetworkState<T> getState();
}
