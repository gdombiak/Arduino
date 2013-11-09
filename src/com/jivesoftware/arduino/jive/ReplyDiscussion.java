package com.jivesoftware.arduino.jive;

import com.jivesoftware.arduino.ListenAndSpeak;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Created with IntelliJ IDEA.
 * User: gaston
 * Date: 11/8/13
 * Time: 11:33 AM
 * To change this template use File | Settings | File Templates.
 */
public class ReplyDiscussion extends ReplyCommand {

    private ListenAndSpeak listenAndSpeak;
    private String replyURL;

    public ReplyDiscussion(ListenAndSpeak listenAndSpeak, String discussionURL) {
        String discussionID = discussionURL.substring(discussionURL.lastIndexOf("/"));
        this.replyURL = api + "/messages/contents" + discussionID;
        this.listenAndSpeak = listenAndSpeak;
    }

    @Override
    public void execute(String rawText) {
        rawText = rawText.replaceFirst("reply ", "");
        String json = "{\"type\" : \"message\",\n" +
                "  \"content\" : {\n" +
                "    \"type\" : \"text/html\",\n" +
                "    \"text\" : \"" + rawText + "\"\n" +
                "  }}";

        // Make v3 call for creating a reply to a discussion
        try {
            CloseableHttpResponse response = post(json, replyURL);
            int code = response.getStatusLine().getStatusCode();
            if (code ==  HttpURLConnection.HTTP_CREATED) {
                // Let user know that reply was sent
                listenAndSpeak.speak(getVoice(), "Reply has been sent");
            } else {
                // Let user know that there was an error sending the reply
                listenAndSpeak.speak(getVoice(), "Failed to send reply");
                System.out.println("Error: " + code + " - " + EntityUtils.toString(response.getEntity()));
            }
        } catch (IOException e) {
            // TODO Handle this exception
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

}
