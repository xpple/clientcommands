package net.earthcomputer.clientcommands.command;

import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.packets.PutTicTacToeMarkC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.StartTicTacToeGameC2CPacket;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CGameProfileArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class TicTacToeCommand {

    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.ctictactoe.playerNotFound"));
    private static final SimpleCommandExceptionType NO_GAME_WITH_PLAYER_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.ctictactoe.noGameWithPlayer"));

    private static final Map<String, TicTacToeGame> activeGames = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(15)).<String, TicTacToeGame>build().asMap();
    private static final Set<String> pendingInvites = Collections.newSetFromMap(CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).<String, Boolean>build().asMap());

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctictactoe")
            .then(literal("start")
                .then(argument("opponent", gameProfile())
                    .executes(ctx -> start(ctx.getSource(), getProfileArgument(ctx, "opponent")))))
            .then(literal("open")
                .then(argument("opponent", word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(activeGames.keySet(), builder))
                    .executes(ctx -> open(ctx.getSource(), getString(ctx, "opponent"))))));
    }

    private static int start(FabricClientCommandSource source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        if (profiles.size() != 1) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        PlayerInfo recipient = source.getClient().getConnection().getPlayerInfo(profiles.iterator().next().getName());
        if (recipient == null) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        StartTicTacToeGameC2CPacket packet = new StartTicTacToeGameC2CPacket(source.getClient().getConnection().getLocalGameProfile().getName(), false);
        C2CPacketHandler.getInstance().sendPacket(packet, recipient);
        pendingInvites.add(recipient.getProfile().getName());
        source.sendFeedback(Component.translatable("c2cpacket.startTicTacToeGameC2CPacket.outgoing.invited", recipient.getProfile().getName()));
        return Command.SINGLE_SUCCESS;
    }

    public static void onStartTicTacToeGameC2CPacket(StartTicTacToeGameC2CPacket packet) {
        String sender = packet.sender();
        PlayerInfo opponent = Minecraft.getInstance().getConnection().getPlayerInfo(sender);
        if (opponent == null) {
            return;
        }

        if (packet.accept() && pendingInvites.remove(sender)) {
            TicTacToeGame game = new TicTacToeGame(opponent, TicTacToeGame.Mark.CROSS);
            activeGames.put(sender, game);

            MutableComponent component = Component.translatable("c2cpacket.startTicTacToeGameC2CPacket.incoming.accepted", sender);
            component.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctictactoe open " + sender))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/ctictactoe open " + sender))));
            Minecraft.getInstance().gui.getChat().addMessage(component);
            return;
        }

        MutableComponent component = Component.translatable("c2cpacket.startTicTacToeGameC2CPacket.incoming", sender);
        component
            .append(" [")
            .append(Component.translatable("c2cpacket.startTicTacToeGameC2CPacket.incoming.accept").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, ClientCommandHelper.registerCode(() -> {
                    TicTacToeGame game = new TicTacToeGame(opponent, TicTacToeGame.Mark.NOUGHT);
                    activeGames.put(sender, game);

                    StartTicTacToeGameC2CPacket acceptPacket = new StartTicTacToeGameC2CPacket(Minecraft.getInstance().getConnection().getLocalGameProfile().getName(), true);
                    try {
                        C2CPacketHandler.getInstance().sendPacket(acceptPacket, opponent);
                    } catch (CommandSyntaxException e) {
                        Minecraft.getInstance().gui.getChat().addMessage(Component.translationArg(e.getRawMessage()));
                    }

                    Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("c2cpacket.startTicTacToeGameC2CPacket.outgoing.accept"));
                })))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("c2cpacket.startTicTacToeGameC2CPacket.incoming.accept.hover")))))
            .append("]");
        Minecraft.getInstance().gui.getChat().addMessage(component);
    }

    private static int open(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        TicTacToeGame game = activeGames.get(name);
        if (game == null) {
            throw NO_GAME_WITH_PLAYER_EXCEPTION.create();
        }

        source.getClient().tell(() -> source.getClient().setScreen(new TicTacToeGameScreen(game)));
        return Command.SINGLE_SUCCESS;
    }

    public static void onPutTicTacToeMarkC2CPacket(PutTicTacToeMarkC2CPacket packet) {
        String sender = packet.sender();
        TicTacToeGame game = activeGames.get(sender);
        if (game == null) {
            return;
        }
        if (game.putMark(packet.x(), packet.y(), game.yourMarks.opposite())) {
            if (game.getWinner() == game.yourMarks.opposite()) {
                Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("c2cpacket.putTicTacToeMarkC2CPacket.incoming.lost", sender));
                activeGames.remove(sender);
                return;
            }
            MutableComponent component = Component.translatable("c2cpacket.putTicTacToeMarkC2CPacket.incoming", sender);
            component.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctictactoe open " + sender))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/ctictactoe open " + sender))));
            Minecraft.getInstance().gui.getChat().addMessage(component);
        }
    }

    private static class TicTacToeGame {
        public final PlayerInfo opponent;

        private final Mark[][] board = new Mark[3][3];
        private final Mark yourMarks;
        private boolean yourTurn;

        public TicTacToeGame(PlayerInfo opponent, Mark yourMarks) {
            this.opponent = opponent;
            this.yourMarks = yourMarks;
            this.yourTurn = yourMarks == Mark.CROSS;
        }

        public boolean putMark(byte x, byte y, Mark mark) {
            if (this.yourMarks == mark == this.yourTurn) {
                if (this.board[x][y] == null) {
                    this.board[x][y] = mark;
                    this.yourTurn = !this.yourTurn;
                    return true;
                }
            }
            return false;
        }

        public Mark getWinner() {
            for (byte x = 0; x < 3; x++) {
                if (this.board[x][0] == this.board[x][1] && this.board[x][1] == this.board[x][2]) {
                    return this.board[x][0];
                }
                if (this.board[0][x] == this.board[1][x] && this.board[1][x] == this.board[2][x]) {
                    return this.board[0][x];
                }
            }
            if (this.board[0][0] == this.board[1][1] && this.board[1][1] == this.board[2][2]) {
                return this.board[0][0];
            }
            if (this.board[0][2] == this.board[1][1] && this.board[1][1] == this.board[2][0]) {
                return this.board[0][2];
            }
            return null;
        }

        private enum Mark {
            NOUGHT(Component.translatable("ticTacToeGame.noughts")),
            CROSS(Component.translatable("ticTacToeGame.crosses"));

            private final Component name;

            Mark(Component name) {
                this.name = name;
            }

            public Mark opposite() {
                return this == NOUGHT ? CROSS : NOUGHT;
            }
        }
    }

    private static class TicTacToeGameScreen extends Screen {
        private final TicTacToeGame game;

        private static final ResourceLocation GRID_TEXTURE = new ResourceLocation("clientcommands", "textures/tic_tac_toe/grid.png");
        private static final ResourceLocation MARKS_TEXTURE = new ResourceLocation("clientcommands", "textures/tic_tac_toe/marks.png");

        private static final int gridSizeTexture = 512;
        private static final int markSizeTexture = 152;

        private static final int gridSize = 256;
        private static final int cellSize = 80;
        private static final int borderSize = 8;
        private static final int markSize = 76;
        private static final int padding = 2;

        private TicTacToeGameScreen(TicTacToeGame game) {
            super(Component.translatable("ticTacToeGame.title", game.opponent.getProfile().getName()));
            this.game = game;
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
            int startX = (this.width - gridSize) / 2;
            int startY = (this.height - gridSize) / 2;

            guiGraphics.drawString(this.font, this.title, startX, startY - 20, 0xff_ffffff);
            guiGraphics.drawString(this.font, Component.translatable("ticTacToeGame.playingWith", this.game.yourMarks.name), startX, startY - 10, 0xff_ffffff);

            guiGraphics.blit(GRID_TEXTURE, startX, startY, gridSize, gridSize, 0, 0, gridSizeTexture, gridSizeTexture, gridSizeTexture, gridSizeTexture);
            TicTacToeGame.Mark[][] board = this.game.board;

            for (byte x = 0; x < 3; x++) {
                for (byte y = 0; y < 3; y++) {
                    TicTacToeGame.Mark mark = board[x][y];
                    if (mark == null) {
                        continue;
                    }
                    int offset = switch (mark) {
                        case NOUGHT -> 0;
                        case CROSS -> markSizeTexture;
                    };
                    guiGraphics.blit(MARKS_TEXTURE, startX + (cellSize + borderSize) * x + padding, startY + (cellSize + borderSize) * y + padding, markSize, markSize, offset, 0, markSizeTexture, markSizeTexture, 2 * markSizeTexture, markSizeTexture);
                }
            }

        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int startX = (this.width - gridSize) / 2;
            int startY = (this.height - gridSize) / 2;
            if (mouseX < startX || mouseX > startX + gridSize || mouseY < startY || mouseY > startY + gridSize) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return false;
            }
            double relativeX = mouseX - startX;
            byte x = (byte) (relativeX / (cellSize + borderSize));
            if (relativeX > (cellSize + borderSize) * (x + 1) - borderSize) {
                return false;
            }
            double relativeY = mouseY - startY;
            byte y = (byte) (relativeY / (cellSize + borderSize));
            if (relativeY > (cellSize + borderSize) * (y + 1) - borderSize) {
                return false;
            }

            if (this.game.putMark(x, y, this.game.yourMarks)) {
                try {
                    PutTicTacToeMarkC2CPacket packet = new PutTicTacToeMarkC2CPacket(Minecraft.getInstance().getConnection().getLocalGameProfile().getName(), x, y);
                    C2CPacketHandler.getInstance().sendPacket(packet, this.game.opponent);
                } catch (CommandSyntaxException e) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translationArg(e.getRawMessage()));
                }
                if (this.game.getWinner() == this.game.yourMarks) {
                    activeGames.remove(this.game.opponent.getProfile().getName());
                }
                return true;
            }
            return false;
        }
    }
}
