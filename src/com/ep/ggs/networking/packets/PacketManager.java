/*******************************************************************************
 * Copyright (c) 2013 MCForge.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.ep.ggs.networking.packets;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

import com.ep.ggs.networking.IOClient;
import com.ep.ggs.networking.packets.browser.*;
import com.ep.ggs.networking.packets.classicminecraft.*;
import com.ep.ggs.networking.packets.classicminecraft.extend.*;
import com.ep.ggs.networking.packets.clients.BrowserClient;
import com.ep.ggs.networking.packets.clients.ClassicClient;
import com.ep.ggs.networking.packets.clients.Client;
import com.ep.ggs.networking.packets.clients.MCServerList;
import com.ep.ggs.networking.packets.clients.SMPClient;
import com.ep.ggs.networking.packets.minecraft.*;
import com.ep.ggs.server.Server;


public class PacketManager {

    protected Packet[] packets;
    
    protected IClient[] clients = new IClient[] {
            new BrowserClient(),
            new ClassicClient(),
            new SMPClient(),
            new MCServerList()
    };

    protected ServerSocket serverSocket;

    protected Thread reader;
    
    protected boolean running;
    
    protected ArrayList<IOClient> connectedclients = new ArrayList<IOClient>();

    /**
     * The server this PacketManager belongs to
     */
    public Server server;

    /**
     * The constructor for the PacketManager
     * @param instance
     *                The server that this PacketManager will belong to
     */
    public PacketManager(Server instance) {
        this.server = instance;
        initPackets();
    }

    protected void initPackets() {
        packets = new Packet[] {
                new Connect(this),
                new DespawnPlayer(this),
                new FinishLevelSend(this),
                new GlobalPosUpdate(this),
                new Kick(this),
                new LevelSend(this),
                new LevelStartSend(this),
                new Message(this),
                new MOTD(this),
                new Ping(this),
                new PosUpdate(this),
                new SetBlock(this),
                new SpawnPlayer(this),
                new TP(this),
                new UpdateUser(this),
                new Welcome(this),
                new GET(this),
                new ClickDistancePacket(this),
                new ExtEntryPacket(this),
                new ExtInfoPacket(this),
                new HoldThisPacket(this),
                new ExtPlayerPacket(this),
                new ExtAddPlayerNamePacket(this),
                new ExtRemovePlayerNamePacket(this),
                
                
                new Animation(this),
                new AttachEntity(this),
                new BlockAction(this),
                new BlockBreakAnimation(this),
                new BlockChange(this),
                new ChangeGameState(this),
                new ClientSettings(this),
                new ClientStatuses(this),
                new CloseWindow(this),
                new CollectItem(this),
                new ConfirmTransaction(this),
                new DestroyEntity(this),
                new EnchantItem(this),
                new EncryptionKeyRequest(this),
                new EncryptionResponse(this),
                new Entity(this),
                new EntityAction(this),
                new EntityEffect(this),
                new EntityHeadLook(this),
                new EntityLook(this),
                new EntityLookAndRelativeMove(this),
                new EntityRelativeMove(this),
                new EntityStatus(this),
                new EntityTeleport(this),
                new EntityVelocity(this),
                new Handshake(this),
                new HeldItemChange(this),
                new IncrementStatistic(this),
                new ItemData(this),
                new KeepAlive(this),
                new LoginRequest(this),
                new NamedSoundEffect(this),
                new OpenWindow(this),
                new Player(this),
                new PlayerDigging(this),
                new PlayerAbilities(this),
                new PlayerDigging(this),
                new PlayerListItem(this),
                new PlayerLook(this),
                new PlayerPosition(this),
                new PlayerPositionAndLook(this),
                new RemoveEntityEffect(this),
                new Respawn(this),
                new SetExperience(this),
                new SMPKick(this),
                new SoundOrParticleEffect(this),
                new SpawnExperienceOrb(this),
                new SpawnGlobalEntity(this),
                new SpawnPainting(this),
                new SpawnPosition(this),
                new TabComplete(this),
                new TimeUpdate(this),
                new UpdateHealth(this),
                new UpdateSign(this),
                new UpdateWindowProperty(this),
                new UseBed(this),
                new UseEntity(this)
        };
    }

    /**
     * Get a packet this PacketManager handles.
     * @param opCode
     *              The OpCode for the packet.
     * @return
     *        The packet found, if no packet is found, then it will
     *        return null.
     * @deprecated
     *            Search by name to avoid getting the wrong packet or include the client type to filter.
     */
    @Deprecated
    public Packet getPacket(byte opCode) {
        for (Packet p : packets) {
            if (p.ID == opCode)
                return p;
        }
        return null;
    }
    
    /**
     * Get a packet this PacketManager handles and only include packets the client <b>"client"</b> supports.
     * @param opCode
     *              The OpCode for the packet.
     * @param client
     *              The client type. This will exclude all other client types.
     * @return
     *        The packet found, if no packet is found, then it will
     *        return null.
     */
    public Packet getPacket(byte opCode, Client client) {
        for (Packet p : packets) {
            if (p.ID == opCode && p.getSupportedClients().contains(client))
                return p;
        }
        return null;
    }

    /**
     * Get a packet this PacketManager handles.
     * @param name
     *            The name of the packet
     * @return
     *        The packet found, if no packet is found, then it will
     *        return null.
     */
    public Packet getPacket(String name) {
        for (Packet p : packets) {
            if (p.name.equalsIgnoreCase(name))
                return p;
        }
        return null;
    }

    /**
     * Have the PacketManager start listening for clients
     * on the port provided by the {@link PacketManager#server}
     * @throws IOException 
     */
    public void startReading() throws IOException {
        if (running)
            return;
        if (serverSocket == null)
            serverSocket = new ServerSocket(this.server.Port);
        running = true;
        reader = new Read();
        reader.start();
        server.log("Listening on port " + server.Port);
    }

    /**
     * Stop listening for clients.
     */
    public void stopReading() {
        if (!running)
            return;
        running = false;
        reader.interrupt();
        try {
            serverSocket.close();
            serverSocket = null;
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        try {
            reader.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public void restartReader() throws IOException {
        if (!running)
            return;
        stopReading();
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) { }
        startReading();
    }
    
    /**
     * Check if an IP is within the localhost range
     * @param addr
     *            The IP
     * @return
     *        True if the connection is local.
     *        False if its not.
     */
    public static boolean isLocalConnection(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress())
            return true;
        try {
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (SocketException e) {
            return false;
        }
    }
    
    /**
     * Remove an {@link IOClient} from the {@link PacketManager#getConnectedClients()} list.
     * @param client
     *              The client to remove
     * @return
     *        Whether the client was removed or not.
     */
    public boolean disconnect(IOClient client) {
       if (connectedclients.contains(client)) {
           connectedclients.remove(client);
           server.log("Removing SimpleIOClient connection", true);
           server.rebuildClassicPlayerCache();
           return true;
       }
       return false;
    }
    
    /**
     * Get all the connected clients
     * @return
     *        An {@link ArrayList} of IOClients
     */
    public ArrayList<IOClient> getConnectedClients() {
        return connectedclients;
    }
    
    private IClient findClient(byte OPCode) {
        for (IClient c : clients) {
            if (c.getOPCode() == OPCode)
                return c;
        }
        return null;
    }

    private void accept(Socket connection) throws IOException {
        DataInputStream reader = new DataInputStream(connection.getInputStream());
        byte firstsend = (byte)reader.read();
        IClient client = findClient(firstsend);
        if (client == null)
            return;
        IOClient clientconnection = client.create(connection, this);
        if (clientconnection == null)
            return;
        connectedclients.add(clientconnection);
        clientconnection.listen();
    }

    private class Read extends Thread {

        @Override
        public void run() {
            Socket connection = null;
            while (running) {
                if (serverSocket.isClosed())
                    break;
                try {
                    connection = serverSocket.accept();
                    connection.setSoTimeout(300000);
                    server.log("Connection made from " + connection.getInetAddress().toString());
                    new AcceptThread(connection).start();
                } catch (IOException e) {
                    if (e.getMessage().indexOf("socket closed") == -1) //Happens when the socket is shutdown
                        e.printStackTrace();
                }
            }
        }
    }

    private class AcceptThread extends Thread {
        private Socket connection;
        public AcceptThread(Socket connection) { this.connection = connection; }

        @Override
        public void run() {
            try {
                accept(connection);
            } catch (IOException e) {
                if (!e.getMessage().contains("Connection reset")) //Mostly happens when xwom connects
                    e.printStackTrace();
            }
        }
    }

}

