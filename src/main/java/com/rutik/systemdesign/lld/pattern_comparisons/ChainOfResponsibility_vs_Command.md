# Chain of Responsibility vs Command Pattern

## Quick Summary

- **Chain of Responsibility (CoR)**: Passes a request along a chain of handlers; each handler decides to process or forward it.
- **Command**: Encapsulates a request as an object; an invoker triggers execution on a known receiver.

---

## Intuition

> **One-line analogy**: Chain of Responsibility is a help desk escalation chain (your issue bounces from tier 1 to tier 2 to manager until someone handles it); Command is a written work order (a specific action captured for a known executor).

**Mental model**: CoR is about *routing* — the sender doesn't know which handler will process the request; it fires into the chain and trusts the chain to route it correctly (or ignore it). Command is about *encapsulation* — a known invoker fires a specific command at a known receiver; the value is that the action is an object (queued, undoable, loggable). CoR is dynamic dispatch through a chain; Command is explicit delegation to a receiver.

**Why it matters**: Both decouple sender from receiver, but differently. CoR is for flexible, cascading decision logic (middleware pipelines, event bubbling). Command is for action history, undo/redo, and task queues.

**Key insight**: CoR — multiple potential handlers, one will respond (or none). Command — one intended receiver, but the action is captured as a replayable object. If the key feature is "pass along the chain," use CoR. If the key feature is "capture and replay the action," use Command.

---

## Side-by-Side Comparison

| Aspect             | Chain of Responsibility                              | Command                                              |
|--------------------|------------------------------------------------------|------------------------------------------------------|
| **Intent**         | Decouple sender from receiver by giving multiple handlers a chance to handle the request | Encapsulate a request as an object to parameterize, queue, log, or undo it |
| **Structure**      | Linked list / chain of handlers; each holds a next-handler reference | Invoker → Command interface → ConcreteCommand → Receiver |
| **Key Difference** | About ROUTING — who should handle this request?      | About ENCAPSULATION — what is the request, and can it be reversed? |
| **Use When**       | The handler is not known ahead of time and must be determined dynamically | You need undo/redo, request queuing, logging, or sender-receiver decoupling |

---

## Java Code Examples

### Chain of Responsibility — Support Ticket Escalation

```java
// Handler interface
public abstract class SupportHandler {
    protected SupportHandler nextHandler;

    public SupportHandler setNext(SupportHandler next) {
        this.nextHandler = next;
        return next;   // enables fluent chaining: l1.setNext(l2).setNext(l3)
    }

    public abstract void handle(SupportTicket ticket);
}

// Request object
public class SupportTicket {
    private final String id;
    private final int severity;   // 1 = low, 2 = medium, 3 = high
    private final String description;

    public SupportTicket(String id, int severity, String description) {
        this.id = id;
        this.severity = severity;
        this.description = description;
    }

    public int getSeverity() { return severity; }
    public String getId()    { return id; }

    @Override
    public String toString() {
        return "Ticket[" + id + ", severity=" + severity + ": " + description + "]";
    }
}

// Concrete Handler 1 — handles low severity tickets
public class L1Support extends SupportHandler {
    @Override
    public void handle(SupportTicket ticket) {
        if (ticket.getSeverity() == 1) {
            System.out.println("L1 Support resolved: " + ticket);
        } else if (nextHandler != null) {
            System.out.println("L1 cannot handle severity " + ticket.getSeverity() + " — escalating...");
            nextHandler.handle(ticket);
        } else {
            System.out.println("No handler available for: " + ticket);
        }
    }
}

// Concrete Handler 2 — handles medium severity tickets
public class L2Support extends SupportHandler {
    @Override
    public void handle(SupportTicket ticket) {
        if (ticket.getSeverity() <= 2) {
            System.out.println("L2 Support resolved: " + ticket);
        } else if (nextHandler != null) {
            System.out.println("L2 cannot handle severity " + ticket.getSeverity() + " — escalating...");
            nextHandler.handle(ticket);
        } else {
            System.out.println("No handler available for: " + ticket);
        }
    }
}

// Concrete Handler 3 — handles all remaining tickets (including high severity)
public class L3Support extends SupportHandler {
    @Override
    public void handle(SupportTicket ticket) {
        System.out.println("L3 Engineering resolved: " + ticket);
    }
}

// Client
public class CoRDemo {
    public static void main(String[] args) {
        // Build the chain
        L1Support l1 = new L1Support();
        L2Support l2 = new L2Support();
        L3Support l3 = new L3Support();
        l1.setNext(l2).setNext(l3);

        l1.handle(new SupportTicket("T001", 1, "Password reset"));
        l1.handle(new SupportTicket("T002", 2, "Application crash"));
        l1.handle(new SupportTicket("T003", 3, "Data breach investigation"));
    }
}
```

