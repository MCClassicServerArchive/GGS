/*******************************************************************************
 * Copyright (c) 2013 MCForge.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.ep.ggs.world;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ep.ggs.API.level.LevelLoadEvent;
import com.ep.ggs.API.level.LevelPreLoadEvent;
import com.ep.ggs.API.level.LevelUnloadEvent;
import com.ep.ggs.iomodel.Player;
import com.ep.ggs.server.Server;
import com.ep.ggs.system.ticker.Tick;
import com.ep.ggs.world.backup.BackupRunner;
import com.ep.ggs.world.classicmodel.ClassicLevel;
import com.ep.ggs.world.generator.Generator;
import com.ep.ggs.world.generator.classicmodel.ClassicGenerator;
import com.ep.ggs.world.generator.classicmodel.FlatGrass;


public class ClassicLevelHandler implements LevelHandler {

    private List<Level> levels = new CopyOnWriteArrayList<Level>();
    
    private Server server;

    private BackupRunner backup;
    
    /**
     * The constructor for a new level handler
     * @param server
     *              The server that requires a level handler
     */
    public ClassicLevelHandler(Server server) {
        this.server = server;
        server.getTicker().addTick(new Saver());
        backup = new BackupRunner(server);
        startBackup();
    }
    
    /**
     * Start running the backup runner.
     * @see BackupRunner#startRunning()
     */
    public void startBackup() {
        backup.startRunning();
    }
    
    /**
     * Stop running the backup runner.
     * @see BackupRunner#stopRunning()
     */
    public void stopBackup() {
        backup.stopRunning();
    }

    /**
     * Get a list of levels
     * @return
     *        A list of levels
     */
    public final List<Level> getLevelList() {
        return levels;
    }

    /**
     * Create a new level
     * @param name
     *            The name of the level
     * @param width
     *             The width (Max X)
     * @param height
     *              The height (Max Y)
     * @param depth
     *              The depth (Max Z) 
     */
    public void newClassicLevel(String name, short width, short height, short length)
    {
        newClassicLevel(name, width, height, length, new FlatGrass(server));
    }

    public void newClassicLevel(String name, short width, short height, short length, ClassicGenerator gen) {
        if(!new File("levels/" + name + ".ggs").exists())
        {
            Level level = new ClassicLevel(width, height, length);
            level.setName(name);
            level.generateWorld(gen);
            try {
                level.save();
            } catch (IOException e) {
                server.logError(e);
            }
        }
    }

    /**
     * Find a level with the given name.
     * If part of a name is given, then it will try to find the
     * full name
     * @param name
     *            The name of the level
     * @return
     *         The level found. If more than 1 level is found, then
     *         it will return null
     */
    public Level findLevel(String name) {
        Level temp = null;
        for (int i = 0; i < levels.size(); i++) {
            if ((levels.get(i).getName()).equalsIgnoreCase(name))
                return levels.get(i);
            if ((levels.get(i).getName()).contains(name) && temp == null)
                temp = levels.get(i);
            else if ((levels.get(i).getName()).contains(name) && temp != null)
                return null;
        }
        return temp;
    }

    /**
     * Get the players in a particular level
     * @param level
     *             The level to check
     * @return
     *        A list of players in that level.
     */
    public ArrayList<Player> getPlayers(Level level) {
        ArrayList<Player> temp = new ArrayList<Player>();
        for (int i = 0; i < server.getClassicPlayers().size(); i++)
            if (server.getClassicPlayers().get(i).getLevel() == level)
                temp.add(server.getClassicPlayers().get(i));
        return temp;
    }
    /**
     * Load all the levels in the 
     * "levels" folder
     */
    public void loadClassicLevels()
    {
        levels.clear();
        File levelsFolder = new File("levels");
        File[] levelFiles = levelsFolder.listFiles();
        for(File f : levelFiles) {
            if (f.getName().endsWith(".ggs") || f.getName().endsWith(".lvl") || f.getName().endsWith(".dat"))
                loadClassicLevel(levelsFolder.getPath() + "/" + f.getName());
        }
    }

    /**
     * Load a level and have it return the loaded
     * level
     * @param filename
     *                The .ggs file to load.
     *                If a .dat file is presented, then it will
     *                be converted to a .ggs
     * @return
     *         The loaded level.
     */
    public ClassicLevel loadClassicLevel(String filename) {
        ClassicLevel l = new ClassicLevel();
        LevelPreLoadEvent event1 = new LevelPreLoadEvent(filename);
        server.getEventSystem().callEvent(event1);
        if (event1.isCancelled()) {
            if ((l = event1.getReplacement()) == null)
                return null;
            else {
                levels.add(l);
                return l;
            }
        }
        try {
            long startTime = System.nanoTime();
            l.load(filename, server);
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            server.log("Loading took: " + duration + "ms");
            LevelLoadEvent event = new LevelLoadEvent(l);
            server.getEventSystem().callEvent(event);
            if(event.isCancelled()) {
                server.log("Loading of level " + l.getName() + " was canceled by " + event.getCanceler());
                l.unload(server); //Dispose the level
                l = null;
                return null;
            }
        } catch (Exception e) {
            server.log("ERROR LOADING LEVEL!");
            e.printStackTrace();
        }
        if (l != null) {
            server.log("[" + l.getName() + "] Loaded!");
            levels.add(l);
        }
        return l;
    }
    /**
     * Unload a level
     * This method will call {@link Level#Unload(Server, boolean)} with save
     * as <b>true</b>.
     * @param level
     *             The level will unload
     * @return boolean
     *                Returns true if the level was unloaded, otherwise returns false.
     */
    @Override
    public boolean unloadLevel(Level level) {
        return unloadLevel(level, true);
    }
    /**
     * Unload a level
     * @param level
     *             The level to unload
     * @param save
     *            Whether the level should save before unloading
     * 
     * @return boolean
     *                Returns true if the level was unloaded, otherwise returns false.
     */
    public boolean unloadLevel(Level level, boolean save) {
        LevelUnloadEvent event = new LevelUnloadEvent(level);
        server.getEventSystem().callEvent(event);
        if (event.isCancelled()) {
            server.log("The unloading of level " + level + " was canceled by " + event.getCanceler());
            return false;
        }
        if (!levels.contains(level))
            return false;
        try {
            level.unload(server, save);
        } catch (IOException e) {
            server.logError(e);
        }
        levels.remove(level);
        return true;
    }

    private class Saver implements Tick {

        @Override
        public void tick() {
            for (int i = 0; i < levels.size(); i++) {
                if (levels.get(i).isAutoSaveEnabled()) {
                    if (!levels.get(i).hasUpdated())
                        continue;
                    try {
                        levels.get(i).save();
                    } catch (IOException e) {
                        server.logError(e);
                    }
                }
            }
        }

        @Override
        public boolean inSeperateThread() {
            return true;
        }

        @Override
        public int getTimeout() {
            return 6000;
        }

        @Override
        public String tickName() {
            return "LevelManagerService-Saver";
        }
    }

    @Override
    public void generateLevel(String name, Generator<?> gen) {
        ClassicGenerator g;
        if (gen instanceof ClassicGenerator)
            g = (ClassicGenerator)gen;
        else
            throw new InvalidParameterException("You can not create a classic level with a non classic generator!");
        newClassicLevel(name, (short)64, (short)64, (short)64, g);
    }

    @Override
    public void loadLevels() {
        loadClassicLevels();
    }

    @Override
    public Level loadLevel(File file) {
        return loadClassicLevel(file.getAbsolutePath());
    }

    @Override
    public void saveLevels() {
        for (Level l : getLevelList()) {
            saveLevel(l);
        }
    }

    @Override
    public void saveLevel(Level level) {
        if (level instanceof ClassicLevel) {
            try {
                ((ClassicLevel)level).save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void generateLevel(String name, Generator<?> gen, Object... param) {
        ClassicGenerator g;
        if (gen instanceof ClassicGenerator)
            g = (ClassicGenerator)gen;
        else
            throw new InvalidParameterException("You can not create a classic level with a non classic generator!");
        int x = 64;
        int y = 64;
        int z = 64;
        if (param.length >= 1 && param[0] instanceof Integer)
            x = (Integer)param[0];
        if (param.length >= 2 && param[1] instanceof Integer)
            y = (Integer)param[1];
        if (param.length >= 3 && param[2] instanceof Integer)
            z = (Integer)param[2];
        newClassicLevel(name, (short)x, (short)y, (short)z, g);
    }

}
