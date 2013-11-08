package com.jivesoftware.arduino.jive;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;


import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: gaston
 * Date: 11/4/13
 * Time: 11:45 AM
 * To change this template use File | Settings | File Templates.
 */
public class CreateDirectMessage extends JiveCommand {

    private static final String regex = "(send a message to|tell) ([\\D\\d]*) (saying|that) ([\\D\\d]*)";
    private static final Pattern pattern = Pattern.compile(regex);

    private static final String service = api + "/dms";

    /**
     * Returns true if the provided text can be handled by this class.
     *
     * @param rawText the raw text that was listened using some magic!
     * @return true if the provided text can be handled by this class.
     */
    public static boolean canHandle(String rawText) {
        return pattern.matcher(rawText).find();
    }

    public void execute(String rawText) {
        Matcher matcher = pattern.matcher(rawText);
        if (matcher.find()) {
            String user = matcher.group(2);
            String message = matcher.group(4);
            // TODO Lookup username for requested user
            String username = getUsername(user);
            String json = "{\"type\" : \"dm\",\n" +
                    "  \"content\" : {\n" +
                    "    \"type\" : \"text/html\",\n" +
                    "    \"text\" : \"" + message + "\"\n" +
                    "  },\n" +
                    "  \"participants\" : [ \"" + username + "\" ]}";


            // Make v3 call for creating a direct message\
            try {
                CloseableHttpResponse response = post(json, service);
                int code = response.getStatusLine().getStatusCode();
                if (code ==  HttpURLConnection.HTTP_CREATED) {
                    // TODO Let user know that message was sent
                } else {
                    // TODO Let user know that there was an error sending the message
                    System.out.println("Error: " + code + " - " + EntityUtils.toString(response.getEntity()));
                }
            } catch (IOException e) {
                // TODO Handle this exception
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

        } else {
            System.out.println("Unrecognized text: " + rawText);
        }

    }
}