**Output:**
```
L1 Support resolved: Ticket[T001, severity=1: Password reset]
L1 cannot handle severity 2 — escalating...
L2 Support resolved: Ticket[T002, severity=2: Application crash]
L1 cannot handle severity 3 — escalating...
L2 cannot handle severity 3 — escalating...
L3 Engineering resolved: Ticket[T003, severity=3: Data breach investigation]
```

---

### Command Pattern — Home Automation Remote Control

```java
// Command interface
public interface Command {
    void execute();
    void undo();
}

// Receivers
public class Light {
    private final String location;
    public Light(String location) { this.location = location; }
    public void turnOn()  { System.out.println(location + " light ON"); }
    public void turnOff() { System.out.println(location + " light OFF"); }
}

public class Thermostat {
    private int temperature = 20;
    public void setTemperature(int temp) {
        System.out.println("Thermostat set to " + temp + "°C");
        this.temperature = temp;
    }
    public int getTemperature() { return temperature; }
}

// Concrete Commands
public class TurnOnLightCommand implements Command {
    private final Light light;

    public TurnOnLightCommand(Light light) { this.light = light; }

    @Override
    public void execute() { light.turnOn(); }

    @Override
    public void undo()    { light.turnOff(); }
}

public class SetThermostatCommand implements Command {
    private final Thermostat thermostat;
    private final int newTemp;
    private int previousTemp;

    public SetThermostatCommand(Thermostat thermostat, int newTemp) {
        this.thermostat = thermostat;
        this.newTemp = newTemp;
    }

    @Override
    public void execute() {
        previousTemp = thermostat.getTemperature();
        thermostat.setTemperature(newTemp);
    }

    @Override
    public void undo() {
        thermostat.setTemperature(previousTemp);
    }
}

// Invoker — holds command slots and an undo stack
public class RemoteControl {
    private final Deque<Command> history = new ArrayDeque<>();

    public void pressButton(Command command) {
        command.execute();
        history.push(command);
    }

    public void pressUndo() {
        if (!history.isEmpty()) {
            System.out.print("[UNDO] ");
            history.pop().undo();
        } else {
            System.out.println("Nothing to undo.");
        }
    }
}

// Client
public class CommandDemo {
    public static void main(String[] args) {
        Light livingRoomLight = new Light("Living Room");
        Thermostat thermostat = new Thermostat();
        RemoteControl remote = new RemoteControl();

        remote.pressButton(new TurnOnLightCommand(livingRoomLight));
        remote.pressButton(new SetThermostatCommand(thermostat, 24));

        remote.pressUndo();  // undo thermostat change
        remote.pressUndo();  // undo light on
        remote.pressUndo();  // nothing left
    }
}
```

**Output:**
```
Living Room light ON
Thermostat set to 24°C
[UNDO] Thermostat set to 20°C
[UNDO] Living Room light OFF
Nothing to undo.
```

---

## Key Structural Differences — ASCII Class Diagrams

### Chain of Responsibility

```
Client ──> +---------------+     +---------------+     +---------------+
           |  L1Support    |---->|  L2Support    |---->|  L3Support    |
           |               |     |               |     |               |
           |+ handle(req)  |     |+ handle(req)  |     |+ handle(req)  |
           +---------------+     +---------------+     +---------------+
                 ^                      ^                      ^
                 |                      |                      |
           +----+---------------------------------------------+-------+
           |          <<abstract>> SupportHandler                      |
           |  - nextHandler: SupportHandler                            |
           |  + setNext(handler): SupportHandler                       |
           |  + handle(ticket) [abstract]                              |
           +-----------------------------------------------------------+

Request travels along the chain until a handler claims it (or it falls off the end).
```

### Command

```
+--------+      +------------------+      +------------------+
| Client |----->|    Invoker       |      |    Receiver      |
+--------+      |  (RemoteControl) |      |  (Light /        |
                |------------------|      |   Thermostat)    |
                |- history: Deque  |      +------------------+
                |+ pressButton()   |              ^
                |+ pressUndo()     |              |
                +------------------+      +-------+--------+
                        |                 | ConcreteCommand|
                        |                 |+ execute()     |
                        +---------------->|+ undo()        |
                                          +----------------+
                                                 ^
                                    +------------+------------+
                                    |                         |
                        +-------------------+  +------------------------+
                        | TurnOnLightCommand|  | SetThermostatCommand   |
                        |+ execute()        |  |+ execute()             |
                        |+ undo()           |  |+ undo()                |
                        +-------------------+  +------------------------+
```

