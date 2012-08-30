/*******************************************************************************
 * Copyright (c) 2012 GamezGalaxy.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.gamezgalaxy.GGS.world;

import java.io.*;
import java.rmi.server.ServerCloneException;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.gamezgalaxy.GGS.iomodel.Player;
import com.gamezgalaxy.GGS.server.Server;
import com.gamezgalaxy.GGS.server.Tick;
import com.gamezgalaxy.GGS.world.convert.DatToGGS;

public class Level implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 6250572320060286713L;
	
	private Thread physics;
	
	private boolean run;
	
	ArrayList<Tick> ticks = new ArrayList<Tick>();
	
	Block[] blocks;
	
	/**
	 * The width of the level (max X)
	 */
	public short width;
	
	/**
	 * The height of the level (max Y)
	 */
	public short height;
	
	/**
	 * The depth of the level (max Z)
	 */
	public short depth;
	
	/**
	 * The X position (in blocks) where the player spawns.
	 */
	public int spawnx;
	
	/**
	 * The Y position (in blocks) where the player spawns.
	 */
	public int spawny;
	
	/**
	 * The Z position (in blocks) where the player spawns.
	 */
	public int spawnz;
	
	/**
	 * The name of the level
	 */
	public String name;
	
	/**
	 * The constructor for Level.
	 * The constructor wont generate a flat world, you need to
	 * call {@link #FlatGrass()}
	 * @param width
	 *             The width (X) of the level
	 * @param height
	 *             The height (Y) of the level
	 * @param depth
	 *             The depth (Z) of the level
	 */
	public Level(short width, short height, short depth) {
		this();
		this.width = width;
		this.height = height;
		this.depth = depth;
		this.spawnx = width / 2;
		this.spawny = 33;
		this.spawnz = depth / 2;
		blocks = new Block[width*height*depth];
	}
	
	/**
	 * The constructor for the level
	 * This constructor starts the physics ticks
	 * This constructor wont generate a level, nor will it set a default
	 * width, height, and depth. To set a width, height, and depth and
	 * initialize the blocks, use must use {@link #Level(short, short, short)}
	 */
	public Level() {
		this.ticks = new ArrayList<Tick>();
		physics = new Ticker();
		run = true;
		physics.start();
	}
	
	/**
	 * This will generate a flat world
	 */
	public void FlatGrass() {
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < depth; z++) {
					if (y < height / 2)
						blocks[PosToInt(x, y, z)] = Block.getBlock("dirt");
					else if (y == height / 2)
						blocks[PosToInt(x, y, z)] = Block.getBlock("grass");
					else
						blocks[PosToInt(x, y, z)] = Block.getBlock("air");
				}
			}
		}
	}
	
	/**
	 * Set a block in this world.
	 * If the block is a physicsblock, it will be added
	 * to the physics tick.
	 * This method wont send out a change to the clients.
	 * To do this, use {@link Player#GlobalBlockChange(short, short, short, Block, Level, Server)} instead
	 * @param b
	 *         The block to add
	 * @param index
	 *             Where to add the block
	 * @param server
	 *              The server this blockchange is happening in
	 */
	public void setTile(Block b, int index, Server server) {
		if (index < 0) index = 0;
		if (index >= blocks.length) index = blocks.length - 1;
		Block wasthere = blocks[index];
		if (b instanceof PhysicsBlock) {
			PhysicsBlock pb = ((PhysicsBlock)b).clone(server);
			pb.setLevel(this);
			int[] pos = IntToPos(index);
			pb.setPos(pos[0], pos[1], pos[2]);
			blocks[index] = pb;
			if (this.ticks == null)
				this.ticks = new ArrayList<Tick>();
			this.ticks.add(pb);
		}
		else
			blocks[index] = b;
		wasthere.onDelete(this, index);
		b.onPlace(this, index);
	}
	
	/**
	 * Get a block in this level
	 * @param index
	 *            Which block to get
	 * @return
	 *        The block at that index
	 */
	public Block getTile(int index) {
		if (index < 0) index = 0;
		if (index >= blocks.length) index = blocks.length - 1;
		if (blocks[index] == null)
			return Block.getBlock((byte)0);
		if (blocks[index].name.equals("NULL"))
			System.out.println("" + blocks[index].getVisableBlock());
		return blocks[index];
	}
	
	/**
	 * Get a block at the X, Y, Z coordinates
	 * @param x
	 *        The X coordinate
	 * @param y
	 *        The Y coordinate
	 * @param z
	 *        The Z coordinate
	 * @return
	 *        The block at those coordinates
	 */
	public Block getTile(int x, int y, int z) {
		return getTile(PosToInt(x, y, z));
	}
	
	/**
	 * Get how big the block array is
	 * @return
	 *       The size of the block array
	 */
	public int getLength() {
		return blocks.length;
	}
	
	/**
	 * Set a block in this world.
	 * If the block is a physicsblock, it will be added
	 * to the physics tick.
	 * This method wont send out a change to the clients.
	 * To do this, use {@link Player#GlobalBlockChange(short, short, short, Block, Level, Server)} instead
	 * @param b
	 *         The block to add
	 * @param x
	 *        The X coordinate
	 * @param y
	 *        The Y coordinate
	 * @param z
	 *        The Z coordinate
	 * @param server
	 *              The server this blockchange is happening in
	 */
	public void setTile(Block b, int x, int y, int z, Server server) {
		setTile(b, PosToInt(x, y, z), server);
	}
	
	/**
	 * Convert coordinates to a number that will
	 * correspond to where the coordinates are in the
	 * block array
     * @param x
	 *        The X coordinate
	 * @param y
	 *        The Y coordinate
	 * @param z
	 *        The Z coordinate
	 * @return
	 *        The number that will correspond to where the coordinates
	 *        are in the block array
	 */
	public int PosToInt(int x, int y, int z) {
        if (x < 0) { return -1; }
        if (x >= width) { return -1; }
        if (y < 0) { return -1; }
        if (y >= height) { return -1; }
        if (z < 0) { return -1; }
        if (z >= depth) { return -1; }
        return x + z * width + y * width * depth;
    }
	
	private int[] IntToPos(int index) {
		int[] toreturn = new int[3];
		toreturn[1] = (index / width / height);
		index -= toreturn[1]*width*height;
		toreturn[2] = (index/width);
		index -= toreturn[2]*width;
		toreturn[0] = index;
		return toreturn;
	}
	
	/**
	 * Save the level
	 * @throws IOException
	 *                   An IOExceptoin is thrown if there is a problem writing to the file
	 */
	public void Save() throws IOException {
		if (!new File("levels").exists())
			new File("levels").mkdir();
		FileOutputStream fos = new FileOutputStream("levels/" + name + ".ggs");
		GZIPOutputStream gos = new GZIPOutputStream(fos);
		ObjectOutputStream out = new ObjectOutputStream(gos);
		out.writeLong(serialVersionUID);
		out.writeObject(this);
		out.close();
		gos.close();
		fos.close();
	}
	
	/**
	 * Unload this level.
	 * All players who are in this level will be sent to the {@link Server#MainLevel}
	 * @param server
	 *             The server thats unloading the level
	 * @throws IOException
	 *                   An IOException will occur if there is a problem saving the level
	 */
	public void Unload(Server server) throws IOException {
		Unload(server, true);
	}
	/**
	 * Unload this level.
	 * All players who are in this level will be sent to the {@link Server#MainLevel}
	 * @param server
	 *             The server thats unloading the level
	 * @param save
	 *           Weather the level should save before unloading
	 * @throws IOException
	 *                   An IOException will occur if there is a problem saving the level
	 */
	public void Unload(Server server, boolean save) throws IOException {
		if (save)
			Save();
		run = false;
		try {
			physics.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		server.Log("Unloading " + name);
		for (Player p : server.players) {
			if (p.getLevel() == this)
				p.changeLevel(server.MainLevel, false);
		}
		blocks = null;
	}
	
	/**
	 * Load a level and return the level
	 * @param filename
	 *               The file to load and read
	 * @return
	 *        The level object
	 * @throws IOException
	 *                   An IOException is thrown if there is a problem reading the level
	 * @throws ClassNotFoundException
	 *                              This exception is thrown if a block that was saved with the level is not loaded or cant be found.
	 */
	public static Level Load(String filename) throws IOException, ClassNotFoundException {
		Level l = null;
		if (filename.endsWith(".dat"))
			l = Convert(filename);
		else {
			FileInputStream fis = new FileInputStream(filename);
			GZIPInputStream gis = new GZIPInputStream(fis);
			ObjectInputStream obj = new ObjectInputStream(gis);
			long version = obj.readLong();
			if (version == serialVersionUID)
				l = (Level)obj.readObject();
			else
				throw new IOException("The level version does not match the current");
			l.ticks = new ArrayList<Tick>();
			l.physics = l.new Ticker();
			l.name = new File(filename).getName().split("\\.")[0];
			l.run = true;
			l.physics.start();
			obj.close();
			gis.close();
			fis.close();
		}
		return l;
	}
	
	/**
	 * Convert a .dat file to a .ggs file
	 * @param file
	 *           The file to load and convert
	 * @return
	 *        The converted level object
	 * @throws IOException
	 *                   An IOException is thrown if there is a problem reading the file
	 */
	public static Level Convert(String file) throws IOException {
		String name = new File(file).getName().split("\\.")[0];
		DatToGGS newlvl = new DatToGGS();
		newlvl.load(file);
		Level lvl = new Level((short)newlvl.level.width, (short)newlvl.level.height, (short)newlvl.level.height);
		int[] cords = new int[3];
		for (int i = 0; i < newlvl.level.blocks.length; i++) {
			cords = newlvl.getCoords(i);
			try {
				lvl.blocks[lvl.PosToInt(cords[0], cords[1], cords[2])] = Block.getBlock(newlvl.level.blocks[i]);
			} catch (Exception e) {
				System.out.println(i + "= " + cords[0] + ":" + cords[1] + ":" + cords[2]);
			}
		}
		lvl.name = name;
		lvl.Save();
		return lvl;
	}
	
	private class Ticker extends Thread implements Serializable {
		
		@Override
		public void run() {
			while (run) {
				if (ticks == null)
					ticks = new ArrayList<Tick>();
				for (int i = 0; i < ticks.size(); i++) {
					ticks.get(i).Tick();
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
