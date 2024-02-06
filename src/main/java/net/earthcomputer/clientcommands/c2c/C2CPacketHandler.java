package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.*;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.network.encryption.PublicPlayerSession;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.security.PublicKey;

public class C2CPacketHandler implements C2CPacketListener {
    private static final DynamicCommandExceptionType MESSAGE_TOO_LONG_EXCEPTION = new DynamicCommandExceptionType(d -> Text.translatable("ccpacket.messageTooLong", d));
    private static final SimpleCommandExceptionType PUBLIC_KEY_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("ccpacket.publicKeyNotFound"));
    private static final SimpleCommandExceptionType ENCRYPTION_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("ccpacket.encryptionFailed"));

    public static final NetworkState.Factory<C2CPacketListener, RegistryByteBuf> C2C = NetworkStateBuilder.buildFactory(NetworkPhase.PLAY, NetworkSide.CLIENTBOUND, builder -> builder.add(MessageC2CPacket.ID, MessageC2CPacket.CODEC));
    public static final NetworkState<C2CPacketListener> STATE = C2C.bind(RegistryByteBuf.makeFactory(MinecraftClient.getInstance().getNetworkHandler().getRegistryManager()));

    private static final C2CPacketHandler instance = new C2CPacketHandler();

    public static C2CPacketHandler getInstance() {
        return instance;
    }

    public static void sendPacket(Packet<C2CPacketListener> packet, PlayerListEntry recipient) throws CommandSyntaxException {
        PublicPlayerSession session = recipient.getSession();
        if (session == null) {
            throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
        }
        PlayerPublicKey ppk = session.publicKeyData();
        if (ppk == null) {
            throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
        }
        PublicKey key = ppk.data().key();
        PacketByteBuf buf = PacketByteBufs.create();
        STATE.codec().encode(buf, packet);
        byte[] uncompressed = new byte[buf.readableBytes()];
        buf.getBytes(0, uncompressed);
        byte[] compressed = ConversionHelper.Gzip.compress(uncompressed);
        // split compressed into 245 byte chunks
        int chunks = (compressed.length + 244) / 245;
        byte[][] chunked = new byte[chunks][];
        for (int i = 0; i < chunks; i++) {
            int start = i * 245;
            int end = Math.min(start + 245, compressed.length);
            chunked[i] = new byte[end - start];
            System.arraycopy(compressed, start, chunked[i], 0, end - start);
        }
        // encrypt each chunk
        byte[][] encrypted = new byte[chunks][];
        for (int i = 0; i < chunks; i++) {
            encrypted[i] = ConversionHelper.RsaEcb.encrypt(chunked[i], key);
            if (encrypted[i] == null || encrypted[i].length == 0) {
                throw ENCRYPTION_FAILED_EXCEPTION.create();
            }
        }
        // join encrypted chunks into one byte array
        byte[] joined = new byte[encrypted.length * 256];
        for (int i = 0; i < encrypted.length; i++) {
            System.arraycopy(encrypted[i], 0, joined, i * 256, 256);
        }
        String packetString = ConversionHelper.BaseUTF8.toUnicode(joined);
        String commandString = "w " + recipient.getProfile().getName() + " CCENC:" + packetString;
        if (commandString.length() >= 256) {
            throw MESSAGE_TOO_LONG_EXCEPTION.create(commandString.length());
        }
        MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(commandString);
        OutgoingPacketFilter.addPacket(packetString);
    }

    @Override
    public void onMessageC2CPacket(MessageC2CPacket packet) {
        String sender = packet.sender();
        String message = packet.message();
        MutableText prefix = Text.empty();
        prefix.append(Text.literal("[").formatted(Formatting.DARK_GRAY));
        prefix.append(Text.literal("/cwe").formatted(Formatting.AQUA));
        prefix.append(Text.literal("]").formatted(Formatting.DARK_GRAY));
        prefix.append(Text.literal(" "));
        Text text = prefix.append(Text.translatable("ccpacket.messageC2CPacket.incoming", sender, message).formatted(Formatting.GRAY));
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(text);
    }

    @Override
    public NetworkSide getSide() {
        return STATE.side();
    }

    @Override
    public NetworkPhase getPhase() {
        return STATE.id();
    }

    @Override
    public void onDisconnected(Text reason) {
    }

    @Override
    public boolean isConnectionOpen() {
        return true;
    }
}
