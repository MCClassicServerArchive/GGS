/*******************************************************************************
 * Copyright (c) 2013 MCForge.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.ep.ggs.iomodel;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.ep.ggs.API.io.PacketReceivedEvent;
import com.ep.ggs.API.io.PacketSentEvent;
import com.ep.ggs.networking.IOClient;
import com.ep.ggs.networking.packets.DynamicPacket;
import com.ep.ggs.networking.packets.Packet;
import com.ep.ggs.networking.packets.PacketManager;
import com.ep.ggs.networking.packets.clients.Client;


public class SimpleIOClient implements IOClient {
    protected Socket client;

    protected OutputStream writer;

    protected InputStream reader;

    protected Thread readerthread;
    
    protected Thread writerthread;

    protected PacketManager pm;
    
    protected boolean connected;
    
    private Client clienttype;
    
    protected long readID;

    protected List<byte[]> packet_queue = Collections.synchronizedList(new LinkedList<byte[]>());

    protected InetAddress address;

    /**
     * Returns the IP address string in textual presentation.
     * @return the raw IP address in a string format.
     */
    public String getIP() {
        return getInetAddress().getHostAddress();
    }
    
    /**
     * Get the type of client this SimpleIOClient is.
     * @return
     *        The {@link Client} type.
     */
    public Client getClientType() {
        return clienttype;
    }

    /**
     * Gets the host name for this IP address. 
     * If this InetAddress was created with a host name, this host name will be remembered and returned; otherwise, a reverse name lookup will be performed and the result will be returned based on the system configured name lookup service. If a lookup of the name service is required, call getCanonicalHostName. 
     * If there is a security manager, its checkConnect method is first called with the hostname and -1 as its arguments to see if the operation is allowed. If the operation is not allowed, it will return the textual representation of the IP address.
     * @return the host name for this IP address, or if the operation is not allowed by the security check, the textual representation of the IP address.
     */
    public String getHostName() {
        return getInetAddress().getHostName();
    }

    public InetAddress getInetAddress() {
        return address;
    }
    
    /**
     * Returns the connection state of the SimpleIOClient 
     * @return
     *        return trues if this SimpleIOClient is connected to a client.
     */
    public boolean isConnected() {
        if (client == null)
            return false;
        return client.isConnected() && !client.isClosed() && connected;
    }


    /**
     * Get the thread ID for the thread thats currently
     * reading packets.
     * @return
     *        The Thread ID as a long
     */
    public long getReaderThreadID() {
        return readID;
    }
    
    /**
     * The constructor for SimpleIOClient
     * @param client
     *              The socket that connected to the server
     * @param pm
     *          The PacketManager that recieved the connection
     */
    public SimpleIOClient(Socket client, PacketManager pm) {
        if (client == null)
            return;
        this.client = client;
        this.pm = pm;
        try {
            writer = new PrintStream(client.getOutputStream());
            reader = new DataInputStream(client.getInputStream());
        } catch (IOException e) {
            pm.server.log("Error");
            e.printStackTrace();
        }
        this.address = client.getInetAddress();
    }

    /**
     * Start listening and receiving packet from this
     * client.
     */
    public void listen() {
        if (reader == null)
            return;
        connected = true;
        readerthread = new Reader(this);
        readerthread.start();
        writerthread = new Writer();
        writerthread.start();
    }

    /**
     * Disconnect this client from the server
     */
    public void closeConnection() {
        if (!connected)
            return;
        try {
            pm.server.log("Closing connection");
            connected = false;
            try {
                writerthread.join(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            writerthread = null;
            readerthread = null;
            writer.close();
            reader.close();
            client.close();
            writer = null;
            reader = null;
            client = null;
            connected = false;
            packet_queue.clear();
            pm.disconnect(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Send this client some data
     * (MMmmm...yummy data)
     * @param data
     *            The data in a byte array
     * @throws IOException
     *                    If there's an error sending the data to
     *                    the client.
     */
    public void writeData(byte[] data) throws IOException {
        Packet p = pm.getPacket(data[0], clienttype);
        if (p != null) {
            PacketSentEvent event = new PacketSentEvent(this, pm.server, p);
            pm.server.getEventSystem().callEvent(event);
        }
        packet_queue.add(data);
    }
    
    public OutputStream getOutputStream() {
        return writer;
    }
    
    public void setOutputStream(OutputStream out) {
        this.writer = out;
    }
    
    public InputStream getInputStream() {
        return reader;
    }
    
    public void setInputStream(InputStream in) {
        this.reader = in;
    }

    protected boolean sendNextPacket() throws IOException {
        if (packet_queue.isEmpty())
            return false;
        byte[] data = packet_queue.remove(0);
        if (data == null) //Safeguard
            return false;
        writer.write(data);
        return true;
    }

    private class Writer extends Thread {

        @Override
        public void run() {
            Thread.currentThread().setName("SimpleIOClient-Writer");
            while (pm.server.Running && connected) {
                try {
                    while (sendNextPacket());
                    writer.flush();
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class Reader extends Thread {
        SimpleIOClient client;

        public Reader(SimpleIOClient client) { this.client = client; }
        @Override
        public void run() {
            readID = Thread.currentThread().getId();
            Thread.currentThread().setName("SimpleIOClient-Reader");
            while (pm.server.Running && connected) {
                try {
                    int readvalue = (byte)reader.read();
                    if (readvalue == -1) {
                        closeConnection();
                        break;
                    }
                    byte opCode = (byte)readvalue;
                    PacketReceivedEvent event = new PacketReceivedEvent(client, pm.server, reader, opCode);
                    pm.server.getEventSystem().callEvent(event);
                    if (event.isCancelled())
                        continue;
                    Packet packet = pm.getPacket(opCode, clienttype);
                    if (packet == null) {
                        pm.server.log("Client sent " + opCode);
                        pm.server.log("How do..?");
                        continue;
                    }
                    if (!packet.dynamicSize() && !(packet instanceof DynamicPacket)) {
                        byte[] message = new byte[packet.length];
                        reader.read(message);
                        if (message.length < packet.length && !packet.dynamicSize()) {
                            pm.server.log("Bad packet..");
                            continue;
                        }
                        packet.Handle(message, pm.server, client);
                    }
                    else if (packet instanceof DynamicPacket)
                        ((DynamicPacket) packet).handle(pm.server, client, reader);
                    else
                        throw new RuntimeException("Packet " + packet.ID + " (" + packet.name + ") has a dynamicSize, but is not using a DynamicPacket!");
                } catch (IOException e) {
                    if (isConnected())
                        closeConnection();
                    break;
                }
                catch (Exception e) {
                    e.printStackTrace();
                    if (client instanceof Player)
                        ((Player)client).kick("ERROR!");
                    if (isConnected())
                        closeConnection();
                    break;
                }
            }
            if (isConnected())
                closeConnection();
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleIOClient) {
            SimpleIOClient client = (SimpleIOClient)obj;
            if (client.client != null && client != null)
                return client.address.equals(address) && client.getReaderThreadID() == getReaderThreadID() && client.client.equals(client);
            else
                return client.address.equals(address) && client.getReaderThreadID() == getReaderThreadID();
        }
        return false;
    }

    public void setClienttype(Client clienttype) {
        this.clienttype = clienttype;
    }
}

