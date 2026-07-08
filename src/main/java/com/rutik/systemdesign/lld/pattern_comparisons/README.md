# Design Pattern Comparisons - Master Reference

This directory contains deep-dive comparisons of commonly confused GoF design patterns. Each file provides side-by-side analysis, code examples, and interview guidance.

---

## Intuition

> **One-line analogy**: Pattern comparisons are like knowing not just the tools in your toolbox but which job each one was designed for — a screwdriver and a drill both turn things, but they're not interchangeable.

**Mental model**: Many GoF patterns look structurally identical (Strategy and State both delegate to an interface; Decorator and Proxy both wrap an object). The confusion clears when you ask "what problem is this solving?" — Strategy swaps algorithms; State encodes lifecycle. Decorator adds behavior; Proxy controls access. The distinction always lives in intent, not structure.

**Why it matters**: Interviewers often deliberately present confusable patterns. Knowing their structural similarity AND the intent difference is the sign of genuine understanding versus memorization.

**Key insight**: When stuck between two patterns, ask: "Who controls the switch?" and "What changes over the object's lifetime?" The answers usually disambiguate immediately.

---

## Master Comparison Matrix - All 23 GoF Patterns

| Pattern | Category | Intent | Scope | Key Mechanism |
|---------|----------|--------|-------|---------------|
| Abstract Factory | Creational | Create families of related objects | Object | Delegates to factory objects |
| Builder | Creational | Construct complex objects step by step | Object | Separates construction from representation |
| Factory Method | Creational | Define interface for object creation | Class | Subclasses decide which class to instantiate |
| Prototype | Creational | Clone existing objects | Object | Copies an existing object |
| Singleton | Creational | Ensure only one instance exists | Object | Controls instance creation |
| Adapter | Structural | Convert interface to another | Class/Object | Wraps an object/class with a new interface |
| Bridge | Structural | Separate abstraction from implementation | Object | Composition over inheritance |
| Composite | Structural | Tree structure of objects | Object | Recursive composition |
| Decorator | Structural | Add responsibilities dynamically | Object | Wraps object, adds behavior |
| Facade | Structural | Simplified interface to subsystem | Object | Delegates to subsystem objects |
| Flyweight | Structural | Share fine-grained objects | Object | Shared state between many small objects |
| Proxy | Structural | Surrogate or placeholder | Object | Wraps object, controls access |
| Chain of Responsibility | Behavioral | Pass request along handler chain | Object | Linked list of handlers |
| Command | Behavioral | Encapsulate request as object | Object | Encapsulates action + receiver |
| Interpreter | Behavioral | Language grammar interpretation | Class | Composite of terminal/nonterminal expressions |
| Iterator | Behavioral | Sequential access to collection | Object | Cursor over aggregate |
| Mediator | Behavioral | Centralize object communication | Object | Central hub coordinates colleagues |
| Memento | Behavioral | Capture and restore object state | Object | Originator/Caretaker/Memento trio |
| Observer | Behavioral | Notify dependents of state change | Object | Subject notifies list of observers |
| State | Behavioral | Alter behavior when state changes | Object | Delegates to current state object |
| Strategy | Behavioral | Encapsulate interchangeable algorithms | Object | Delegates to strategy object |
| Template Method | Behavioral | Define algorithm skeleton in base class | Class | Inheritance, hook methods |
| Visitor | Behavioral | Add operations without changing classes | Object | Double dispatch |

---

## Pattern Relationship Map

