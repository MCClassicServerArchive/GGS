/*******************************************************************************
 * Copyright (c) 2013 MCForge.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.ep.ggs.groups;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ep.ggs.API.EventHandler;
import com.ep.ggs.API.Listener;
import com.ep.ggs.API.player.PlayerLoginEvent;
import com.ep.ggs.API.plugin.Command;
import com.ep.ggs.chat.ChatColor;
import com.ep.ggs.groups.exceptions.GroupAttributeNotFoundException;
import com.ep.ggs.iomodel.Player;
import com.ep.ggs.server.Server;
import com.ep.ggs.util.FileUtils;

public class Group implements Listener {
	static ArrayList<Group> groups = new ArrayList<Group>();
	static HashMap<Group, String> temp = new HashMap<Group, String>();
	private ArrayList<String> members = new ArrayList<String>();
	private HashMap<String, String> extradata = new HashMap<String, String>();
	private Element data;
	private static Group defaultgroup;
	/**
	 * The permission level of this group
	 */
	public int permissionlevel;
	/**
	 * If this group is an OP group
	 */
	public boolean isOP = false;
	/**
	 * The name of this group
	 */
	public String name;
	/**
	 * The parent group
	 */
	public Group parent;
	/**
	 * Gets the group's color
	 */
	public ChatColor color;

	/**
	 * Commands this group can use despite permission level
	 */
	private ArrayList<String> exceptions = new ArrayList<String>(); 

	public Group(String name, int permission, boolean isOP, ChatColor color, Group parent) {
		this.name = name;
		this.permissionlevel = permission;
		this.isOP = isOP;
		this.parent = parent;
		this.color = color;
	}

	public Group(String name, int permission, boolean isOP, ChatColor color) {
		this(name, permission, isOP, color, null);
	}

	public Group(String name, Group parent) {
		this(name, parent.permissionlevel, parent.isOP, parent.color, parent);
	}
	
	/**
	 * Set a value to be saved for this group in the XML settings (groups.xml)
	 * @param key
	 *           The node name
	 * @param value
	 *             The value of the node
	 */
	public void setAttribute(String key, String value) {
	    if (extradata.containsKey(key))
	        extradata.remove(key);
	    extradata.put(key, value);
	}
	
	/**
	 * Check to see if the setting <b>"key"</b> exists in the XML settings or in the cache.
	 * @param key
	 *           The key to lookup
	 * @return
	 *        Returns whether or not the setting exists
	 */
	public boolean hasAttribute(String key) {
	    return extradata.containsKey(key) || getTextValue(data, key) != null;
	}
	
	/**
	 * Get a group setting that is stored in the cache or in the XML settings (groups.xml)
	 * @param key
	 *           The key to lookup
	 * @return
	 *        The value of the setting
	 * @throws GroupAttributeNotFoundException
	 *                                        This is thrown if the setting can't be found.
	 */
	public String getAttribute(String key) throws GroupAttributeNotFoundException {
	    if (extradata.containsKey(key))
	        return extradata.get(key);
	    String d = getTextValue(data, key);
	    if (d != null) {
	        extradata.put(key, d);
	        return d;
	    }
	    throw new GroupAttributeNotFoundException("The group attribute \"" + key + "\" does not exist!");
	}

	/**
	 * Can this group execute the command <b>c</b> This method will check the
	 * {@link #parent} group as well if this group can't execute the command
	 * 
	 * @param c
	 *            The command to check
	 * @return Returns true if the group can execute the command
	 */
	public boolean canExecute(Command c) {
		if (c.isOPCommand() && isOP && c.getPermissionLevel() <= permissionlevel)
			return true;
		else if (c.getPermissionLevel() <= permissionlevel && !c.isOPCommand() && !isOP)
			return true;
		else if (isOP && !c.isOPCommand())
			return true;
		else if (exceptions.contains(c.getName())) return true;
		for (String shorts : c.getShortcuts()) {
			if (exceptions.contains(shorts)) { return true; }
		}
		return (parent != null) ? parent.canExecute(c) : false;
	}

	/**
	 * Remove a member from this group If you want to remove a player to this
	 * group, use {@link #removePlayer(Player)}
	 * 
	 * @param name
	 *            The member's name name
	 */
	public void removeMember(String name) {
		if (!members.contains(name)) return;
		members.remove(name);
		saveMembers();
	}

	/**
	 * Add a member to this group If you want to add a player to this group, use
	 * {@link #addPlayer(Player)}
	 * 
	 * @param name
	 *            The member's name to add
	 */
	public void addMember(String name) {
		if (members.contains(name)) return;
		members.add(name);
		saveMembers();
	}

	/**
	 * Add a player to this group
	 * 
	 * @param p
	 *            The player to add
	 */
	public void addPlayer(Player p) {
		addMember(p.getUsername());
	}

	/**
	 * Remove a player from this group
	 * 
	 * @param p
	 *            The player to remove
	 */
	public void removePlayer(Player p) {
		removeMember(p.getUsername());
	}

	/**
	 * Load the members in this group
	 * 
	 * @throws IOException
	 *             An IOException will be thrown if there is a problem loading
	 *             the member list file
	 */
	public void loadMembers() throws IOException {
		if (!new File("ranks").exists()) new File("ranks").mkdir();
		if (!new File("ranks/" + name).exists()) return;
		FileInputStream fstream = new FileInputStream("ranks/" + name);
		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String strLine;
		while ((strLine = br.readLine()) != null) {
			if (strLine.startsWith("#")) continue;
			members.add(strLine);
		}
		in.close();

	}

	/**
	 * Save the members in this group
	 */
	public void saveMembers() {
		if (!new File("ranks").exists()) new File("ranks").mkdir();
		if (new File("ranks/" + name).exists()) new File("ranks/" + name).delete();
		PrintWriter out = null;
		try {
			new File("ranks/" + name).createNewFile();
			out = new PrintWriter("ranks/" + name);
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		catch (IOException e) {
			e.printStackTrace();
			return;
		}
		for (String s : members) {
			out.println(s);
		}
		out.flush();
		out.close();
	}

	/**
	 * Retrieves the list of existing groups
	 * @return An ArrayList containing the list of groups
	 */
	public static final ArrayList<Group> getGroupList() {
		return groups;
	}
	
	/**
	 * Gets an ArrayList containing the online members of this group
	 */
	public List<Player> getOnlinePlayers(Server server) {
		ArrayList<Player> online = new ArrayList<Player>();
	    List<Player> players = server.getClassicPlayers();
	    for (Player p : players) {
	        if (p.getGroup() == this)
	            online.add(p);
	    }
	    return Collections.unmodifiableList(online);
	}

	/**
	 * Find a group
	 * 
	 * @param name
	 *            The name of the group
	 * @return The group object
	 */
	public static Group find(String name) {
		for (Group g : groups) {
			if (g.name.equalsIgnoreCase(name)) return g;
		}
		return null;
	}

	/**
	 * Get the group the player <b>p</b> is assigned to
	 * 
	 * @param p
	 *            The player to search for
	 * @return The group player <b>p</b> is in
	 */
	public static Group getGroup(Player p) {
		for (Group g : groups) {
			for (String pp : g.members) {
				if (pp.equals(p.getUsername())) return g;
			}
		}
		return null;
	}
	/**
	 * Get the group the <b>playerName</b> is assigned to
	 * 
	 * @param playerName
	 *            The player to search for
	 * @return The group player <b>playerName</b> is in
	 */
	public static Group getGroup(String playerName) {
		for (Group g : groups) {
			for (String pp : g.members) {
				if (pp.equals(playerName)) return g;
			}
		}
		return null;
	}
	/**
	 * Get the default group the player is assigned to if the player is not
	 * assigned to any group
	 * 
	 * @return The default group
	 */
	public static Group getDefault() {
		return defaultgroup;
	}

	/**
	 * Adds a group to the group list
	 * 
	 * @param group
	 *            The group to be added
	 */
	public static boolean add(Group group) {
		for (Group g : groups) {
			if (g.name.equals(group.name)) { return false; }
		}
		groups.add(group);
		return saveGroups();
	}

	/**
	 * Deletes this group.
	 */
	public boolean delete() {
		groups.remove(this);
		members.clear();
		if (new File("ranks/" + name).exists()) new File("ranks/" + name).delete();
		return saveGroups();
	}

	/**
	 * change a group's name
	 * 
	 * @param name
	 *            new name to set it to
	 * @return success true/false
	 */
	public boolean setName(String name) {
		this.name = name;
		return saveGroups();
	}

	/**
	 * Change if this is an operator group or not
	 * 
	 * @param isop
	 *            if operator or not (true/false)
	 * @return returns if action was successful
	 */
	public boolean setIsOp(boolean isop) {
		this.isOP = isop;
		return saveGroups();
	}
	
	/**
	 * Change if this is an operator group or not
	 * 
	 * @param isop
	 *            if operator or not (true/false)
	 * @return returns if action was successful
	 */
	public boolean setColor(ChatColor color, Server server) {
	    List<Player> online = getOnlinePlayers(server);
		for (int i = 0; i < online.size(); i++) {
			Player who = online.get(i);
			if (who.getDisplayColor().getName().equals(who.getGroup().color.getName()))
				who.setDisplayColor(color);
		}
		this.color = color;
		
		return saveGroups();
	}
	
	/**
	 * Change permissions level
	 * 
	 * @param permissionlevel
	 *            the new level to set it to
	 * @return returns if successful
	 */
	public boolean setPermission(int permissionlevel) {
		this.permissionlevel = permissionlevel;
		return saveGroups();
	}
	
	/**
	 * Save all the group data into groups.xml
	 * @return
	 *        Returns true if the data was saved,
	 *        returns false if the data could not be saved.
	 */
	public static boolean saveGroups() {
	    ArrayList<String> lines = new ArrayList<String>();
	    lines.add("<Groups>");
	    for (Group g : groups) {
	        lines.add("<Group" + (g.parent == null ? ">" : " parent=\"" + g.parent.name + "\">"));
	        lines.add("<name>" + g.name + "</name>");
	        lines.add("<isop>" + g.isOP + "</isop>");
	        lines.add("<permission>" + g.permissionlevel + "</permission>");
	        lines.add("<color>" + g.color.getColor() + "</color>");
	        if (Group.getDefault() == g)
	            lines.add("<default>true</default>");
	        for (String key : g.extradata.keySet()) {
	            lines.add("<" + key + ">" + g.extradata.get(key) + "</" + key + ">");
	        }
	        lines.add("</Group>");
	    }
	    lines.add("</Groups>");
	    
	    FileUtils.deleteIfExist(FileUtils.PROPS_DIR + "/groups.xml");
	    PrintWriter out = null;
        try {
            new File(FileUtils.PROPS_DIR + "/groups.xml").createNewFile();
            out = new PrintWriter(FileUtils.PROPS_DIR + "/groups.xml");
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        for (String s : lines) {
            out.println(s);
        }
        out.flush();
        out.close();
        lines.clear();
        return true;
	    
	}

	/**
	 * Load the groups for the server
	 * 
	 * @param server
	 *            The server the groups will be loaded into
	 */
	public static void load(Server server) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			FileUtils.createIfNotExist(FileUtils.PROPS_DIR, "groups.xml", DEFAULT_XML);

			DocumentBuilder db = dbf.newDocumentBuilder();
			Document dom = db.parse(FileUtils.PROPS_DIR + "groups.xml");
			Element elm = dom.getDocumentElement();
			NodeList nl = elm.getElementsByTagName("Group");
			if (nl != null && nl.getLength() > 0) {
				for (int i = 0; i < nl.getLength(); i++) {
					Element e = (Element) nl.item(i);
					try {
					    Group g = read(e, server);
					    server.getEventSystem().registerEvents(g);
						groups.add(g);
					}
					catch (Exception ee) {
					    server.logError(ee);
					}
				}
			}
		}
		catch (ParserConfigurationException pce) {
		    server.logError(pce);
		}
		catch (SAXException se) {
		    server.logError(se);
		}
		catch (IOException ioe) {
		    server.logError(ioe);
		}
		Set<Entry<Group, String>> set = temp.entrySet();
		// Get an iterator
		Iterator<Entry<Group, String>> i = set.iterator();
		// Display elements
		while (i.hasNext()) {
			Entry<Group, String> me = i.next();
			Group g = (Group) me.getKey();
			Group parent = find((String) me.getValue());
			if (parent != null) g.parent = parent;
		}
		temp.clear();
		if (defaultgroup == null)
			defaultgroup = groups.get(0);
	}
	
    @EventHandler
    public void connect(PlayerLoginEvent event) {
        Group g = getGroup(event.getPlayer());
        if (g == null)
            Group.getDefault().addPlayer(event.getPlayer());
    }

	private static Group read(Element e, Server server) {
		String name = getTextValue(e, "name");
		boolean isOp = getTextValue(e, "isop").equalsIgnoreCase("true");
		int permission = getIntValue(e, "permission");
		ChatColor color = ChatColor.White;
		String[] exceptions = new String[0];
		String parent = "null";
		boolean defaultg = false;
		try {
			parent = e.getAttribute("parent");
		}
		catch (Exception ee) {
		}
		
		exceptions = tryGetTextValue(e, "exceptions", "").split("\\:");
		defaultg = tryGetTextValue(e, "default", "false").equalsIgnoreCase("true");
		color = ChatColor.parse(tryGetTextValue(e, "color", ChatColor.White.toString()));

		Group g = new Group(name, permission, isOp, color);
		if (!parent.equals("null")) temp.put(g, parent);
		for (String s : exceptions) {
			g.exceptions.add(s);
		}
		if (defaultg) defaultgroup = g;
		try {
			g.loadMembers();
		}
		catch (IOException e1) {
		    server.logError(e1);
		}
		g.data = e;
		return g;
	}
	
	private static String tryGetTextValue(Element e, String key, String defaultvalue) {
	    try {
	        String toreturn = getTextValue(e, key);
	        if (toreturn == null)
	            toreturn = defaultvalue;
	        return toreturn;
	    } catch (Exception ee) {
	        return defaultvalue;
	    }
	}

	/**
	 * I take a xml element and the tag name, look for the tag and get the text
	 * content i.e for <employee><name>John</name></employee> xml snippet if the
	 * Element points to employee node and tagName is 'name' I will return John
	 */
	private static String getTextValue(Element ele, String tagName) {
		String textVal = null;
		NodeList nl = ele.getElementsByTagName(tagName);
		if (nl != null && nl.getLength() > 0) {
			Element el = (Element) nl.item(0);
			textVal = el.getFirstChild().getNodeValue();
		}

		return textVal;
	}

	/**
	 * Calls getTextValue and returns a int value
	 */
	private static int getIntValue(Element ele, String tagName) {
		// in production application you would catch the exception
		return Integer.parseInt(getTextValue(ele, tagName));
	}

	private static final String DEFAULT_XML = "<!-- \n"
			+ "Copyright (c) 2012 GamezGalaxy.\n"
			+ "All rights reserved. This program and the accompanying materials\n"
			+ "are made available under the terms of the GNU Public License v3.0\n"
			+ "which accompanies this distribution, and is available at\n"
			+ "http://www.gnu.org/licenses/gpl.html\n" 
			+ "-->\n" 
			+ "<Groups>\n"
			+ " <Group>\n" 
			+ "<name>Guest</name>\n" 
			+ "<isop>False</isop>\n"
			+ "<permission>0</permission>\n" 
			+ "<default>true</default>\n" 
			+ "<color>a</color>"
			+ "</Group>\n"
			+ "<Group parent=\"Guest\">\n" 
			+ "<name>Guest1</name>\n"
			+ "<isop>False</isop>\n" 
			+ "<permission>0</permission>\n"
			+ "<color>a</color>"
			+ "</Group>\n"
			+ "<Group>\n" 
			+ "<name>Builder</name>\n" 
			+ "<isop>False</isop>\n"
			+ "<permission>50</permission>\n"
			+ "<color>b</color>"
			+ "</Group>\n" 
			+ "<Group>\n"
			+ "<name>Op</name>\n" 
			+ "<isop>True</isop>\n"
			+ "<permission>100</permission>\n"
			+ "<color>2</color>"
			+ " </Group>\n" 
			+ "</Groups>";
}
