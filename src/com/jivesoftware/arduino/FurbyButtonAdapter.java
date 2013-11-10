package com.jivesoftware.arduino;

import java.util.EnumSet;

/**
 * User: ed
 * Date: 11/10/13
 * Time: 9:57 AM
 */
public class FurbyButtonAdapter implements FurbyButtonListener {

    private final EnumSet<Button> buttons;

    public FurbyButtonAdapter(Button first, Button... more) {
        this.buttons = EnumSet.of(first, more);
    }

    @Override
    public boolean filter(Button button) {
        return buttons.contains(button);
    }

    @Override
    public void buttonDown(Button button) {
        // no-op
    }

    @Override
    public void buttonUp(Button button) {
        // no-op
    }
}
