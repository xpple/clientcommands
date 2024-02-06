package net.earthcomputer.clientcommands;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record CommandExecutionCustomPayload(String command) implements CustomPayload {
    public static final PacketCodec<PacketByteBuf, CommandExecutionCustomPayload> CODEC = CustomPayload.codecOf(CommandExecutionCustomPayload::write, CommandExecutionCustomPayload::new);
    public static final CustomPayload.Id<CommandExecutionCustomPayload> ID = new CustomPayload.Id<>(ClientCommands.COMMAND_EXECUTION_PACKET_ID);

    private CommandExecutionCustomPayload(PacketByteBuf buf) {
        this(buf.readString());
    }

    private void write(PacketByteBuf buf) {
        buf.writeString(this.command);
    }

    public CustomPayload.Id<CommandExecutionCustomPayload> getId() {
        return ID;
    }
}
