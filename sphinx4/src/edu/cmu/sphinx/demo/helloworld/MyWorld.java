package edu.cmu.sphinx.demo.helloworld;

import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.ConfigurationManager;

import java.net.URL;

/**
 * Created with IntelliJ IDEA.
 * User: gaston
 * Date: 11/1/13
 * Time: 1:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class MyWorld {

    public static void main(String[] args) throws Exception {
        URL digitsConfig = new URL("file:./digits.xml");
        ConfigurationManager cm = new ConfigurationManager(digitsConfig);
        Recognizer sphinxDigitsRecognizer = (Recognizer) cm.lookup("digitsRecognizer");
        boolean done = false;
        Result result;
        sphinxDigitsRecognizer.allocate();
        // echo spoken digits, quit when 'nine' is spoken
        while (!done) {
            result = sphinxDigitsRecognizer.recognize();
            System.out.println("Result: " + result);
            done = result.toString().equals("nine");
        }
        sphinxDigitsRecognizer.deallocate();

    }
}
