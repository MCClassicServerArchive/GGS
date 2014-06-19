/*******************************************************************************
 * Copyright (c) 2013 MCForge.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.ep.ggs.networking.packets.classicminecraft;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import com.ep.ggs.API.io.PacketPrepareEvent;
import com.ep.ggs.iomodel.Player;
import com.ep.ggs.networking.IOClient;
import com.ep.ggs.networking.packets.PacketManager;
import com.ep.ggs.server.Server;


public class SpawnPlayer extends ClassicPacket {

    public SpawnPlayer(String name, byte ID, PacketManager parent) {
        super(name, ID, parent);
    }

    public SpawnPlayer(PacketManager pm) {
        super("Spawn Player", (byte)0x07, pm);
    }

    @Override
    public void Handle(byte[] message, Server server, IOClient player) {

    }

    @Override
    public void Write(IOClient p, Server server, Object...parma) {
        PacketPrepareEvent event = new PacketPrepareEvent(p, this, server);
        server.getEventSystem().callEvent(event);
        if (event.isCancelled())
            return;
        Player player;
        if (p instanceof Player) {
            player = (Player)p;
        }
        else
            return;
        try {
            Player spawn = (Player)parma[0];
            byte[] send = new byte[74];
            send[0] = ID;
            send[1] = (spawn == player) ? (byte)0xFF : spawn.getID();
            String name = (spawn.isShowingPrefix() ? spawn.getDisplayName() : (spawn.isUsingCustomNick() && spawn.getCustomName().startsWith("&") ? "" : spawn.getDisplayColor().toString()) + (spawn.isUsingCustomNick() ? spawn.getCustomName() : spawn.getUsername()));
            while (name.length() < 64)
                name += " ";
            byte[] nameb = name.getBytes("US-ASCII");
            System.arraycopy(nameb, 0, send, 2, 64);
            System.arraycopy(HTNO(spawn.getX()), 0, send, 66, 2);
            System.arraycopy(HTNO(spawn.getY()), 0, send, 68, 2);
            System.arraycopy(HTNO(spawn.getZ()), 0, send, 70, 2);
            send[72] = spawn.yaw;
            send[73] = spawn.pitch;
            player.writeData(send);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public byte[] HTNO(short x) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeShort(x);
        dos.flush();
        return baos.toByteArray();
    }

    @Override
    public void Write(IOClient player, Server servers) {
    }

}