```mermaid
flowchart LR
    classDef base   fill:#e5c07b,stroke:#f39c12,color:#1a1a1a
    classDef frozen fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef mathOp fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold

    subgraph CR["CREATIONAL"]
        direction TB
        FM["Factory Method"] -->|extends| AF["Abstract Factory<br/>(factory of factories)"]
        BLD["Builder<br/>complex object construction"]
        PRO["Prototype<br/>clone-based creation"]
        SIN["Singleton<br/>single instance"]
    end

    subgraph ST["STRUCTURAL"]
        direction TB
        ADP["Adapter<br/>wraps incompatible interface (retrofit)"]
        BRI["Bridge<br/>separates abstraction + implementation"]
        FAC["Facade<br/>simplifies subsystem"]
        DEC["Decorator<br/>wraps, adds behavior (stackable)"]
        PRX["Proxy<br/>wraps, controls access"]
        CMP["Composite<br/>composes tree structures (part-whole)"]
        FLY["Flyweight<br/>shares many fine-grained objects"]
    end

    subgraph BE["BEHAVIORAL"]
        direction TB
        STR["Strategy<br/>choose algorithm at runtime"]
        STA["State<br/>behavior changes with state"]
        TM["Template Method<br/>fixed skeleton, variable steps"]
        CMD["Command<br/>encapsulate action as object"]
        COR["Chain of Responsibility<br/>pass request along chain"]
        OBS["Observer<br/>event notification (1-to-many)"]
        MED["Mediator<br/>centralize communication (many-to-many)"]
        ITR["Iterator<br/>traverse collection"]
        MEM["Memento<br/>undo/snapshot"]
        VIS["Visitor<br/>add ops without modifying classes"]
        INT["Interpreter<br/>parse/execute grammar"]
    end

    SIN -.->|often combined with| FM
    STA -.->|looks like| STR

    class FM,AF,BLD,PRO,SIN base
    class ADP,BRI,FAC,DEC,PRX,CMP,FLY frozen
    class STR,STA,TM,CMD,COR,OBS,MED,ITR,MEM,VIS,INT mathOp
```

*Patterns cluster by GoF category — gold for Creational, purple for Structural, orange for Behavioral. The two dashed edges are the classic look-alike traps: Singleton is frequently paired with Factory Method, and State's structure is often confused with Strategy's — exactly the pairs the comparison table below digs into.*

---

## Commonly Confused Pairs (Files in this directory)

| File | Patterns Compared | Core Confusion |
|------|-------------------|----------------|
| [Strategy_vs_State.md](Strategy_vs_State.md) | Strategy vs State | Both delegate to an object; differ in *who* drives change |
| [Factory_vs_AbstractFactory_vs_Builder.md](Factory_vs_AbstractFactory_vs_Builder.md) | Factory Method vs Abstract Factory vs Builder | All create objects; differ in complexity and structure |
| [Adapter_vs_Bridge_vs_Facade.md](Adapter_vs_Bridge_vs_Facade.md) | Adapter vs Bridge vs Facade | All wrap; differ in intent and timing |
| [Decorator_vs_Proxy.md](Decorator_vs_Proxy.md) | Decorator vs Proxy | Both wrap objects; differ in purpose |
| [Observer_vs_Mediator.md](Observer_vs_Mediator.md) | Observer vs Mediator | Both handle communication; differ in topology |
| [Command_vs_Strategy.md](Command_vs_Strategy.md) | Command vs Strategy | Both encapsulate behavior; differ in purpose |
| [Template_vs_Strategy.md](Template_vs_Strategy.md) | Template Method vs Strategy | Both vary steps; inheritance vs composition |
| [Composite_vs_Decorator.md](Composite_vs_Decorator.md) | Composite vs Decorator | Both use recursive composition |
| [ChainOfResponsibility_vs_Command.md](ChainOfResponsibility_vs_Command.md) | Chain of Responsibility vs Command | Both handle requests |

---

## Quick Selection Guide

