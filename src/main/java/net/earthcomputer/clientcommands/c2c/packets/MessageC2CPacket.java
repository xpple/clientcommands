package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacketListener;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import net.minecraft.util.Identifier;

public record MessageC2CPacket(String sender, String message) implements Packet<C2CPacketListener> {
    public static final PacketCodec<RegistryByteBuf, MessageC2CPacket> CODEC = Packet.createCodec(MessageC2CPacket::write, MessageC2CPacket::new);
    public static final PacketType<MessageC2CPacket> ID = new PacketType<>(NetworkSide.CLIENTBOUND, new Identifier("clientcommands", "message"));

    private MessageC2CPacket(RegistryByteBuf buf) {
        this(buf.readString(), buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.sender);
        buf.writeString(this.message);
    }

    @Override
    public void apply(C2CPacketListener listener) {
        listener.onMessageC2CPacket(this);
    }

    @Override
    public PacketType<? extends Packet<C2CPacketListener>> getPacketId() {
        return ID;
    }
}
