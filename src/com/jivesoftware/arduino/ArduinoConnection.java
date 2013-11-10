package com.jivesoftware.arduino;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listen to codes from Arduino and executes associated logic to each code.
 * List of supported codes:
 * <ul>
 *     <li><b>1</b> - Listen to command</li>
 * </ul>
 */
public class ArduinoConnection implements SerialPortEventListener {
    SerialPort serialPort;
    /** The port we're normally going to use. */
    private static final String PORT_NAMES[] = {
            "/dev/tty.usbserial-A9007UX1", // Mac OS X
            "/dev/cu.usbmodem1421", // Mac OS X
            "/dev/tty.usbmodem1421", // Mac OS X
            "/dev/ttyUSB0", // Linux
            "COM3", // Windows
    };

    private static final Pattern MATCH_SWITCH = Pattern.compile("sw\\((belly|tongue|light)\\)=(0|1)");

    /**
     * A BufferedReader which will be fed by a InputStreamReader
     * converting the bytes into characters
     * making the displayed results codepage independent
     */
    private BufferedReader input;
    /** The output stream to the port */
    private PrintWriter output;
    /** Milliseconds to block while waiting for port open */
    private static final int TIME_OUT = 2000;
    /** Default bits per second for COM port. */
    private static final int DATA_RATE = 115200;

    private final Collection<FurbyButtonListener> listeners = new LinkedList<FurbyButtonListener>();

    public void initialize() {
        CommPortIdentifier portId = null;
        Enumeration portEnum = CommPortIdentifier.getPortIdentifiers();

        //First, Find an instance of serial port as set in PORT_NAMES.
        while (portEnum.hasMoreElements()) {
            CommPortIdentifier currPortId = (CommPortIdentifier) portEnum.nextElement();
            for (String portName : PORT_NAMES) {
                if (currPortId.getName().equals(portName)) {
                    portId = currPortId;
                    break;
                }
            }
        }
        if (portId == null) {
            System.out.println("Could not find COM port. Reading from STDIN");
            Thread t = new Thread(new Runnable() {

                final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

                @SuppressWarnings("InfiniteLoopStatement")
                @Override
                public void run() {
                    while (true) {
                        String line = getLine();
                        if (line.length() > 0) {
                            handleReadLine(line);
                        }
                    }
                }

                private String getLine() {
                    try {
                        return in.readLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return "";
                }
            }, "Read from STDIN");
            t.setDaemon(true);
            t.start();
            return;
        }

        try {
            // open serial port, and use class name for the appName.
            serialPort = (SerialPort) portId.open(this.getClass().getName(),
                    TIME_OUT);

            // set port parameters
            serialPort.setSerialPortParams(DATA_RATE,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);

            // open the streams
            input = new BufferedReader(new InputStreamReader(serialPort.getInputStream(), Charset.forName("US-ASCII")));
            output = new PrintWriter(new OutputStreamWriter(serialPort.getOutputStream(), Charset.forName("US-ASCII")));

            // add event listeners
            serialPort.addEventListener(this);
            serialPort.notifyOnDataAvailable(true);
        } catch (Exception e) {
            System.err.println(e.toString());
        }
    }

    /**
     * This should be called when you stop using the port.
     * This will prevent port locking on platforms like Linux.
     */
    public synchronized void close() {
        if (serialPort != null) {
            serialPort.removeEventListener();
            serialPort.close();
        }
    }
    public synchronized void send(String command) {
        if (output == null) {
            System.out.println("Arduino not detected. Failed to send command: " + command);
            return;
        }
        System.out.println("Sending command to Arduiuno: " + command);
        output.write(command);
        output.write('\n');
        output.flush();
    }

    public void addListener(FurbyButtonListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(FurbyButtonListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Handle an event on the serial port. Read the data and print it.
     */
    public synchronized void serialEvent(SerialPortEvent oEvent) {
        if (oEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            try {
                String inputLine=input.readLine();
                System.out.println("Furby said: " + inputLine);
                handleReadLine(inputLine);
            } catch (Exception e) {
                System.err.println(e.toString());
            }
        }
        // Ignore all the other eventTypes, but you should consider the other ones.
    }

    private void handleReadLine(String inputLine) {
        Matcher matcher = MATCH_SWITCH.matcher(inputLine);
        if (matcher.find()) {
            String name = matcher.group(1);
            boolean down = "1".equals(matcher.group(2));
            FurbyButtonListener.Button button = FurbyButtonListener.Button.valueOf(name.toUpperCase());
            for (FurbyButtonListener listener : listeners) {
                if (!listener.filter(button)) {
                    continue;
                }
                try {
                    if (down) {
                        listener.buttonDown(button);
                    } else {
                        listener.buttonUp(button);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ArduinoConnection main = new ArduinoConnection();
        main.initialize();
        Thread t=new Thread() {
            public void run() {
                //the following line will keep this app alive for 1000 seconds,
                //waiting for events to occur and responding to them (printing incoming messages to console).
                try {Thread.sleep(1000000);} catch (InterruptedException ie) {}
            }
        };
        t.start();
        System.out.println("Started");
    }

}
