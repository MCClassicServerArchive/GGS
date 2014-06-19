/*******************************************************************************
 * Copyright (c) 2013 MCForge.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.ep.ggs.networking.packets.clients;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;

import com.ep.ggs.iomodel.Browser;
import com.ep.ggs.networking.IOClient;
import com.ep.ggs.networking.packets.IClient;
import com.ep.ggs.networking.packets.Packet;
import com.ep.ggs.networking.packets.PacketManager;


public class BrowserClient implements IClient {

    @Override
    public byte getOPCode() {
        return (byte)'G';
    }

    @Override
    public IOClient create(Socket client, PacketManager pm) {
        Browser b = new Browser(client, pm);
        b.setClienttype(Client.BROWSER);
        b.setOutputStream(null);
        try {
            PrintStream writer = new PrintStream(client.getOutputStream(), false, "US-ASCII");
            b.setOutputStream(writer);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Packet packet = pm.getPacket("GET");
        if (packet == null)
            return null;
       else {
            byte[] message = new byte[0xFF];
            try {
                b.getInputStream().read(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (message.length < packet.length) {
                pm.server.log("Bad packet..");
                return null;
            }
            else
                packet.Handle(message, pm.server, b);
            return null;
        }

    }
}