---

## Decision Guide

Use **Chain of Responsibility** when:
- More than one handler may be able to handle a request and the correct one is determined at runtime
- You want to decouple the sender from knowing which specific object handles it
- The set of handlers or their order may change dynamically (middleware pipelines, filter chains)
- A request might legitimately go unhandled (fall through the chain)

Use **Command** when:
- You need to parameterize actions (store, pass, and invoke them later)
- You need undo/redo capability
- You need to queue, schedule, or log operations
- You want to support macro commands (composite of commands)
- The receiver is known and fixed — the question is WHEN to execute, not WHO handles it

---

## Common Confusion Points

1. **Both decouple sender from receiver** — but differently. CoR decouples by not knowing *who* will handle. Command decouples by not knowing *when* the request will be executed.

2. **CoR may not process the request at all** — In Command, execute() is always called (eventually). In CoR, the request can fall off the end of the chain with no handler claiming it.

3. **CoR handlers are ordered** — the sequence matters. Command objects are typically independent and unordered (though undo stack order matters).

4. **Request object in CoR vs Command** — In CoR the request is passed as a parameter. In Command, the request *is* the object, with its parameters baked in.

5. **Only one handler acts in CoR (typically)** — Once a handler claims the ticket, it stops. Command's Invoker can call many commands in sequence.

---

## Real-World Examples

| Chain of Responsibility | Command |
|-------------------------|---------|
| HTTP middleware / filter chains (`javax.servlet.Filter`) | Menu items and toolbar buttons in GUIs |
| Spring Security filter chain | Database transaction log (replay / rollback) |
| Exception handling chains (try-catch nesting) | Job queues (Celery, Sidekiq, RabbitMQ tasks) |
| Logging level hierarchy (DEBUG → INFO → WARN → ERROR) | Git commits (reversible history) |
| Help desk ticket escalation (L1 → L2 → L3) | `java.awt.event` action listeners |
| AWS API Gateway authorizer chain | Macro recording in IDEs and spreadsheets |

---

## Can They Work Together?

Yes — this is a powerful and common combination. Use **CoR to route** the request to the right handler, then use a **Command to execute** the action in an undoable, loggable way.

```java
// Combined: CoR routes, Command executes

public abstract class RequestRouter {
    protected RequestRouter next;

    public RequestRouter setNext(RequestRouter next) {
        this.next = next;
        return next;
    }

    // Returns a Command if this handler can process the request, else delegates
    public abstract Optional<Command> route(SupportTicket ticket);
}

public class AutomatedRouter extends RequestRouter {
    @Override
    public Optional<Command> route(SupportTicket ticket) {
        if (ticket.getSeverity() == 1) {
            // Return a Command that encapsulates the auto-resolve action
            return Optional.of(new AutoResolveCommand(ticket));
        }
        return next != null ? next.route(ticket) : Optional.empty();
    }
}

public class HumanRouter extends RequestRouter {
    private final AgentQueue queue;

    public HumanRouter(AgentQueue queue) { this.queue = queue; }

    @Override
    public Optional<Command> route(SupportTicket ticket) {
        // Return a Command that enqueues the ticket for a human agent
        return Optional.of(new AssignToAgentCommand(ticket, queue));
    }
}

// Orchestrator
public class SupportOrchestrator {
    private final RequestRouter routerChain;
    private final Deque<Command> auditLog = new ArrayDeque<>();

    public SupportOrchestrator(RequestRouter routerChain) {
        this.routerChain = routerChain;
    }

    public void process(SupportTicket ticket) {
        routerChain.route(ticket).ifPresent(cmd -> {
            cmd.execute();          // Command executes the routed action
            auditLog.push(cmd);     // Logged for undo / audit trail
        });
    }

    public void rollbackLast() {
        if (!auditLog.isEmpty()) auditLog.pop().undo();
    }
}
```

**Flow summary:**
```
Incoming Request
     |
     v
[CoR Chain] — routes request to the appropriate handler
     |
     v
[Command returned] — encapsulates the action + receiver
     |
     v
[Invoker executes Command] — logged to audit trail, supports undo
```

This pattern is common in event-driven architectures, workflow engines, and API gateway implementations where requests must first be routed and then reliably executed.
