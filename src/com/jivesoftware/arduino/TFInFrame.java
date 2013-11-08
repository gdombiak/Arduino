package com.jivesoftware.arduino;

import com.jivesoftware.arduino.jive.CreateDirectMessage;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Open a dialog with a single text field. The dialog will close after text was written.
 */
public class TFInFrame extends JFrame {

    public static void main(String[] args) {
        TFInFrame frame = new TFInFrame();
        frame.captureText();
    }

    public TFInFrame() {
        super("JTextField in a JFrame");
    }

    protected void captureText() {
        // Use the dafault metal styled titlebar
        setUndecorated(true);
        getRootPane().setWindowDecorationStyle(JRootPane.FRAME);
        // Set the style of the frame
        final JTextField textField = new JTextField("", 100);

        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                // Get Text and close window
                String enteredText = textField.getText();
                TFInFrame.this.dispose();
                if (CreateDirectMessage.canHandle(enteredText)) {
                    // Execute "send direct message" command
                    new CreateDirectMessage().execute(enteredText);
                } else {
                    System.out.println("Unrecognized text: " + enteredText);
                    // TODO Let user know that text is not recognized
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                // Do nothing
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Do nothing
            }
        });

        add(textField);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new FlowLayout());
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
}