package com.jivesoftware.arduino;

import com.jivesoftware.arduino.jive.GetCustomStream;

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
        ArduinoConnection arduinoConnection = new ArduinoConnection();
        ListenAndSpeak listenAndSpeak = new ListenAndSpeak(arduinoConnection);
        GetCustomStream streamCommand = new GetCustomStream(listenAndSpeak);
        arduinoConnection.initialize();
        listenAndSpeak.start();
        streamCommand.start();
    }
}
