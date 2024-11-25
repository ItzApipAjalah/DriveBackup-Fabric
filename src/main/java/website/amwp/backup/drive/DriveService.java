package website.amwp.backup.drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class DriveService {
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static GoogleAuthorizationCodeFlow flow;
    private static Credential credential;

    private static void initializeFlow() throws Exception {
        if (flow != null) return;

        Path configDir = FabricLoader.getInstance().getConfigDir();
        File credentialsFile = configDir.resolve("drivebackup/credentials.json").toFile();

        if (!credentialsFile.exists()) {
            throw new FileNotFoundException("Credentials file not found. Please run /drivebackup auth first");
        }

        // Load client secrets from config directory
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                GsonFactory.getDefaultInstance(), 
                new FileReader(credentialsFile));

        flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(
                    new File(configDir.toString(), TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
    }

    public static String getAuthorizationUrl() throws Exception {
        initializeFlow();
        return flow.newAuthorizationUrl()
                .setRedirectUri("urn:ietf:wg:oauth:2.0:oob")
                .build();
    }

    public static void authorizeWithCode(String authCode) throws Exception {
        initializeFlow();
        credential = flow.createAndStoreCredential(
                flow.newTokenRequest(authCode)
                    .setRedirectUri("urn:ietf:wg:oauth:2.0:oob")
                    .execute(),
                "user");
    }

    public static Credential getCredentials() throws Exception {
        initializeFlow(); // Make sure flow is initialized
        
        if (credential == null) {
            credential = flow.loadCredential("user");
            if (credential == null) {
                throw new FileNotFoundException("No credentials found. Please run /drivebackup auth first");
            }
        }
        return credential;
    }
}