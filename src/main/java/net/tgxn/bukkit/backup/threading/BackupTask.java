/*
 *  Backup - CraftBukkit server Backup plugin (continued)
 *  Copyright (C) 2011 Domenic Horner <https://github.com/gamerx/Backup>
 *  Copyright (C) 2011 Lycano <https://github.com/gamerx/Backup>
 *
 *  Backup - CraftBukkit server Backup plugin (original author)
 *  Copyright (C) 2011 Kilian Gaertner <https://github.com/Meldanor/Backup>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.tgxn.bukkit.backup.threading;

import java.util.List;
import java.io.FileFilter;
import org.bukkit.entity.Player;
import net.tgxn.bukkit.backup.BackupMain;
import net.tgxn.bukkit.backup.config.Settings;
import net.tgxn.bukkit.backup.config.Strings;
import net.tgxn.bukkit.backup.utils.FileUtils;
import net.tgxn.bukkit.backup.utils.LogUtils;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.logging.Level;

import static net.tgxn.bukkit.backup.utils.FileUtils.FILE_SEPARATOR;

/**
 * The Task copies and backups the worlds and delete older backups. This task
 * is only runes once in backup and doing all the thread safe options.
 * The PrepareBackupTask and BackupTask are two threads to find a compromise between
 * security and performance.
 *
 * @author Kilian Gaertner
 */
public class BackupTask implements Runnable {
    
    private final Settings settings;
    private Strings strings;
    private Plugin plugin;
    private final LinkedList<String> worldsToBackup;
    private final Server server;
    public List<String> skippedPlugins;

    /**
     * The main backup constructor.
     * 
     * @param settings The settings object, to get the plugins settings.
     * @param strings Strings object, for all string values
     * @param worldsToBackup The list of worlds that need to be backed up.
     * @param server The server we are backing up.
     */
    public BackupTask(Settings settings, Strings strings, LinkedList<String> worldsToBackup, Server server) {
        this.settings = settings;
        this.worldsToBackup = worldsToBackup;
        this.server = server;
        this.plugin = server.getPluginManager().getPlugin("Backup");
        this.strings = strings;
    }
    
    @Override
    public void run() {

        // This will catch any backup errors.
        try {
            // Run the backup.
            backup();
        } catch (Exception ex) {
            /** @TODO create exception classes **/
            ex.printStackTrace(System.out);
        }
    }

    /**
     * Run the backup.
     * 
     * @throws Exception 
     */
    public void backup() throws Exception {

        // Load preferences.
        String backupDirName = settings.getStringProperty("backuppath").concat(FILE_SEPARATOR);
        boolean ShouldZIP = settings.getBooleanProperty("zipbackup");
        boolean BackupWorlds = settings.getBooleanProperty("backupworlds");
        boolean BackupPlugins = settings.getBooleanProperty("backupplugins");
        boolean backupeverything = settings.getBooleanProperty("backupeverything");

        // Get skipped plugins from config.
        skippedPlugins = Arrays.asList(settings.getStringProperty("skipplugins").split(";"));


        // Folder to store backup in. IE: "backups/30092011-142238"
        backupDirName = backupDirName.concat(getFolderName());

        // We are performing a full server backup.
        if (backupeverything) {

            // Setup FileFilter to exclude the backups path.
            FileFilter ff = new FileFilter() {

                /**
                 * Files to accept/deny.
                 */
                @Override
                public boolean accept(File f) {

                    // Return backuppath.
                    return !f.getName().equals(settings.getStringProperty("backuppath"));
                }
            };

            // Setup Source and destination DIR's.
            File srcDIR = new File("./");
            File destDIR = new File(backupDirName);

            // Copy this world into the backup directory, in a folder called the worlds name.
            try {
                FileUtils.copyDirectory(srcDIR, destDIR, ff, true);
            } catch (FileNotFoundException ex) {
                ex.printStackTrace(System.out);
            } catch (IOException e) {
                LogUtils.sendLog("Error with full backup");
                /** @TODO create exception classes **/
                e.printStackTrace(System.out);
                server.broadcastMessage(strings.getString("backupfailed"));
            }
            
        } else {

            // If there are worlds to backup, and we are performing world backups.
            if ((worldsToBackup != null) && (BackupWorlds)) {

                // While we have a worlds to backup.
                while (!worldsToBackup.isEmpty()) {

                    // Remove first world from the array and put it into a var.
                    String worldName = worldsToBackup.removeFirst();

                    // Copy this world into the backup directory, in a folder called the worlds name.
                    try {
                        FileUtils.copyDirectory(worldName, backupDirName.concat(FILE_SEPARATOR).concat(worldName));
                    } catch (FileNotFoundException ex) {
                        ex.printStackTrace(System.out);
                    } catch (IOException e) {
                        LogUtils.sendLog(Level.WARNING, strings.getStringWOPT("errorcreatetemp", worldName), true);
                        /** @TODO create exception classes **/
                        e.printStackTrace(System.out);
                        server.broadcastMessage(strings.getString("backupfailed"));
                    }
                }
            } else {
                LogUtils.sendLog(Level.INFO, strings.getString("skipworlds"), true);
            }

            // We are backing up plugins.
            if (BackupPlugins) {

                // Setup FileFilter to exclude the backups path.
                FileFilter ffplugins = new FileFilter() {

                    /**
                     * Files to accept/deny.
                     */
                    @Override
                    public boolean accept(File f) {

                        // Check if there are ignored plugins
                        if (skippedPlugins.size() > 0 && !skippedPlugins.get(0).isEmpty()) {

                            // Loop each plugin.
                            for (int i = 0; i < skippedPlugins.size(); i++) {

                                // Check if the current plugin matches the 
                                if (skippedPlugins.get(i).equals(f.getName())) {
                                    return false;
                                }
                            }
                        }
                        return true;
                    }
                };

                // Setup Source and destination DIR's.
                File srcDIR = new File("plugins");
                File destDIR = new File(backupDirName.concat(FILE_SEPARATOR).concat("plugins"));

                // Copy this world into the backup directory, in a folder called the worlds name.
                try {
                    if (skippedPlugins.size() > 0 && !skippedPlugins.get(0).isEmpty()) {
                        // Log what plugins are disabled.
                        LogUtils.sendLog(strings.getString("disabledplugins"));
                        LogUtils.sendLog(skippedPlugins.toString());
                        
                    }
                    FileUtils.copyDirectory(srcDIR, destDIR, ffplugins, true);
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace(System.out);
                } catch (IOException e) {
                    LogUtils.sendLog("Error backing up plugins.");
                    /** @TODO create exception classes **/
                    e.printStackTrace(System.out);
                    server.broadcastMessage(strings.getString("backupfailed"));
                }
                
                
            } else {
                LogUtils.sendLog(Level.INFO, strings.getString("skipplugins"), true);
            }
            
        }

        // Should we ZIP the backup.
        if (ShouldZIP) {

            // Add backup folder to a ZIP.
            FileUtils.zipDir(backupDirName, backupDirName);

            // Delete the original backup directory.
            FileUtils.deleteDirectory(new File(backupDirName));
        }

        // Delete old backups.
        deleteOldBackups();

        // Clean up.
        finish();
        
    }

