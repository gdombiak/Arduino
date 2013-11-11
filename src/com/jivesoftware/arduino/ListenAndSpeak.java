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
import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: ed
 * Date: 11/4/13
 * Time: 10:11 PM
 */
public class ListenAndSpeak {

    private static long STABLE_WAIT_MS = 2500L;
//    private static long SILENCE_TIMEOUT_MS = 12000L;

    private static String START_DICT = "tell application \"System Events\" to keystroke \"d\" using {shift down, option down, command down}";

    private static final Pattern MATCH_SWITCH = Pattern.compile("sw\\((belly|tongue|light)\\)=(0|1)");

    private final JTextField textArea;

    private final AtomicReference<State> state = new AtomicReference<State>(State.STOPPED);
    private final JFrame frame;
    private final Collection<TextListener> listeners = new LinkedList<TextListener>();
    private final Deque<Say> thingsToSay = new LinkedList<Say>();

    private ArduinoConnection arduinoConnection;
    private AtomicBoolean enabled;

    private Thread monitorDictation;

    private LikeContentCommand likeContentCommand;
    private ReplyCommand replyCommand;
    private String lastHeadline;
    private String lastDetail;

    public ListenAndSpeak(ArduinoConnection arduinoConnection, AtomicBoolean enabled) throws InterruptedException {
        this.arduinoConnection = arduinoConnection;
        this.enabled = enabled;
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
                            if (say.runAfter != null) {
                                try {
                                    say.runAfter.run();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        try {
                            Thread.sleep(250L);
                        } catch (InterruptedException e) {
                            Thread.interrupted();
                        }
                        if (!ListenAndSpeak.this.enabled.get()) {
                            setState(State.IDLE);
                            break;
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
                            }
                        } else {
                            previousValue = v;
                            lastChange = System.currentTimeMillis();
                            checkedForTerminal = false;
                        }
                        if (!ListenAndSpeak.this.enabled.get()) {
                            setState(State.IDLE);
                            break;
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
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
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
                String textLowerCase = text.toLowerCase();
                if (CreateDirectMessage.canHandle(textLowerCase)) {
                    // Execute "send direct message" command
                    new CreateDirectMessage(ListenAndSpeak.this).execute(text);
                } else if (textLowerCase.contains("dance")) {
                    // Read body of last news we heard from the stream
                    if (textLowerCase.contains("travolta")) {
                        play(AudioClip.TRAVOLTA);
                    } else {
                        play(AudioClip.DANCE);
                    }
                } else if (textLowerCase.contains("like")) {
                    if (likeContentCommand != null) {
                        likeContentCommand.execute();
                    } else {
                        speak(getVoice(), "There is nothing to like");
                    }
                } else if (textLowerCase.contains("reply")) {
                    if (replyCommand != null) {
                        replyCommand.execute(text);
                    } else {
                        speak(getVoice(), "There is nothing to reply to");
                    }
                } else if (textLowerCase.contains("repeat")) {
                    // Repeat last headline we heard from the stream
                    if (lastHeadline != null) {
                        speak(getVoice(), lastHeadline);
                    } else {
                        speak(getVoice(), "There is nothing new");
                    }
                } else if (textLowerCase.contains("read")) {
                    // Read body of last news we heard from the stream
                    if (lastDetail != null) {
                        speak(getVoice(), lastDetail);
                    } else {
                        speak(getVoice(), "There is nothing new");
                    }
                } else {
                    System.out.println("Unrecognized text: " + text);
                    // TODO Let user know that text is not recognized
                }
//                speak(Voice.VICKI, text);
            }
        });
        arduinoConnection.addListener(new FurbyButtonAdapter(FurbyButtonListener.Button.BELLY) {

            private long lastTickle = 0L;

            @Override
            public void buttonDown(Button button) {
                long now = System.currentTimeMillis();
                if (now - lastTickle > 15000L) {
                    lastTickle = now;
                    play(ListenAndSpeak.AudioClip.LAUGH);
                }
            }
        });
    }

    private void dance() {
        play(AudioClip.DANCE);
//        if (!enabled.get()) {
//            return;
//        }
//        new Thread(new Runnable() {
//            public void run() {
//                arduinoConnection.send("op(6,0)");
//
//                Random rand = new Random();
//                int posLow;
//                int posHigh;
//                try {
//                    posLow = rand.nextInt(40);
//                    arduinoConnection.send("op(5,"+ posLow+ ")");
//                    Thread.sleep(350);
//                    posHigh = rand.nextInt(40) + 50;
//                    arduinoConnection.send("op(5,"+ posHigh+ ")");
//
//                    posLow = rand.nextInt(40);
//                    arduinoConnection.send("op(5,"+ posLow+ ")");
//                    Thread.sleep(350);
//                    posHigh = rand.nextInt(40) + 50;
//                    arduinoConnection.send("op(5,"+ posHigh+ ")");
//
//                    posLow = rand.nextInt(40);
//                    arduinoConnection.send("op(5,"+ posLow+ ")");
//                    Thread.sleep(350);
//                    posHigh = rand.nextInt(40) + 50;
//                    arduinoConnection.send("op(5,"+ posHigh+ ")");
//
//                    Thread.sleep(350);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                } finally {
//                    arduinoConnection.send("op(6,1)");
//                }
//            }
//        }, "Dance").start();
    }

    public void start() {
        setState(State.IDLE);
        if (!monitorDictation.isAlive()) {
            monitorDictation.start();
            startDictation();
        } else {
            enabled.set(true);
            play(AudioClip.WAKING_UP);
            setState(State.SPEAKING);
        }
    }

    public void stop() {
        AudioClip audioClip = AudioClip.SLEEPY_TIME;
        File audioFile = audioClip.getFile();
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            enabled.set(false);
            setState(State.IDLE);
            return;
        }
        synchronized (thingsToSay) {
            thingsToSay.clear();
            thingsToSay.add(new Say(null, audioFile.getAbsolutePath(), new Runnable() {
                @Override
                public void run() {
                    enabled.set(false);
                    setState(State.IDLE);
                }
            }));
        }
        setState(State.SPEAKING);
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

    public void play(AudioClip audioClip) {
        File audioFile = audioClip == null ? null : audioClip.getFile();
        if (!enabled.get() || audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            return;
        }
        synchronized (thingsToSay) {
            thingsToSay.add(new Say(null, audioFile.getAbsolutePath()));
        }
        setState(State.SPEAKING);
    }

    public void speak(Voice voice, String text) {
        if (!enabled.get() || text == null || voice == null || text.length() == 0) {
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
        if (say.voice != null) {
            runCommand("/usr/bin/say", "-v", say.voice.toString(), "-r", "160", say.text);
        } else {
            runCommand("/usr/bin/afplay", say.text);
        }
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
        ListenAndSpeak listenAndSpeak = new ListenAndSpeak(arduinoConnection, new AtomicBoolean(true));
        listenAndSpeak.start();
    }

    private enum State {
        IDLE, LISTENING, SPEAKING, STOPPED
    }

    public enum Voice {
        VICKI, TOM, BRUCE, DIEGO
    }

    public enum AudioClip {
        WAKING_UP("cock-a-doodle-doo.aif"),
        DANCE("dance-boogie.aif"),
        TRAVOLTA("dance-like-travolta.aif"),
        LAUGH("he-he-he-tickle.aif"),
        SLEEPY_TIME("yawn-snore.aif");

        private final File file;

        AudioClip(String name) {
            URL resource = getClass().getClassLoader().getResource(name);
            String fileName = resource == null ? null : resource.getFile();
            file = fileName == null ? null : new File(fileName);
        }

        public File getFile() {
            return file;
        }
    }

    private static class Say {
        final Voice voice;
        final String text;
        final Runnable runAfter;

        private Say(Voice voice, String text) {
            this(voice, text, null);
        }

        private Say(Voice voice, String text, Runnable runAfter) {
            this.voice = voice;
            this.text = text;
            this.runAfter = runAfter;
        }
    }

    private static boolean isEd() {
        return "ed".equals(System.getProperty("username"));
    }

    protected Voice getVoice() {
        return isEd() ? ListenAndSpeak.Voice.BRUCE : ListenAndSpeak.Voice.TOM;
    }

}
