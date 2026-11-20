package de.lyricsky;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ModCommands {
    private ModCommands() {}

    private static int login(CommandContext<FabricClientCommandSource> c, String clientId) {
        if (clientId != null) {
            Config.get().clientId = clientId;
            Config.save();
        }
        try {
            String url = SpotifyAuth.startLogin();
            c.getSource().sendFeedback(Text.literal("Spotify-Login: ").append(
                    Text.literal("[hier klicken]").styled(s -> s.withColor(Formatting.GREEN)
                            .withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)))));
        } catch (Exception e) {
            c.getSource().sendError(Text.literal(String.valueOf(e.getMessage())));
        }
        return 1;
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(literal("lyrics")
                // Kurzform: /lyrics login <ClientID>  (speichert die ID gleich mit)
                .then(literal("login")
                        .executes(c -> login(c, null))
                        .then(argument("clientid", StringArgumentType.word())
                                .executes(c -> login(c, StringArgumentType.getString(c, "clientid")))))
                .then(literal("logout").executes(c -> {
                    SpotifyAuth.logout();
                    c.getSource().sendFeedback(Text.literal("Ausgeloggt."));
                    return 1;
                }))
                .then(literal("toggle").executes(c -> {
                    Config cfg = Config.get();
                    cfg.enabled = !cfg.enabled;
                    Config.save();
                    c.getSource().sendFeedback(Text.literal(cfg.enabled ? "Lyrics an." : "Lyrics aus."));
                    return 1;
                }))
                .then(literal("follow").executes(c -> {
                    Config cfg = Config.get();
                    cfg.followView = !cfg.followView;
                    Config.save();
                    c.getSource().sendFeedback(Text.literal(cfg.followView
                            ? "Lyrics folgen deinem Blick (träge)."
                            : "Lyrics bleiben fest. Mit /lyrics here Richtung setzen."));
                    return 1;
                }))
                .then(literal("here").executes(c -> {
                    Config cfg = Config.get();
                    cfg.azimuth = MathHelper.wrapDegrees(c.getSource().getPlayer().getYaw());
                    cfg.followView = false;
                    Config.save();
                    c.getSource().sendFeedback(Text.literal("Lyrics hängen jetzt fest in dieser Richtung."));
                    return 1;
                }))
                .then(literal("offset")
                        .then(argument("ms", IntegerArgumentType.integer(-10000, 10000)).executes(c -> {
                            Config.get().offsetMs = IntegerArgumentType.getInteger(c, "ms");
                            Config.save();
                            c.getSource().sendFeedback(Text.literal("Offset: " + Config.get().offsetMs + " ms"));
                            return 1;
                        })))
                .then(literal("deadzone")
                        .then(argument("deg", FloatArgumentType.floatArg(0, 90)).executes(c -> {
                            Config.get().deadzone = FloatArgumentType.getFloat(c, "deg");
                            Config.save();
                            return 1;
                        })))
                .then(literal("elevation")
                        .then(argument("deg", FloatArgumentType.floatArg(0, 85)).executes(c -> {
                            Config.get().elevation = FloatArgumentType.getFloat(c, "deg");
                            Config.save();
                            return 1;
                        })))
                .then(literal("distance")
                        .then(argument("blocks", FloatArgumentType.floatArg(10, 200)).executes(c -> {
                            Config.get().distance = FloatArgumentType.getFloat(c, "blocks");
                            Config.save();
                            return 1;
                        })))
                .then(literal("scale")
                        .then(argument("factor", FloatArgumentType.floatArg(0.2f, 5)).executes(c -> {
                            Config.get().scale = FloatArgumentType.getFloat(c, "factor");
                            Config.save();
                            return 1;
                        }))));
    }
}
