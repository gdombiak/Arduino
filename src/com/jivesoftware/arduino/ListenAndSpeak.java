package com.jivesoftware.arduino;

import com.jivesoftware.arduino.jive.CreateDirectMessage;
import com.jivesoftware.arduino.jive.LikeContentCommand;
import com.jivesoftware.arduino.jive.ReplyCommand;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: ed
 * Date: 11/4/13
 * Time: 10:11 PM
 */
public class ListenAndSpeak {

    private static long STABLE_WAIT_MS = 2500L;
//    private static long SILENCE_TIMEOUT_MS = 12000L;

    private static String START_DICT = "tell application \"System Events\" to keystroke \"d\" using {shift down, option down, command down}";

    private final JTextField textArea;

    private final AtomicReference<State> state = new AtomicReference<State>(State.STOPPED);
    private final JFrame frame;
    private final Collection<TextListener> listeners = new LinkedList<TextListener>();
    private final Deque<Say> thingsToSay = new LinkedList<Say>();

    private ArduinoConnection arduinoConnection;

    private Thread monitorDictation;

    private LikeContentCommand likeContentCommand;
    private ReplyCommand replyCommand;
    private String lastHeadline;
    private String lastDetail;

    public ListenAndSpeak(ArduinoConnection arduinoConnection) throws InterruptedException {
        this.arduinoConnection = arduinoConnection;
        monitorDictation = new Thread(new Runnable() {

            private State was = State.STOPPED;
            private boolean checkedForTerminal = false;
            private String previousValue = "";
            private long lastChange = 0L;

            @Override
            public void run() {
                while (state.get() != State.STOPPED) {
                    processState();
                }
            }

            private void processState() {
                State state = ListenAndSpeak.this.state.get();
                boolean newState = state != was;
                switch (state) {
                    case IDLE:
                        if (was == State.LISTENING) {
                            stopDictation();
                        }
                        previousValue = "";
                        synchronized (ListenAndSpeak.this.state) {
                            try {
                                ListenAndSpeak.this.state.wait(2500L);
                            } catch (InterruptedException e) {
                                Thread.interrupted();
                            }
                        }
                        break;
                    case SPEAKING:
                        if (was == State.LISTENING) {
                            stopDictation();
                        }
                        Say say = null;
                        synchronized (thingsToSay) {
                            if (!thingsToSay.isEmpty()) {
                                say = thingsToSay.pollFirst();
                            }
                        }
                        if (say != null) {
                            speakImpl(say);
                        }
                        try {
                            Thread.sleep(250L);
                        } catch (InterruptedException e) {
                            Thread.interrupted();
                        }
                        boolean empty;
                        synchronized (thingsToSay) {
                            empty = thingsToSay.isEmpty();
                        }
                        if (empty) {
                            setState(State.IDLE);
                            startDictation();
                        }
                        break;
                    case LISTENING:
                        if (newState) {
                            previousValue = textArea.getText();
                            lastChange = System.currentTimeMillis();
                            checkedForTerminal = false;
                        }
                        try {
                            Thread.sleep(100L);
                        } catch (InterruptedException e) {
                            Thread.interrupted();
                        }
                        String v = textArea.getText().trim();
                        if (previousValue.equals(v)) {
                            long elapsed = System.currentTimeMillis() - lastChange;
                            if (elapsed >= STABLE_WAIT_MS && !checkedForTerminal) {
                                clearTextBox();
                                previousValue = "";
                                checkedForTerminal = true;
                                try {
                                    notifyListeners(v);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
//                            } else if (elapsed > SILENCE_TIMEOUT_MS) {
//                                stopDictation();
                            }
                        } else {
                            previousValue = v;
                            lastChange = System.currentTimeMillis();
                            checkedForTerminal = false;
                        }
                        break;
                }
                was = state;
            }
        }, "Monitor Dictation");
        frame = new JFrame();


//        frame.setUndecorated(true);
        frame.getRootPane().setWindowDecorationStyle(JRootPane.FRAME);
        // Set the style of the frame
        textArea = new JTextField("", 100);

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                monitorDictation.interrupt();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                monitorDictation.interrupt();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                monitorDictation.interrupt();
            }
        });

        frame.add(textArea);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setLayout(new FlowLayout());
        frame.pack();
        frame.setLocationRelativeTo(null);
        textArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (state.compareAndSet(State.LISTENING, State.IDLE)) {
                    startDictation();
                }
            }
        });
        frame.setVisible(true);

        addListener(new TextListener() {
            @Override
            public void userSaid(String text) {
                System.out.println("I heard: " + text);
                text = text.toLowerCase();
                if (CreateDirectMessage.canHandle(text)) {
                    // Execute "send direct message" command
                    new CreateDirectMessage(ListenAndSpeak.this).execute(text);
                } else if (text.contains("like")) {
                    if (likeContentCommand != null) {
                        likeContentCommand.execute();
                    } else {
                        speak(getVoice(), "There is nothing to like");
                    }
                } else if (text.contains("reply")) {
                    if (replyCommand != null) {
                        replyCommand.execute(text);
                    } else {
                        speak(getVoice(), "There is nothing to reply to");
                    }
                } else if (text.contains("repeat")) {
                    // Repeat last headline we heard from the stream
                    if (lastHeadline != null) {
                        speak(getVoice(), lastHeadline);
                    } else {
                        speak(getVoice(), "There is nothing new");
                    }
                } else if (text.contains("read")) {
                    // Read body of last news we heard from the stream
                    if (lastDetail != null) {
                        speak(getVoice(), lastDetail);
                    } else {
                        speak(getVoice(), "There is nothing new");
                    }
                } else if (text.contains("dance")) {
                    // Read body of last news we heard from the stream
                    dance();
                } else {
                    System.out.println("Unrecognized text: " + text);
                    // TODO Let user know that text is not recognized
                }
//                speak(Voice.VICKI, text);
            }
        });
    }

    private void dance() {
        Random rand = new Random();
        int posLow;
        int posHigh;
        try {
            posLow = rand.nextInt(40);
            arduinoConnection.send("op(5,"+ posLow+ ")");
            sleepThisLong(900000000);
            posHigh = rand.nextInt(40) + 50;
            arduinoConnection.send("op(5,"+ posHigh+ ")");

            posLow = rand.nextInt(40);
            arduinoConnection.send("op(5,"+ posLow+ ")");
            sleepThisLong(900000000);
            posHigh = rand.nextInt(40) + 50;
            arduinoConnection.send("op(5,"+ posHigh+ ")");

            posLow = rand.nextInt(40);
            arduinoConnection.send("op(5,"+ posLow+ ")");
            sleepThisLong(900000000);
            posHigh = rand.nextInt(40) + 50;
            arduinoConnection.send("op(5,"+ posHigh+ ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sleepThisLong(int howlong) {
        for (int i=0; i < howlong;i++) {
            // Do nothing
        }
    }

    public void start() throws InterruptedException {
        setState(State.IDLE);
        monitorDictation.start();
    }

    private void startDictation() {
        if (state.get() == State.IDLE) {
            if (textArea.isFocusOwner()) {
                runScript(START_DICT);
                setState(State.LISTENING);
            } else {
                final FocusAdapter listener = new FocusAdapter() {
                    @Override
                    public synchronized void focusGained(FocusEvent e) {
                        textArea.removeFocusListener(this);
                        notifyAll();
                    }
                };
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (listener) {
                    frame.requestFocus();
                    textArea.addFocusListener(listener);
                    textArea.requestFocus();
                    try {
                        listener.wait(2500L);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                    if (state.get() == State.IDLE) {
                        runScript(START_DICT);
                        setState(State.LISTENING);
                    }
                }
            }
        }
    }

    public void stopDictation() {
        runScript(START_DICT);
//        runCommand("/usr/bin/say", ".");
//        try {
//            Thread.sleep(250L);
//        } catch (InterruptedException e) {
//            Thread.interrupted();
//        }
    }

    private void runScript(String script) {
        clearTextBox();
        String[] lines = script.split("\n");
        String[] args = new String[lines.length * 2 + 1];
        args[0] = "/usr/bin/osascript";
        for (int i = 0, j = 1, l = lines.length; i < l; i++, j += 2) {
            args[j] = "-e";
            args[j + 1] = lines[i];
        }
        runCommand(args);
    }

    private void runCommand(String... args) {
        System.out.println("Running script: " + Arrays.asList(args));
        if (System.getProperty("noCommand") == null) {
            // Execute the command since we are on Mac OS
            Process process;
            try {
                Runtime runtime = Runtime.getRuntime();
                process = runtime.exec(args);
                while (true) {
                    try {
                        process.waitFor();
                        break;
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            System.out.println("Script finished: exit code = " + process.exitValue());
        }
    }

    private void clearTextBox() {
        boolean retry;
        do {
            retry = false;
            try {
                textArea.setText("");
            } catch (ThreadDeath td) {
                throw td;
            } catch (Error e) {
                if ("Interrupted attempt to aquire write lock".equals(e.getMessage())) {
                    retry = true;
                } else {
                    throw e;
                }
            }
        } while (retry);
    }

    private void notifyListeners(String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        Collection<TextListener> clone;
        synchronized (listeners) {
            clone = new ArrayList<TextListener>(listeners);
        }
        for (TextListener listener : clone) {
            try {
                listener.userSaid(text);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void addListener(TextListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(TextListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void speak(Voice voice, String text) {
        if (text == null || voice == null || text.length() == 0) {
            return;
        }
        synchronized (thingsToSay) {
            thingsToSay.add(new Say(voice, text));
        }
        setState(State.SPEAKING);
    }

    public void remember(LikeContentCommand likeContentCommand, ReplyCommand replyCommand, String lastHeadline, String lastDetail) {
        this.likeContentCommand = likeContentCommand;
        this.replyCommand = replyCommand;
        this.lastHeadline = lastHeadline;
        this.lastDetail = lastDetail;
    }

    private void speakImpl(Say say) {
        if (say == null) {
            return;
        }
        runCommand("/usr/bin/say", "-v", say.voice.toString(), "-r", "160", say.text);
    }

    public void setState(State state) {
        State previouState = this.state.getAndSet(state);
        if (previouState != state) {
            System.out.println("Changing state: " + previouState + " -> " + state);
            synchronized (this.state) {
                this.state.notify();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ArduinoConnection arduinoConnection = new ArduinoConnection();
        ListenAndSpeak listenAndSpeak = new ListenAndSpeak(arduinoConnection);
        listenAndSpeak.start();
    }

    private enum State {
        IDLE, LISTENING, SPEAKING, STOPPED
    }

    public enum Voice {
        VICKI, TOM, BRUCE, DIEGO
    }

    private static class Say {
        final Voice voice;
        final String text;

        private Say(Voice voice, String text) {
            this.voice = voice;
            this.text = text;
        }
    }

    public interface TextListener {
        void userSaid(String text);
    }

    private static boolean isEd() {
        return "ed".equals(System.getProperty("username"));
    }

    protected Voice getVoice() {
        return isEd() ? ListenAndSpeak.Voice.BRUCE : ListenAndSpeak.Voice.TOM;
    }

}
