package service.impl;

import app.CreateWhiteBoard;
import common.Consts;
import common.Consts.Service;

import java.util.Map;
import java.util.logging.Logger;
import service.iUser;
import service.iWhiteboard;
import view.Shape;

import javax.swing.*;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;

public class WhiteboardImpl extends UnicastRemoteObject implements iWhiteboard {

    private static final Logger logger = Logger.getLogger(WhiteboardImpl.class.getName());
    private final HashMap<String, iUser> users;
    private final DefaultListModel<String> usernameList = new DefaultListModel<>();

    private iUser manager;

    public WhiteboardImpl() throws RemoteException {
        super();
        users = new HashMap<>();
    }

    /**
     * Synchronize the shape to other clients
     * @param shape the target shape
     * @param userInfo user information of user who draws the shape
     */
    @Override
    public void update(Shape shape, HashMap<String, String> userInfo) throws RemoteException {

        logger.info(shape + "");

        if (!userInfo.get(Service.USERNAME).equals(this.manager.getUsername())) {
            this.manager.updateShape(shape);
        }

        for (iUser user : users.values()) {
            if (!userInfo.get(Service.USERNAME).equals(user.getUsername())) {
                try {
                    user.updateShape(shape);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * Join JoinWhiteBoard user to the whiteboard room
     * @param userInfo user information of JoinWhiteBoard user
     * @return True if can join the room
     */
    @Override
    public Map<String, Object> join(HashMap<String, String> userInfo) throws RemoteException {
        try {
            iUser client = (iUser) Naming.lookup("rmi://" +
                    userInfo.get(Consts.Service.RMI_HOST) + "/" + userInfo.get(Consts.Service.USERNAME));

            boolean isApproved = manager.approve(userInfo.get(Consts.Service.USERNAME));

            // The JoinWhiteBoard is approved to join
            if (isApproved) {
                Map<String, Object> res = new HashMap<>();
                users.put(userInfo.get(Consts.Service.USERNAME), client);
                usernameList.addElement(Service.CLIENT_NAME + userInfo.get(Consts.Service.USERNAME));

                res.put("ShapeList", manager.getShapeList());
                res.put("UsernameList", this.usernameList);
                return res;
            } else {
                // been rejected
                client.leave(Consts.Message.REJECT_REQUEST);
                return null;
            }
        } catch (MalformedURLException | NotBoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean judge(String str) throws RemoteException {
        return false;
    }

    @Override
    public void removeUser(String username) throws RemoteException {
        iUser client = users.get(username);
        client.leave(Consts.Message.BEEN_KICKED);
        usernameList.removeElement(username);
        manager.popupMessage(Consts.Message.SUCC_KICKED, Consts.Message.BACK, username);
        users.remove(username);
        logger.info("User " + username + " has been kicked out the room.");
    }

    @Override
    public void broadcast(String from, String message) throws RemoteException {
        String type = "";
        System.out.println(from);
        System.out.println(message);
        if (from.equals(manager.getUsername())) {
            type = Service.MGR_NAME ;
        } else if(from.equals(Service.SERVER_NAME)) {
            type = Service.SERVER_NAME;
            from = "";
        } else {
            type = Service.USERNAME;
        }

        if (!from.equals(this.manager.getUsername())) {
            manager.rcvChatMsg(type, from, message);
        }

        for (iUser user : users.values()) {
            if (!from.equals(user.getUsername())) {
                user.rcvChatMsg(type, from, message);
            }
        }
    }

    /**
     * Check whether the name is already existing in this room
     * @param username target username
     * @return true is existing
     */
    public boolean existingName(String username) throws RemoteException {
        return manager.getUsername().equals(username) || users.containsKey(username);
    }

    @Override
    public void exit(String username) throws RemoteException {

    }

    @Override
    public void end() throws RemoteException {

    }

    /**
     * Add the manager user (CreateWhiteBoard) to the room
     * @param manager CreateWhiteBoard object
     * @return true if created
     * @throws RemoteException
     */
    @Override
    public boolean createRoom(iUser manager) throws RemoteException {

        if (this.manager != null) {
            return false;
        }

        if (manager instanceof CreateWhiteBoard) {
            this.manager = manager;
            usernameList.addElement(Service.MGR_NAME + manager.getUsername());
            manager.updateUserList(usernameList);
            System.out.println("Manager " + manager.getUsername() + " has created the room.");
            return true;
        }
        return false;
    }

    /**
     * Check whether the room is empty
     * @return true if the room is empty (non-manager user)
     * @throws RemoteException
     */
    @Override
    public boolean isEmptyRoom() throws RemoteException {
        return this.manager == null;
    }
}
