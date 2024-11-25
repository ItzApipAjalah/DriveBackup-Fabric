package website.amwp.backup;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import website.amwp.backup.commands.DriveBackupCommand;
import com.google.api.client.auth.oauth2.Credential;
import website.amwp.backup.drive.DriveService;
import website.amwp.backup.config.BackupConfig;
import com.google.api.client.http.FileContent;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import net.minecraft.text.Text;

public class Drivebackup implements ModInitializer {
	public static final String MOD_ID = "drivebackup";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private Timer backupTimer;
	private Drive driveService;
	private static Drivebackup instance;
	private static final int BUFFER_SIZE = 4096; // 4KB buffer
	private static final long THROTTLE_DELAY = 50; // 50ms delay between chunks
	private static final ExecutorService backupExecutor = Executors.newSingleThreadExecutor();
	private static final net.minecraft.server.MinecraftServer server = null;

	@Override
	public void onInitialize() {
		instance = this;
		LOGGER.info("Initializing DriveBackup mod");

		// Register the command
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			DriveBackupCommand.register(dispatcher);
		});

		try {
			setupBackupSchedule();
			registerServerEvents();
		} catch (Exception e) {
			LOGGER.error("Failed to initialize DriveBackup", e);
		}

		// Add shutdown hook to clean up executor
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			backupExecutor.shutdown();
		}));
	}

	private void initializeDriveService() throws Exception {
		if (driveService == null) {
			final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

			// Initialize the Drive service
			driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials())
					.setApplicationName("Minecraft Backup Mod")
					.build();
		}
	}

	private void setupBackupSchedule() {
		backupTimer = new Timer("BackupTimer", true);
		backupTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				performBackup();
			}
		}, BackupConfig.getInstance().getBackupInterval(),
				BackupConfig.getInstance().getBackupInterval());
	}

	private void registerServerEvents() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Server stopping, performing final backup");
			performBackup();
			if (backupTimer != null) {
				backupTimer.cancel();
			}
		});
	}

	public static void performManualBackup() {
		if (instance != null) {
			instance.performBackup();
		}
	}

	private void performBackup() {
		backupExecutor.submit(() -> {
			try {
				BackupConfig config = BackupConfig.getInstance();
				if (!config.isAuthenticated()) {
					LOGGER.warn("Backup skipped: Not authenticated. Use /drivebackup auth to set up Google Drive.");
					return;
				}

				// Announce backup start
				broadcastMessage("§6[Backup] Starting backup process. Server might experience slight lag...");

				initializeDriveService();
				Path gameDir = FabricLoader.getInstance().getGameDir();

				// Backup specified worlds
				for (String worldName : config.getWorldsToBackup()) {
					Path worldPath = gameDir.resolve(worldName);
					if (Files.exists(worldPath) && Files.isDirectory(worldPath)) {
						broadcastMessage("§7[Backup] Backing up world: " + worldName);
						backupDirectory(worldPath.toFile(), "worlds/" + worldName);
						Thread.sleep(1000); // Wait 1 second between world backups
					} else {
						LOGGER.warn("World '{}' not found", worldName);
					}
				}

				// Backup mods if enabled
				if (config.isBackupMods()) {
					Path modsDir = gameDir.resolve("mods");
					if (Files.exists(modsDir)) {
						broadcastMessage("§7[Backup] Backing up mods folder...");
						backupDirectory(modsDir.toFile(), "mods");
					}
				}

				config.setLastBackupTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
				broadcastMessage("§a[Backup] Backup completed successfully!");
				
			} catch (Exception e) {
				LOGGER.error("Failed to perform backup: {}", e.getMessage());
				broadcastMessage("§c[Backup] Backup failed: " + e.getMessage());
			}
		});
	}

	private void backupDirectory(File directory, String backupType) {
		try {
			if (!directory.exists() || !directory.isDirectory()) {
				LOGGER.error("Invalid directory: {}", directory.getAbsolutePath());
				return;
			}

			String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
			String zipFileName = backupType.replace('/', '-') + "_" + timestamp + ".zip";

			// Create backups directory and zip file
			Path backupsDir = FabricLoader.getInstance().getGameDir().resolve("backups");
			Files.createDirectories(backupsDir);
			File zipFile = backupsDir.resolve(zipFileName).toFile();

			// Create zip file
			broadcastMessage("§7[Backup] Creating zip file for " + backupType + "...");
			zipDirectory(directory, zipFile);

			if (!zipFile.exists() || zipFile.length() == 0) {
				LOGGER.error("Failed to create backup zip file");
				return;
			}

			// Upload to Google Drive
			broadcastMessage("§7[Backup] Uploading " + zipFileName + " to Google Drive...");
			uploadToGoogleDrive(zipFile, zipFileName);
			broadcastMessage("§a[Backup] Successfully uploaded " + backupType);

			// Cleanup old backups
			cleanupOldBackups(backupType, 1);

		} catch (Exception e) {
			LOGGER.error("Failed to backup {}: {}", backupType, e.getMessage());
			broadcastMessage("§c[Backup] Failed to backup " + backupType + ": " + e.getMessage());
		}
	}

	private void uploadToGoogleDrive(File zipFile, String fileName) throws Exception {
		// Create file metadata
		com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
		fileMetadata.setName(fileName);

		String folderId = findOrCreateFolder("MinecraftBackups");
		if (folderId != null) {
			fileMetadata.setParents(Collections.singletonList(folderId));
		}

		// Create file content with default chunk size (which is usually 10MB)
		FileContent mediaContent = new FileContent("application/zip", zipFile);

		// Upload file with progress monitoring
		driveService.files().create(fileMetadata, mediaContent)
				.setFields("id")
				.execute();
	}

	private void zipDirectory(File directoryToZip, File zipFile) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(zipFile);
			 BufferedOutputStream bos = new BufferedOutputStream(fos);
			 ZipOutputStream zos = new ZipOutputStream(bos)) {
			
			zos.setLevel(1); // Use fastest compression level to reduce CPU usage
			zipFile(directoryToZip, directoryToZip.getName(), zos);
		}
	}

	private void zipFile(File fileToZip, String fileName, ZipOutputStream zos) throws IOException {
		if (fileToZip.isHidden()) {
			return;
		}

		if (fileToZip.isDirectory()) {
			if (fileName.endsWith("/")) {
				zos.putNextEntry(new ZipEntry(fileName));
			} else {
				zos.putNextEntry(new ZipEntry(fileName + "/"));
			}
			zos.closeEntry();

			File[] children = fileToZip.listFiles();
			if (children != null) {
				for (File childFile : children) {
					zipFile(childFile, fileName + "/" + childFile.getName(), zos);
					try {
						Thread.sleep(10); // Small delay between files
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
			return;
		}

		try (FileInputStream fis = new FileInputStream(fileToZip);
			 BufferedInputStream bis = new BufferedInputStream(fis)) {
			
			ZipEntry zipEntry = new ZipEntry(fileName);
			zos.putNextEntry(zipEntry);
			
			byte[] buffer = new byte[BUFFER_SIZE];
			int length;
			long bytesWritten = 0;
			
			while ((length = bis.read(buffer)) >= 0) {
				zos.write(buffer, 0, length);
				bytesWritten += length;
				
				// Throttle after every 1MB
				if (bytesWritten >= 1024 * 1024) {
					try {
						Thread.sleep(THROTTLE_DELAY);
						bytesWritten = 0;
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}
	}

	private void cleanupOldBackups(String backupType, int keepCount) {
		try {
			// Cleanup local backups
			
			Path backupsDir = FabricLoader.getInstance().getGameDir().resolve("backups");
			if (Files.exists(backupsDir)) {
				String prefix = backupType.replace('/', '-');
				File[] backupFiles = backupsDir.toFile()
						.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".zip"));

				if (backupFiles != null && backupFiles.length > keepCount) {
					Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified));
					for (int i = 0; i < backupFiles.length - keepCount; i++) {
						Files.deleteIfExists(backupFiles[i].toPath());
					}
				}
			}

			// Cleanup Google Drive backups
			String folderId = findOrCreateFolder("MinecraftBackups");
			if (folderId != null) {
				cleanupGoogleDriveBackups(backupType, folderId, keepCount);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to cleanup old backups: {}", e.getMessage());
		}
	}

	private void cleanupGoogleDriveBackups(String backupType, String folderId, int keepCount) throws IOException {
		String query = String.format(
				"name contains '%s' and '%s' in parents and mimeType='application/zip' and trashed=false",
				backupType.replace('/', '-'), folderId);

		com.google.api.services.drive.model.FileList result = driveService.files().list()
				.setQ(query)
				.setOrderBy("modifiedTime")
				.setFields("files(id, modifiedTime)")
				.execute();

		List<com.google.api.services.drive.model.File> files = result.getFiles();
		if (files != null && files.size() > keepCount) {
			files.sort((a, b) -> a.getModifiedTime().toStringRfc3339()
					.compareTo(b.getModifiedTime().toStringRfc3339()));

			for (int i = 0; i < files.size() - keepCount; i++) {
				driveService.files().delete(files.get(i).getId()).execute();
			}
		}
	}

	private String findOrCreateFolder(String folderName) {
		try {
			// First try to find the folder
			String query = "mimeType='application/vnd.google-apps.folder' and name='" + folderName
					+ "' and trashed=false";
			com.google.api.services.drive.model.File folder = driveService.files().list()
					.setQ(query)
					.setSpaces("drive")
					.setFields("files(id)")
					.execute()
					.getFiles()
					.stream()
					.findFirst()
					.orElse(null);

			if (folder != null) {
				return folder.getId();
			}

			// If folder doesn't exist, create it
			com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
			folderMetadata.setName(folderName);
			folderMetadata.setMimeType("application/vnd.google-apps.folder");

			folder = driveService.files().create(folderMetadata)
					.setFields("id")
					.execute();

			return folder.getId();
		} catch (Exception e) {
			LOGGER.error("Failed to find or create folder: {}", e.getMessage());
			return null;
		}
	}

	private Credential getCredentials() throws Exception {
		return DriveService.getCredentials();
	}

	// Add this helper method to broadcast messages to all players
	private void broadcastMessage(String message) {
		try {
			// Try to get the server instance from the mod loader
			net.minecraft.server.MinecraftServer currentServer = (net.minecraft.server.MinecraftServer) 
				FabricLoader.getInstance().getGameInstance();
			
			if (currentServer != null) {
				currentServer.execute(() -> {
					currentServer.getPlayerManager().broadcast(
						Text.literal(message), false);
				});
			}
		} catch (Exception e) {
			// If broadcasting fails, just log it
			LOGGER.info(message);
		}
	}
}