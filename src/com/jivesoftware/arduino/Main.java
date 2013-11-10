package com.jivesoftware.arduino;

import com.jivesoftware.arduino.jive.GetCustomStream;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * System properties:
 * <ul>
 *     <li>username - <b>ed</b> or null means gato</li>
 *     <li>password - <b>must be defined</b> or kaboom</li>
 *     <li>noCommand - <b>if you are not running on Mac OS</li>
 * </ul>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        AtomicBoolean enabled = new AtomicBoolean(true);
        ArduinoConnection arduinoConnection = new ArduinoConnection();
        final ListenAndSpeak listenAndSpeak = new ListenAndSpeak(arduinoConnection);
        GetCustomStream streamCommand = new GetCustomStream(listenAndSpeak);
        arduinoConnection.initialize();
        arduinoConnection.addListener(new FurbyButtonAdapter(FurbyButtonListener.Button.LIGHT) {
            @Override
            public void buttonDown(Button button) {
                listenAndSpeak.stop();
            }

            @Override
            public void buttonUp(Button button) {
                listenAndSpeak.start();
            }
        });
        listenAndSpeak.start();
        streamCommand.start();
    }
}
