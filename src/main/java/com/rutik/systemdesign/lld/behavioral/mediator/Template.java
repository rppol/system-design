package com.rutik.systemdesign.lld.behavioral.mediator; /**
 * Mediator Pattern - Template
 *
 * Intent: Define an object that encapsulates how a set of objects interact.
 * Mediator promotes loose coupling by keeping objects from referring to each
 * other explicitly, and it lets you vary their interaction independently.
 *
 * Key participants:
 *  - Mediator        : Defines the interface for communicating with Colleague objects.
 *  - ConcreteMediator: Implements cooperative behaviour by coordinating Colleague objects;
 *                      knows and maintains its colleagues.
 *  - Colleague       : Each Colleague knows its Mediator; communicates with other
 *                      colleagues through the mediator instead of directly.
 *
 * When to use:
 *  - A set of objects communicate in well-defined but complex ways.
 *  - Reusing an object is difficult because it refers to and communicates with many others.
 *  - Behaviour distributed among several classes should be customisable without lots of subclassing.
 */

// ─── Mediator Interface ───────────────────────────────────────────────────────

/**
 * The Mediator declares the notify method colleagues call when something happens.
 */
interface Mediator {
    /**
     * Receives an event from the originating colleague and decides what to do.
     *
     * @param sender the colleague that raised the event
     * @param event  a string describing what happened (e.g. "click", "changed")
     */
    void notify(Colleague sender, String event);
}

// ─── Colleague (abstract base) ────────────────────────────────────────────────

/**
 * Every UI component / participant holds a back-reference to the Mediator.
 * It raises events via the mediator rather than calling other components directly.
 */
abstract class Colleague {
    protected Mediator mediator;

    public Colleague(Mediator mediator) {
        this.mediator = mediator;
    }

    /** Replace mediator at runtime (e.g. when wiring dialogs). */
    public void setMediator(Mediator mediator) {
        this.mediator = mediator;
    }
}

// ─── Concrete Colleagues ──────────────────────────────────────────────────────

class Button extends Colleague {
    private final String label;

    public Button(String label, Mediator mediator) {
        super(mediator);
        this.label = label;
    }

    /** Simulates a user click. */
    public void click() {
        System.out.println("Button[" + label + "] clicked.");
        mediator.notify(this, "click:" + label);
    }
}

class TextBox extends Colleague {
    private String text = "";

    public TextBox(Mediator mediator) {
        super(mediator);
    }

    public void setText(String text) {
        this.text = text;
        System.out.println("TextBox text changed to: \"" + text + "\"");
        mediator.notify(this, "textChanged");
    }

    public String getText() { return text; }

    public void enable()  { System.out.println("TextBox enabled."); }
    public void disable() { System.out.println("TextBox disabled."); }
}

class CheckBox extends Colleague {
    private boolean checked = false;
    private final String label;

    public CheckBox(String label, Mediator mediator) {
        super(mediator);
        this.label = label;
    }

    public void toggle() {
        checked = !checked;
        System.out.println("CheckBox[" + label + "] " + (checked ? "checked." : "unchecked."));
        mediator.notify(this, "toggle:" + label + ":" + checked);
    }

    public boolean isChecked() { return checked; }
}

// ─── ConcreteMediator ─────────────────────────────────────────────────────────

/**
 * Coordinates all the form components. All inter-component logic lives here
 * so the colleagues remain simple and reusable.
 */
class FormMediator implements Mediator {
    // The mediator knows all colleagues
    private Button   submitButton;
    private Button   cancelButton;
    private TextBox  nameField;
    private CheckBox agreeCheckBox;

    // ── Wiring ───────────────────────────────────────────────────────────────
    public void setSubmitButton(Button b)      { this.submitButton   = b; }
    public void setCancelButton(Button b)      { this.cancelButton   = b; }
    public void setNameField(TextBox tb)       { this.nameField      = tb; }
    public void setAgreeCheckBox(CheckBox cb)  { this.agreeCheckBox  = cb; }

    // ── Coordination Logic ───────────────────────────────────────────────────
    @Override
    public void notify(Colleague sender, String event) {
        System.out.println("  [Mediator] Event: " + event);

        if (event.equals("textChanged")) {
            // Enable submit only if name is non-empty AND checkbox is ticked
            evaluateSubmitState();
        } else if (event.startsWith("toggle:agree")) {
            evaluateSubmitState();
        } else if (event.equals("click:Submit")) {
            System.out.println("  [Mediator] Submitting form for: \"" + nameField.getText() + "\"");
        } else if (event.equals("click:Cancel")) {
            System.out.println("  [Mediator] Cancelling. Resetting form.");
            nameField.setText("");
            nameField.enable();
        }
    }

    private void evaluateSubmitState() {
        boolean ready = !nameField.getText().isBlank() && agreeCheckBox.isChecked();
        if (ready) {
            System.out.println("  [Mediator] Form valid — Submit enabled.");
        } else {
            System.out.println("  [Mediator] Form incomplete — Submit disabled.");
        }
    }
}

// ─── Client / Demo ────────────────────────────────────────────────────────────

public class Template {

    public static void main(String[] args) {
        System.out.println("=== Mediator Pattern Template ===\n");

        FormMediator mediator = new FormMediator();

        // Create colleagues and point them at the mediator
        Button   submitBtn  = new Button("Submit", mediator);
        Button   cancelBtn  = new Button("Cancel", mediator);
        TextBox  nameField  = new TextBox(mediator);
        CheckBox agreeBox   = new CheckBox("agree", mediator);

        // Wire the mediator to its colleagues
        mediator.setSubmitButton(submitBtn);
        mediator.setCancelButton(cancelBtn);
        mediator.setNameField(nameField);
        mediator.setAgreeCheckBox(agreeBox);

        System.out.println("--- User types name ---");
        nameField.setText("Alice");

        System.out.println("\n--- User checks agreement ---");
        agreeBox.toggle();

        System.out.println("\n--- User submits ---");
        submitBtn.click();

        System.out.println("\n--- User cancels ---");
        cancelBtn.click();

        System.out.println("\n--- User only checks box (no name) ---");
        agreeBox.toggle(); // check it again after cancel reset it
    }
}
