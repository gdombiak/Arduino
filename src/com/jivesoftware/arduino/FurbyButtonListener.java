package com.jivesoftware.arduino;

/**
* Created with IntelliJ IDEA.
* User: ed
* Date: 11/10/13
* Time: 9:41 AM
* To change this template use File | Settings | File Templates.
*/
public interface FurbyButtonListener {

    enum Button {
        BELLY, TONGUE, LIGHT
    }

    boolean filter(Button button);

    void buttonDown(Button button);

    void buttonUp(Button button);
}
