package com.rutik.systemdesign.lld.creational.factory_method;

import java.util.HashMap;
import java.util.Map;

/**
 * FACTORY METHOD PATTERN — Real World Example
 *
 * Scenario: Multi-Channel Notification System
 *
 * Problem:
 *   An application sends notifications through different channels: Email, SMS,
 *   and Push (mobile). The sending logic (compose message, validate recipient,
 *   log the result) is the same regardless of channel. Only the delivery
 *   mechanism differs. Hard-coding `new EmailNotification()` everywhere creates
 *   tight coupling and makes adding new channels require editing existing code.
 *
 * Solution:
 *   A NotificationSender hierarchy where the base class implements the full
 *   send workflow (validate → create notification → deliver → log), and each
 *   subclass overrides `createNotification()` — the factory method — to produce
 *   the correct channel-specific Notification object.
 *
 * Adding a new channel (e.g., Slack) = add SlackNotification + SlackSender.
 * Zero changes to existing code (Open/Closed Principle).
 *
 * Run: javac RealWorldExample.java && java NotificationDemo
 */

// ─────────────────────────────────────────────────────────────────────────────
// Product interface — all notification channels implement this
// ─────────────────────────────────────────────────────────────────────────────
interface Notification {
    void deliver(String recipient, String message);
    String getChannelName();
    boolean validateRecipient(String recipient);
}

// ─────────────────────────────────────────────────────────────────────────────
// Concrete Products — one per notification channel
// ─────────────────────────────────────────────────────────────────────────────
class EmailNotification implements Notification {

    @Override
    public void deliver(String recipient, String message) {
        System.out.println("  [EMAIL] To: " + recipient);
        System.out.println("  [EMAIL] Subject: App Notification");
        System.out.println("  [EMAIL] Body: " + message);
        System.out.println("  [EMAIL] Status: Sent via SMTP server");
    }

    @Override
    public String getChannelName() {
        return "Email";
    }

    @Override
    public boolean validateRecipient(String recipient) {
        // Simplified: must contain @
        return recipient != null && recipient.contains("@");
    }
}

class SMSNotification implements Notification {

    @Override
    public void deliver(String recipient, String message) {
        // Truncate to SMS character limit
        String smsText = message.length() > 160 ? message.substring(0, 157) + "..." : message;
        System.out.println("  [SMS] To: " + recipient);
        System.out.println("  [SMS] Message (" + smsText.length() + " chars): " + smsText);
        System.out.println("  [SMS] Status: Sent via Twilio API");
    }

    @Override
    public String getChannelName() {
        return "SMS";
    }

    @Override
    public boolean validateRecipient(String recipient) {
        // Simplified: must start with + and be at least 10 digits
        return recipient != null && recipient.startsWith("+") && recipient.length() >= 10;
    }
}

class PushNotification implements Notification {

    @Override
    public void deliver(String recipient, String message) {
        System.out.println("  [PUSH] Device token: " + recipient);
        System.out.println("  [PUSH] Title: New Notification");
        System.out.println("  [PUSH] Body: " + message);
        System.out.println("  [PUSH] Status: Sent via FCM/APNs");
    }

    @Override
    public String getChannelName() {
        return "Push";
    }

