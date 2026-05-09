package com.rutik.systemdesign.lld.behavioral.iterator; /**
 * Iterator Pattern - Real World Example
 *
 * Scenario: Social Network - Friend Traversal
 *
 * A social network has users, each of whom has a list of friends. We want to
 * traverse a user's social graph without exposing whether the internal storage is
 * an array, linked list, or adjacency map. We provide:
 *
 *   - FriendListIterator  : visits direct friends in insertion order
 *   - MutualFriendIterator: visits only friends shared with another user
 *
 * Client code (the "feed" generator) works identically regardless of which
 * iterator it receives — the traversal strategy is pluggable.
 */

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

// ─── Domain Model ─────────────────────────────────────────────────────────────

/**
 * Represents a user profile in the social network.
 */
class UserProfile {
    private final String       username;
    private final List<UserProfile> friends = new ArrayList<>();

    public UserProfile(String username) {
        this.username = username;
    }

    public void addFriend(UserProfile friend) {
        if (!friends.contains(friend) && friend != this) {
            friends.add(friend);
        }
    }

    public List<UserProfile> getFriends() { return friends; }
    public String getUsername()           { return username; }

    @Override
    public String toString() { return "@" + username; }
}

// ─── ProfileIterator Interface ────────────────────────────────────────────────

/**
 * The iterator interface specialized for user profiles.
 */
interface ProfileIterator {
    boolean hasMore();
    UserProfile getNext();

    /** Convenience: reset to the beginning of the traversal. */
    void reset();
}

// ─── Social Network (Aggregate) ───────────────────────────────────────────────

/**
 * The collection interface. A SocialNetwork can produce iterators over its users.
 */
interface SocialNetwork {
    ProfileIterator createFriendsIterator(UserProfile user);
    ProfileIterator createMutualFriendsIterator(UserProfile user, UserProfile other);
}

// ─── Concrete Network ─────────────────────────────────────────────────────────

/**
 * Concrete implementation backed by an in-memory user registry.
 */
class InMemorySocialNetwork implements SocialNetwork {

    private final List<UserProfile> users = new ArrayList<>();

    public void addUser(UserProfile user) {
        users.add(user);
    }

    @Override
    public ProfileIterator createFriendsIterator(UserProfile user) {
        return new FriendListIterator(user);
    }

    @Override
    public ProfileIterator createMutualFriendsIterator(UserProfile user,
                                                        UserProfile other) {
        return new MutualFriendIterator(user, other);
    }
}

// ─── Concrete Iterator: FriendListIterator ────────────────────────────────────

/**
 * Iterates over all direct friends of a user in insertion order.
 * Models the simplest, most common traversal — "show my friend list".
 */
class FriendListIterator implements ProfileIterator {
    private final List<UserProfile> friends;
    private int                     cursor = 0;

    public FriendListIterator(UserProfile user) {
        // Defensive copy so structural changes to the user don't affect iteration
        this.friends = new ArrayList<>(user.getFriends());
    }

    @Override
    public boolean hasMore() {
        return cursor < friends.size();
    }

    @Override
    public UserProfile getNext() {
        if (!hasMore()) throw new NoSuchElementException("No more friends.");
        return friends.get(cursor++);
    }

    @Override
    public void reset() { cursor = 0; }
}

// ─── Concrete Iterator: MutualFriendIterator ─────────────────────────────────

/**
 * Iterates only over friends shared by two given users.
 * Models the "people you may know" / mutual friend feature.
 *
 * The set of mutual friends is computed lazily (on first call to hasMore/getNext)
 * so the cost is deferred until the iterator is actually used.
 */
class MutualFriendIterator implements ProfileIterator {
    private final UserProfile user;
    private final UserProfile other;

    private List<UserProfile> mutualFriends; // lazily initialised
    private int               cursor = 0;

    public MutualFriendIterator(UserProfile user, UserProfile other) {
        this.user  = user;
        this.other = other;
    }

    private void ensureInitialised() {
        if (mutualFriends != null) return;

        Set<UserProfile> otherFriendSet = new HashSet<>(other.getFriends());
        mutualFriends = new ArrayList<>();
        for (UserProfile friend : user.getFriends()) {
            if (otherFriendSet.contains(friend)) {
                mutualFriends.add(friend);
            }
        }
    }

