package com.rutik.systemdesign.lld.behavioral.mediator; /**
 * Mediator Pattern - Real World Example
 *
 * Scenario: Chat Room Mediator
 *
 * Users in a chat room never communicate directly with each other. Instead, each
 * user sends messages to the ChatRoom (the Mediator), which decides how to
 * distribute them:
 *
 *   - Broadcast: send a message to all other users in the room.
 *   - Direct message (DM): send a private message to one specific user.
 *   - Muting: the room tracks muted users; muted users can send but no one receives.
 *   - Join / Leave events: the room announces them to everyone.
 *
 * This keeps User objects simple — they only know how to send/receive strings.
 * All routing, filtering, and policy logic lives in ChatRoom.
 */

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// ─── ChatMediator Interface ───────────────────────────────────────────────────

/**
 * Declares the contract for the chat room mediator.
 */
interface ChatMediator {
    void addUser(ChatUser user);
    void removeUser(ChatUser user);
    void broadcast(ChatUser sender, String message);
    void directMessage(ChatUser sender, String recipientName, String message);
    void mute(String username);
    void unmute(String username);
}

// ─── Colleague: ChatUser ──────────────────────────────────────────────────────

/**
 * A participant in the chat room. Users hold a reference to the mediator and
 * never communicate with other users directly.
 */
class ChatUser {
    private final String      username;
    private final ChatMediator mediator;

    public ChatUser(String username, ChatMediator mediator) {
        this.username = username;
        this.mediator = mediator;
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    /** Joins the chat room. */
    public void join() {
        mediator.addUser(this);
    }

    /** Leaves the chat room. */
    public void leave() {
        mediator.removeUser(this);
    }

    /** Sends a message to everyone in the room. */
    public void send(String message) {
        System.out.println("[" + username + " -> ALL] " + message);
        mediator.broadcast(this, message);
    }

    /** Sends a private message to a specific user by name. */
    public void sendDM(String recipientName, String message) {
        System.out.println("[" + username + " -> " + recipientName + " (DM)] " + message);
        mediator.directMessage(this, recipientName, message);
    }

    // ── Reception ─────────────────────────────────────────────────────────────

    /** Called by the mediator when a message is delivered to this user. */
    public void receive(String senderName, String message) {
        System.out.println("  >> " + username + " received from " + senderName + ": \"" + message + "\"");
    }

    /** Called by the mediator to deliver a system / event notification. */
    public void notify(String event) {
        System.out.println("  [" + username + "] " + event);
    }

    public String getUsername() { return username; }
}

// ─── ConcreteMediator: ChatRoom ───────────────────────────────────────────────

/**
 * The ChatRoom mediator handles all message routing, filtering, and policy.
 * Users only ever talk to this class — never to each other.
 */
class ChatRoom implements ChatMediator {
    private final String            roomName;
    private final List<ChatUser>    users   = new ArrayList<>();
    private final Set<String>       muted   = new HashSet<>();

    public ChatRoom(String roomName) {
        this.roomName = roomName;
    }

    // ── Membership ───────────────────────────────────────────────────────────

    @Override
    public void addUser(ChatUser user) {
        users.add(user);
        System.out.println("  [" + roomName + "] " + user.getUsername() + " joined the room.");
        // Notify all others of the new arrival
        for (ChatUser u : users) {
            if (u != user) {
                u.notify(user.getUsername() + " has joined " + roomName + ".");
            }
        }
    }

    @Override
    public void removeUser(ChatUser user) {
        users.remove(user);
        System.out.println("  [" + roomName + "] " + user.getUsername() + " left the room.");
        for (ChatUser u : users) {
            u.notify(user.getUsername() + " has left " + roomName + ".");
        }
    }

    // ── Messaging ────────────────────────────────────────────────────────────

    @Override
    public void broadcast(ChatUser sender, String message) {
        if (muted.contains(sender.getUsername())) {
            System.out.println("  [" + roomName + "] " + sender.getUsername()
                    + " is muted — message suppressed.");
            return;
        }
        for (ChatUser user : users) {
            if (user != sender) {
                user.receive(sender.getUsername(), message);
            }
        }
    }

    @Override
    public void directMessage(ChatUser sender, String recipientName, String message) {
        if (muted.contains(sender.getUsername())) {
            System.out.println("  [" + roomName + "] " + sender.getUsername()
                    + " is muted — DM suppressed.");
            return;
        }
        ChatUser recipient = findUser(recipientName);
        if (recipient == null) {
            System.out.println("  [" + roomName + "] User '" + recipientName + "' not found.");
            return;
        }
        recipient.receive("DM from " + sender.getUsername(), message);
    }

    // ── Moderation ───────────────────────────────────────────────────────────

    @Override
    public void mute(String username) {
        muted.add(username);
        System.out.println("  [" + roomName + "] " + username + " has been muted.");
        ChatUser user = findUser(username);
        if (user != null) user.notify("You have been muted in " + roomName + ".");
    }

    @Override
    public void unmute(String username) {
        muted.remove(username);
        System.out.println("  [" + roomName + "] " + username + " has been unmuted.");
        ChatUser user = findUser(username);
        if (user != null) user.notify("You have been unmuted in " + roomName + ".");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ChatUser findUser(String username) {
        return users.stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
    }
}

// ─── Main ─────────────────────────────────────────────────────────────────────

public class RealWorldExample {

    public static void main(String[] args) {
        System.out.println("=== Chat Room Mediator (Mediator Pattern) ===\n");

        // Create the room (mediator)
        ChatRoom room = new ChatRoom("general");

        // Create users — they all share the same mediator
        ChatUser alice  = new ChatUser("alice",  room);
        ChatUser bob    = new ChatUser("bob",    room);
        ChatUser charlie= new ChatUser("charlie",room);
        ChatUser dave   = new ChatUser("dave",   room);

        // ── Users join ───────────────────────────────────────────────────────
        System.out.println("--- Joining ---");
        alice.join();
        bob.join();
        charlie.join();

        // ── Broadcast messages ───────────────────────────────────────────────
        System.out.println("\n--- Broadcast messages ---");
        alice.send("Hey everyone! Glad to be here.");
        bob.send("Hi Alice! Welcome.");

        // ── Direct message ───────────────────────────────────────────────────
        System.out.println("\n--- Direct messages ---");
        charlie.sendDM("alice", "Hey Alice, can we chat?");
        alice.sendDM("charlie", "Sure, what's up?");

        // ── A new user joins mid-conversation ────────────────────────────────
        System.out.println("\n--- Late joiner ---");
        dave.join();
        dave.send("Hello everyone!");

        // ── Moderation: mute a user ──────────────────────────────────────────
        System.out.println("\n--- Moderation: muting bob ---");
        room.mute("bob");
        bob.send("Can anyone hear me?"); // suppressed
        bob.sendDM("alice", "Private message?"); // also suppressed

        // ── Unmute ───────────────────────────────────────────────────────────
        System.out.println("\n--- Unmuting bob ---");
        room.unmute("bob");
        bob.send("I'm back!");

        // ── DM to unknown user ────────────────────────────────────────────────
        System.out.println("\n--- DM to unknown user ---");
        alice.sendDM("zara", "Are you there?");

        // ── User leaves ──────────────────────────────────────────────────────
        System.out.println("\n--- Charlie leaves ---");
        charlie.leave();
        alice.send("See you later, Charlie... oh wait.");
    }
}