    @Override
    public boolean validateRecipient(String recipient) {
        // Simplified: device token must be 64+ characters
        return recipient != null && recipient.length() >= 20;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Abstract Creator — defines the notification sending workflow
// ─────────────────────────────────────────────────────────────────────────────
abstract class NotificationSender {

    // THE FACTORY METHOD — subclasses override this to return their channel's notification
    protected abstract Notification createNotification();

    /**
     * Template method: orchestrates the complete send workflow.
     * Calls createNotification() internally — never references concrete types.
     */
    public final NotificationResult send(String recipient, String message) {
        Notification notification = createNotification(); // factory method call

        System.out.println("\n[" + notification.getChannelName() + "Sender] Sending notification...");

        // Step 1: Validate
        if (!notification.validateRecipient(recipient)) {
            String error = "Invalid recipient for " + notification.getChannelName() + ": " + recipient;
            System.out.println("  [VALIDATION FAILED] " + error);
            return NotificationResult.failure(notification.getChannelName(), error);
        }

        // Step 2: Deliver
        try {
            notification.deliver(recipient, message);
        } catch (Exception e) {
            return NotificationResult.failure(notification.getChannelName(), e.getMessage());
        }

        // Step 3: Log
        System.out.println("  [LOG] Notification logged at " + System.currentTimeMillis());

        return NotificationResult.success(notification.getChannelName());
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Concrete Creators — each overrides the factory method
// ─────────────────────────────────────────────────────────────────────────────
class EmailSender extends NotificationSender {

    @Override
    protected Notification createNotification() {
        return new EmailNotification();
    }
}

class SMSSender extends NotificationSender {

    @Override
    protected Notification createNotification() {
        return new SMSNotification();
    }
}

class PushSender extends NotificationSender {

    @Override
    protected Notification createNotification() {
        return new PushNotification();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Value object for send results
// ─────────────────────────────────────────────────────────────────────────────
class NotificationResult {
    private final boolean success;
    private final String channel;
    private final String message;

    private NotificationResult(boolean success, String channel, String message) {
        this.success = success;
        this.channel = channel;
        this.message = message;
    }

    public static NotificationResult success(String channel) {
        return new NotificationResult(true, channel, "Delivered successfully");
    }

    public static NotificationResult failure(String channel, String reason) {
        return new NotificationResult(false, channel, reason);
    }

    @Override
    public String toString() {
        return "[" + channel + "] " + (success ? "SUCCESS" : "FAILURE") + ": " + message;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Notification Service — selects the sender based on user preference
// (Shows how configuration drives creator selection — no concrete type coupling)
// ─────────────────────────────────────────────────────────────────────────────
class NotificationService {

    private final Map<String, NotificationSender> senders = new HashMap<>();

    public NotificationService() {
        senders.put("email", new EmailSender());
        senders.put("sms", new SMSSender());
        senders.put("push", new PushSender());
    }

    public NotificationResult notify(String channel, String recipient, String message) {
        NotificationSender sender = senders.get(channel.toLowerCase());
        if (sender == null) {
            return NotificationResult.failure(channel, "Unknown channel: " + channel);
        }
        return sender.send(recipient, message);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Demo Entry Point
// ─────────────────────────────────────────────────────────────────────────────
public class RealWorldExample {

    public static void main(String[] args) {
        System.out.println("=== Factory Method Pattern: Notification System Demo ===");

        NotificationService service = new NotificationService();

        // ── Successful notifications ──────────────────────────────────────────
        System.out.println("\n--- Sending Email ---");
        NotificationResult r1 = service.notify("email", "alice@example.com",
                "Your order #12345 has been shipped!");
        System.out.println("Result: " + r1);

        System.out.println("\n--- Sending SMS ---");
        NotificationResult r2 = service.notify("sms", "+14155551234",
                "Your OTP is 847291. Valid for 5 minutes.");
        System.out.println("Result: " + r2);

        System.out.println("\n--- Sending Push ---");
        NotificationResult r3 = service.notify("push", "device_token_abc123xyz_fedcba987654",
                "You have a new message from Bob.");
        System.out.println("Result: " + r3);

        // ── Validation failure ────────────────────────────────────────────────
        System.out.println("\n--- Invalid SMS recipient ---");
        NotificationResult r4 = service.notify("sms", "notaphone",
                "This should fail validation");
        System.out.println("Result: " + r4);

        System.out.println("\n--- Invalid Email recipient ---");
        NotificationResult r5 = service.notify("email", "notanemail",
                "This should also fail");
        System.out.println("Result: " + r5);

        // ── Unknown channel ───────────────────────────────────────────────────
        System.out.println("\n--- Unknown channel ---");
        NotificationResult r6 = service.notify("fax", "555-1234", "Hello from 1995");
        System.out.println("Result: " + r6);

        // ── Direct sender usage — shows polymorphism ──────────────────────────
        System.out.println("\n--- Direct sender polymorphism ---");
        NotificationSender[] senders = {
            new EmailSender(),
            new SMSSender(),
            new PushSender()
        };

        String[] recipients = {"admin@company.com", "+12025551111", "abcdef1234567890abcdef12"};
        String message = "System maintenance at midnight tonight.";

        for (int i = 0; i < senders.length; i++) {
            NotificationResult result = senders[i].send(recipients[i], message);
            System.out.println("  => " + result);
        }

        System.out.println("\n=== Demo complete ===");
    }
}