    @Override
    public boolean hasMore() {
        ensureInitialised();
        return cursor < mutualFriends.size();
    }

    @Override
    public UserProfile getNext() {
        ensureInitialised();
        if (!hasMore()) throw new NoSuchElementException("No more mutual friends.");
        return mutualFriends.get(cursor++);
    }

    @Override
    public void reset() {
        cursor = 0;
        mutualFriends = null; // allow re-computation if graph changed
    }
}

// ─── Client: Feed Generator ───────────────────────────────────────────────────

/**
 * The client only depends on ProfileIterator — it doesn't know whether it is
 * traversing all friends, mutual friends, or any other strategy.
 */
class FeedGenerator {
    /**
     * Prints profile suggestions from the provided iterator.
     * Could be "you may also know" or "recommended content creators", etc.
     */
    public void printSuggestions(String heading, ProfileIterator iterator) {
        System.out.println("\n  " + heading);
        if (!iterator.hasMore()) {
            System.out.println("    (none)");
            return;
        }
        while (iterator.hasMore()) {
            System.out.println("    - " + iterator.getNext());
        }
    }
}

// ─── Main ─────────────────────────────────────────────────────────────────────

public class RealWorldExample {

    public static void main(String[] args) {
        System.out.println("=== Social Network Friend Traversal (Iterator Pattern) ===");

        // ── Build the social graph ───────────────────────────────────────────
        UserProfile alice   = new UserProfile("alice");
        UserProfile bob     = new UserProfile("bob");
        UserProfile charlie = new UserProfile("charlie");
        UserProfile diana   = new UserProfile("diana");
        UserProfile eve     = new UserProfile("eve");
        UserProfile frank   = new UserProfile("frank");

        // Alice's friends
        alice.addFriend(bob);
        alice.addFriend(charlie);
        alice.addFriend(diana);

        // Bob's friends (share charlie and diana with alice; also knows eve)
        bob.addFriend(alice);
        bob.addFriend(charlie);
        bob.addFriend(diana);
        bob.addFriend(eve);

        // Charlie's friends
        charlie.addFriend(alice);
        charlie.addFriend(bob);
        charlie.addFriend(frank);

        InMemorySocialNetwork network = new InMemorySocialNetwork();
        for (UserProfile u : List.of(alice, bob, charlie, diana, eve, frank)) {
            network.addUser(u);
        }

        FeedGenerator feed = new FeedGenerator();

        // ── Traverse alice's direct friends ──────────────────────────────────
        System.out.println("\n--- Alice's friend list ---");
        ProfileIterator aliceFriends = network.createFriendsIterator(alice);
        feed.printSuggestions("Alice's friends:", aliceFriends);

        // ── Traverse bob's direct friends ────────────────────────────────────
        System.out.println("\n--- Bob's friend list ---");
        ProfileIterator bobFriends = network.createFriendsIterator(bob);
        feed.printSuggestions("Bob's friends:", bobFriends);

        // ── Mutual friends of alice and bob ──────────────────────────────────
        System.out.println("\n--- Mutual friends: Alice & Bob ---");
        ProfileIterator mutualAB = network.createMutualFriendsIterator(alice, bob);
        feed.printSuggestions("Alice & Bob share:", mutualAB);

        // ── Mutual friends of alice and charlie ──────────────────────────────
        System.out.println("\n--- Mutual friends: Alice & Charlie ---");
        ProfileIterator mutualAC = network.createMutualFriendsIterator(alice, charlie);
        feed.printSuggestions("Alice & Charlie share:", mutualAC);

        // ── No mutual friends case ────────────────────────────────────────────
        System.out.println("\n--- Mutual friends: Alice & Eve ---");
        ProfileIterator mutualAE = network.createMutualFriendsIterator(alice, eve);
        feed.printSuggestions("Alice & Eve share:", mutualAE);

        // ── Demonstrate reset ─────────────────────────────────────────────────
        System.out.println("\n--- Reset and re-traverse Alice's friends ---");
        aliceFriends.reset();
        feed.printSuggestions("Alice's friends (after reset):", aliceFriends);
    }
}
