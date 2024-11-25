DriveBackup Mod Tutorial
=======================

1. Getting Google Drive Credentials
---------------------------------
a. Visit Google Cloud Console (https://console.cloud.google.com)
b. Create new project or select existing one
c. Enable APIs:
   - Search for "Google Drive API"
   - Click "Enable"
d. Create credentials:
   - Go to "Credentials" tab
   - Click "Create Credentials"
   - Choose "OAuth client ID"
   - Application type: "Desktop app"
   - Name it anything you want
   - Download the JSON file
e. Rename downloaded file to "credentials.json"
f. Place it in: config/drivebackup/credentials.json

2. First Time Setup
------------------
a. Start your Minecraft server with the mod installed
b. Type: /drivebackup auth
c. Click the link in chat
d. Login to your Google account
e. Allow permissions
f. Copy the authorization code
g. Type: /drivebackup code <paste_code_here>

3. Configure Backup Settings
--------------------------
Add world to backup:
/drivebackup config addworld <worldname>
Example: /drivebackup config addworld Kizu-serv

Set backup interval (in minutes):
/drivebackup config interval <minutes>
Example: /drivebackup config interval 120 (for 2 hours)

Toggle mods backup:
/drivebackup config togglemods

4. Manual Backup
---------------
To start backup manually:
/drivebackup backup

5. Check Configuration
---------------------
View all settings:
/drivebackup config status

6. Important Notes
-----------------
- Backups are saved in server's "backups" folder
- Files are also uploaded to Google Drive in "MinecraftBackups" folder
- Server might lag slightly during backup
- Old backups are automatically deleted
- Default backup interval is 1 hour
- All commands require operator permission (level 4)

7. Common Issues
---------------
If authentication fails:
- Make sure credentials.json is in the correct folder
- Try the auth process again
- Check server logs for errors

If backup fails:
- Check if world name is correct
- Ensure enough disk space
- Verify Google Drive permissions

8. Backup Process
----------------
When backup runs, you'll see these messages:
- Backup starting
- Creating zip files
- Uploading to Google Drive
- Backup complete

The mod automatically:
- Creates zip files of worlds/mods
- Uploads them to Google Drive
- Cleans up old backups
- Shows progress in chat 