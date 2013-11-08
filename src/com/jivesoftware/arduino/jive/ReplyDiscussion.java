package com.jivesoftware.arduino.jive;

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

    private String replyURL;

    public ReplyDiscussion(String discussionURL) {
        String discussionID = discussionURL.substring(discussionURL.lastIndexOf("/"));
        replyURL = api + "/messages/contents" + discussionID;
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
                // TODO Let user know that reply was sent
            } else {
                // TODO Let user know that there was an error sending the reply
                System.out.println("Error: " + code + " - " + EntityUtils.toString(response.getEntity()));
            }
        } catch (IOException e) {
            // TODO Handle this exception
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