    /**
     * Get the name of this backups folder.
     * 
     * @return The name, as a string.
     */
    private String getFolderName() {

        // Get the calendar, and initalize the date format string.
        Calendar calendar = Calendar.getInstance();
        String formattedDate;

        // Java string (and date) formatting:
        // http://download.oracle.com/javase/1.5.0/docs/api/java/util/Formatter.html#syntax
        try {
            formattedDate = String.format(settings.getStringProperty("dateformat"), calendar);
        } catch (Exception e) {
            LogUtils.sendLog(Level.WARNING, strings.getString("errordateformat"), true);
            formattedDate = String.format("%1$td%1$tm%1$tY-%1$tH%1$tM%1$tS", calendar);

            // @TODO write exception class
            System.out.println(e);
        }
        return formattedDate;
    }

    /**
     * Check whether there are more backups as allowed to store. 
     * When this case is true, it deletes oldest ones.
     */
    private void deleteOldBackups() {
        try {

            // Get properties.
            File backupDir = new File(settings.getStringProperty("backuppath"));
            final int maxBackups = settings.getIntProperty("maxbackups");

            // Store all backup files in an array.
            File[] filesList = backupDir.listFiles();

            // If the amount of files exceeds the max backups to keep.
            if (filesList.length > maxBackups) {
                ArrayList<File> backupList = new ArrayList<File>(filesList.length);
                backupList.addAll(Arrays.asList(filesList));
                
                int maxModifiedIndex;
                long maxModified;

                //Remove the newst backups from the list.
                for (int i = 0; i < maxBackups; ++i) {
                    maxModifiedIndex = 0;
                    maxModified = backupList.get(0).lastModified();
                    for (int j = 1; j < backupList.size(); ++j) {
                        File currentFile = backupList.get(j);
                        if (currentFile.lastModified() > maxModified) {
                            maxModified = currentFile.lastModified();
                            maxModifiedIndex = j;
                        }
                    }
                    backupList.remove(maxModifiedIndex);
                }

                // Inform the user what backups are being deleted.
                LogUtils.sendLog(strings.getString("removeold"));
                LogUtils.sendLog(Arrays.toString(backupList.toArray()));

                // Finally delete the backups.
                for (File backupToDelete : backupList) {
                    backupToDelete.delete();
                }
            }
        } catch (Exception e) {
            //@TODO write exception class
            e.printStackTrace(System.out);
        }
    }

    /**
     * Creates a temporary Runnable that is running on the main thread by the scheduler to prevent thread problems.
     */
    private void finish() {
        Runnable run = new Runnable() {
            
            @Override
            public void run() {
                if (settings.getBooleanProperty("enableautosave")) {
                    server.dispatchCommand(server.getConsoleSender(), "save-on");
                }

                // Inform players backup has finished.
                String completedBackupMessage = strings.getString("backupfinished");
                
                if (completedBackupMessage != null && !completedBackupMessage.trim().isEmpty()) {

                    // Verify Permissions
                    if (BackupMain.Permissions != null) {

                        // Get all players.
                        Player[] players = server.getOnlinePlayers();

                        // Loop through all online players.
                        for (int i = 0; i < players.length; i++) {
                            Player currentplayer = players[i];

                            // If the current player has the right permissions, notify them.
                            if (BackupMain.Permissions.has(currentplayer, "backup.notify")) {
                                currentplayer.sendMessage(completedBackupMessage);
                            }
                        }

                        // Send message to log, to be sure.
                        LogUtils.sendLog(completedBackupMessage);
                        
                    } else {

                        // If there are no permissions, notify all.
                        server.broadcastMessage(completedBackupMessage);
                    }
                }
            }
        };
        server.getScheduler().scheduleSyncDelayedTask(plugin, run);
    }
}
