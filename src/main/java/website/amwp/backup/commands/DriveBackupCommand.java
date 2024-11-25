package website.amwp.backup.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import website.amwp.backup.Drivebackup;
import website.amwp.backup.config.BackupConfig;
import website.amwp.backup.drive.DriveService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class DriveBackupCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("drivebackup")
            .requires(source -> source.hasPermissionLevel(4))
            .then(literal("auth")
                .executes(context -> {
                    try {
                        String authUrl = DriveService.getAuthorizationUrl();
                        Text message = Text.literal("§6Click here to authorize Google Drive backup")
                            .setStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, authUrl))
                                .withUnderline(true));
                        context.getSource().sendMessage(message);
                        context.getSource().sendMessage(Text.literal("§7After authorizing, copy the code and use:"));
                        context.getSource().sendMessage(Text.literal("§7/drivebackup code <authorization_code>"));
                        return 1;
                    } catch (Exception e) {
                        context.getSource().sendError(Text.literal("Failed to get authorization URL: " + e.getMessage()));
                        return 0;
                    }
                }))
            .then(literal("code")
                .then(argument("auth_code", StringArgumentType.greedyString())
                    .executes(context -> {
                        String authCode = StringArgumentType.getString(context, "auth_code");
                        try {
                            DriveService.authorizeWithCode(authCode);
                            BackupConfig.getInstance().setAuthenticated(true);
                            context.getSource().sendMessage(Text.literal("§aSuccessfully authorized Google Drive backup!"));
                            return 1;
                        } catch (Exception e) {
                            context.getSource().sendError(Text.literal("Failed to authorize: " + e.getMessage()));
                            return 0;
                        }
                    })))
            .then(literal("backup")
                .executes(context -> {
                    try {
                        context.getSource().sendMessage(Text.literal("§6Starting manual backup..."));
                        Drivebackup.performManualBackup();
                        String time = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        BackupConfig.getInstance().setLastBackupTime(time);
                        context.getSource().sendMessage(Text.literal("§aBackup completed successfully!"));
                        return 1;
                    } catch (Exception e) {
                        context.getSource().sendError(Text.literal("Failed to perform backup: " + e.getMessage()));
                        return 0;
                    }
                }))
            .then(literal("config")
                .then(literal("interval")
                    .then(argument("minutes", LongArgumentType.longArg(1))
                        .executes(context -> {
                            long minutes = LongArgumentType.getLong(context, "minutes");
                            BackupConfig.getInstance().setBackupInterval(minutes * 60000);
                            context.getSource().sendMessage(Text.literal("§aBackup interval set to " + minutes + " minutes"));
                            return 1;
                        })))
                .then(literal("addworld")
                    .then(argument("worldname", StringArgumentType.word())
                        .executes(context -> {
                            String worldName = StringArgumentType.getString(context, "worldname");
                            BackupConfig.getInstance().addWorldToBackup(worldName);
                            context.getSource().sendMessage(Text.literal("§aAdded world '" + worldName + "' to backup list"));
                            return 1;
                        })))
                .then(literal("removeworld")
                    .then(argument("worldname", StringArgumentType.word())
                        .executes(context -> {
                            String worldName = StringArgumentType.getString(context, "worldname");
                            BackupConfig.getInstance().removeWorldFromBackup(worldName);
                            context.getSource().sendMessage(Text.literal("§aRemoved world '" + worldName + "' from backup list"));
                            return 1;
                        })))
                .then(literal("togglemods")
                    .executes(context -> {
                        BackupConfig config = BackupConfig.getInstance();
                        config.setBackupMods(!config.isBackupMods());
                        context.getSource().sendMessage(Text.literal("§aMods backup " + 
                            (config.isBackupMods() ? "enabled" : "disabled")));
                        return 1;
                    }))
                .then(literal("status")
                    .executes(context -> {
                        BackupConfig config = BackupConfig.getInstance();
                        context.getSource().sendMessage(Text.literal("§6Backup Configuration:"));
                        context.getSource().sendMessage(Text.literal("§7- Authenticated: " + 
                            (config.isAuthenticated() ? "§aYes" : "§cNo")));
                        context.getSource().sendMessage(Text.literal("§7- Backup Interval: " + 
                            (config.getBackupInterval() / 60000) + " minutes"));
                        context.getSource().sendMessage(Text.literal("§7- Backup Mods: " + 
                            (config.isBackupMods() ? "§aYes" : "§cNo")));
                        context.getSource().sendMessage(Text.literal("§7- Worlds to backup: " + 
                            String.join(", ", config.getWorldsToBackup())));
                        if (!config.getLastBackupTime().isEmpty()) {
                            context.getSource().sendMessage(Text.literal("§7- Last backup: " + 
                                config.getLastBackupTime()));
                        }
                        return 1;
                    }))));
    }
} 