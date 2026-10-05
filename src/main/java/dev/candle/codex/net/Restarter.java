package dev.candle.codex.net;

import dev.candle.codex.config.Config;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Fabric can only load mods at start-up, so after installing a mod we relaunch the game with the exact
 * same JVM command line (same Java, same game version, same profile/arguments). The new process is started
 * from a shutdown hook, i.e. after the old game has saved and exited.
 */
public final class Restarter {
    private Restarter() {}

    /** @return null on success, otherwise a message to show the user. */
    public static String restart(Minecraft mc) {
        try {
            ProcessHandle.Info info = ProcessHandle.current().info();
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path gameDir = Config.gameDir();
            ProcessBuilder pb;

            if (windows) {
                String cmdLine = info.commandLine().orElse(null);
                if (cmdLine == null || cmdLine.isBlank()) return unsupported();
                Path bat = Files.createTempFile("candle-restart-", ".bat");
                String script = "@echo off\r\n"
                        + "ping -n 4 127.0.0.1 >nul\r\n"
                        + "cd /d \"" + gameDir.toAbsolutePath() + "\"\r\n"
                        + cmdLine + "\r\n"
                        + "del \"%~f0\"\r\n";
                Files.writeString(bat, script, StandardCharsets.UTF_8);
                pb = new ProcessBuilder("cmd.exe", "/c", bat.toAbsolutePath().toString());
            } else {
                String cmd = info.command().orElse(null);
                String[] args = info.arguments().orElse(null);
                if (cmd == null || args == null || args.length == 0) return unsupported();
                List<String> full = new ArrayList<>();
                full.add(cmd);
                full.addAll(Arrays.asList(args));
                pb = new ProcessBuilder(full);
            }
            pb.directory(gameDir.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            final ProcessBuilder launch = pb;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (!windows) Thread.sleep(1500);
                    launch.start();
                } catch (Exception ignored) {
                }
            }, "candle-relaunch"));

            Config.save();
            mc.execute(mc::stop);
            return null;
        } catch (IOException | RuntimeException e) {
            return "Could not restart automatically: " + Modrinth.describe(e);
        }
    }

    private static String unsupported() {
        return "Automatic restart is not supported here. The mod is installed; please restart Minecraft yourself.";
    }
}
