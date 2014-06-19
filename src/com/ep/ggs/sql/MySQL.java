/*******************************************************************************
 * Copyright (c) 2013 MCForge.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.ep.ggs.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.ep.ggs.server.Server;


public class MySQL implements ISQL {

    protected Connection connection;
    protected String IP;
    protected int port;
    protected String DB;
    protected String prefix;
    protected String username;
    protected String pass;
    private Server server;
    @Override
    public void executeQuery(String command) {
        try {
            if (connection.isClosed())
                connect(server);
        } catch (SQLException e) {
            connect(server);
        }
        try {
            PreparedStatement pstm = connection.prepareStatement(command);
            pstm.executeUpdate();
        } catch (SQLException e) {
            server.logError(e);
        }
    }

    @Override
    public void executeQuery(String[] commands) {
        try {
            if (connection.isClosed())
                connect(server);
        } catch (SQLException e) {
            connect(server);
        }
        try {
            Statement statement = connection.createStatement();
            for (String s : commands) {
                try {
                    statement.executeUpdate(s);
                }
                catch (SQLException e) {
                    server.log("ERROR EXECUTING STATEMENT!");
                    server.log("Statement: ");
                    server.log(s);
                    server.logError(e);
                }
            }
            statement.close();
        }
        catch (SQLException e) {
            server.logError(e);
        }
    }

    @Override
    public ResultSet fillData(String command) {
        try {
            if (connection.isClosed())
                connect(server);
        } catch (SQLException e) {
            connect(server);
        }
        try {
            return connection.prepareStatement(command).executeQuery();
        } catch (SQLException e) {
            server.logError(e);
            return null;
        }
    }

    @Override
    public void connect(Server server) {
        this.server = server;
        try {
            DriverManager.registerDriver(new com.mysql.jdbc.Driver());
            connection = DriverManager.getConnection(getURL() + DB + getProperties(), username, pass);
        } catch (Exception e) {
            server.logError(e);
        }
    }
    /**
     * Get the connection
     * @return java.sql.connection
     */
    @Override
    public Connection getConnection() {
        return connection;
    }
    
    /**
     * Get the URL to the mysql server
     * @return The URL
     */
    public String getURL() {
        return "jdbc:mysql://" + IP + ":" + port + "/";
    }
    
    /**
     * Properties to add to the mysql connection
     * @return
     */
    public String getProperties() {
        return "?autoDeserialize=true";
    }
    
    /**
     * Set the IP of the MySQL database
     * @param IP
     */
    public void setIP(String IP) {
        this.IP = IP;
    }
    
    /**
     * Set the port of the MySQL database
     * @param port
     */
    public void setPort(int port) {
        this.port = port;
    }
    
    /**
     * Get the IP of the MySQL database
     * @return the IP
     */
    public String getIP() {
        return IP;
    }
    
    /**
     * Get the port of the MySQL database
     * @return the port
     */
    public int getPort() {
        return port;
    }
    /**
     * Get the URL to the mysql server including the database
     * @return The URL
     */
    public String getFullURL() {
        return getURL() + DB;
    }

    @Override
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    @Override
    public String getPrefix() {
        return prefix;
    }
    
    public void setUsername(String user) {
        this.username = user;
    }
    
    public void setPassword(String pass) {
        this.pass = pass;
    }
    
    public void setDatabase(String DB) {
        this.DB = DB;
    }

}

