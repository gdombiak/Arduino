package com.jivesoftware.arduino.jive;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Created with IntelliJ IDEA.
 * User: gaston
 * Date: 11/8/13
 * Time: 12:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class LikeContentCommand extends JiveCommand {

    private String likeURL;

    public LikeContentCommand(String contentURL) {
        String contentID = contentURL.substring(contentURL.lastIndexOf("/"));
        likeURL = api + "/contents" + contentID + "/likes";
    }

    public void execute() {
        // Make v3 call for liking a content
        try {
            CloseableHttpResponse response = post("", likeURL);
            int code = response.getStatusLine().getStatusCode();
            if (code ==  HttpURLConnection.HTTP_NO_CONTENT || code == HttpURLConnection.HTTP_CONFLICT) {
                // TODO Let user know that liking a content was sent
            } else {
                // TODO Let user know that there was an error liking a content
                System.out.println("Error: " + code + " - " + EntityUtils.toString(response.getEntity()));
            }
        } catch (IOException e) {
            // TODO Handle this exception
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }
}