```mermaid
flowchart LR
    classDef io     fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef base   fill:#e5c07b,stroke:#f39c12,color:#1a1a1a
    classDef frozen fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef mathOp fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold

    ROOT(["What do you<br/>need to do?"]) --> CAT_CR(["CREATE objects"])
    ROOT --> CAT_ST(["STRUCTURE classes"])
    ROOT --> CAT_BE(["DEFINE behavior"])

    CAT_CR -->|"type varies by subclass"| QFM(["Factory Method"])
    CAT_CR -->|"families of related objects"| QAF(["Abstract Factory"])
    CAT_CR -->|"many optional parts"| QBLD(["Builder"])
    CAT_CR -->|"copy an existing object"| QPRO(["Prototype"])
    CAT_CR -->|"only one instance ever"| QSIN(["Singleton"])

    CAT_ST -->|"incompatible interfaces"| QADP(["Adapter"])
    CAT_ST -->|"separate abstraction / impl"| QBRI(["Bridge"])
    CAT_ST -->|"add behavior at runtime"| QDEC(["Decorator"])
    CAT_ST -->|"simplify a subsystem"| QFAC(["Facade"])
    CAT_ST -->|"tree, part-whole"| QCMP(["Composite"])
    CAT_ST -->|"control access"| QPRX(["Proxy"])
    CAT_ST -->|"share many small objects"| QFLY(["Flyweight"])

    CAT_BE -->|"swap algorithms at runtime"| QSTR(["Strategy"])
    CAT_BE -->|"behavior changes with state"| QSTA(["State"])
    CAT_BE -->|"skeleton, variable steps"| QTM(["Template Method"])
    CAT_BE -->|"undo-able operations"| QCMD(["Command"])
    CAT_BE -->|"event notifications"| QOBS(["Observer"])
    CAT_BE -->|"many-to-many communication"| QMED(["Mediator"])
    CAT_BE -->|"pass request along a chain"| QCOR(["Chain of<br/>Responsibility"])
    CAT_BE -->|"traverse a collection"| QITR(["Iterator"])
    CAT_BE -->|"save / restore state"| QMEM(["Memento"])
    CAT_BE -->|"add ops without<br/>modifying classes"| QVIS(["Visitor"])

    class ROOT io
    class CAT_CR,QFM,QAF,QBLD,QPRO,QSIN base
    class CAT_ST,QADP,QBRI,QDEC,QFAC,QCMP,QPRX,QFLY frozen
    class CAT_BE,QSTR,QSTA,QTM,QCMD,QOBS,QMED,QCOR,QITR,QMEM,QVIS mathOp
```

*Triage first by job type (colors matching the relationship map above), then follow the branch whose condition matches your situation to the recommended pattern. The checklists below spell out the same 22 rules in prose — use whichever you scan faster.*

### "I need to CREATE objects"
- Single object, type varies by subclass -> **Factory Method**
- Families of related objects -> **Abstract Factory**
- Complex object with many optional parts -> **Builder**
- Copy an existing object -> **Prototype**
- Only one instance ever -> **Singleton**

### "I need to STRUCTURE classes/objects"
- Make incompatible interfaces work together -> **Adapter**
- Separate abstraction from implementation -> **Bridge**
- Add behavior dynamically at runtime -> **Decorator**
- Simplify a complex subsystem -> **Facade**
- Tree structure (part-whole) -> **Composite**
- Control access to an object -> **Proxy**
- Share many small objects -> **Flyweight**

### "I need to define BEHAVIOR / communication"
- Swap algorithms at runtime -> **Strategy**
- Behavior changes with object state -> **State**
- Skeleton algorithm with variable steps -> **Template Method**
- Undo-able operations -> **Command**
- Event notifications -> **Observer**
- Decouple many-to-many communication -> **Mediator**
- Pass request along a chain -> **Chain of Responsibility**
- Traverse a collection -> **Iterator**
- Save and restore state -> **Memento**
- Add operations to class hierarchy without modifying it -> **Visitor**

---

## Pattern Frequency in Real Systems

| Frequency | Patterns |
|-----------|----------|
| Very Common | Singleton, Factory Method, Observer, Strategy, Decorator, Facade |
| Common | Builder, Adapter, Proxy, Command, Iterator, Template Method |
| Moderate | Abstract Factory, Composite, State, Chain of Responsibility, Mediator |
| Less Common | Bridge, Flyweight, Prototype, Memento, Visitor, Interpreter |

---

## Files in This Directory

- [README.md](README.md) — This file (master matrix + quick guide)
- [Strategy_vs_State.md](Strategy_vs_State.md)
- [Factory_vs_AbstractFactory_vs_Builder.md](Factory_vs_AbstractFactory_vs_Builder.md)
- [Adapter_vs_Bridge_vs_Facade.md](Adapter_vs_Bridge_vs_Facade.md)
- [Decorator_vs_Proxy.md](Decorator_vs_Proxy.md)
- [Observer_vs_Mediator.md](Observer_vs_Mediator.md)
- [Command_vs_Strategy.md](Command_vs_Strategy.md)
- [Template_vs_Strategy.md](Template_vs_Strategy.md)
- [Composite_vs_Decorator.md](Composite_vs_Decorator.md)
- [ChainOfResponsibility_vs_Command.md](ChainOfResponsibility_vs_Command.md)
- [DecisionFlowchart.md](DecisionFlowchart.md)
- [InterviewQuestions.md](InterviewQuestions.md)
