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
 * Time: 12:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class LikeContentCommand extends JiveCommand {

    private ListenAndSpeak listenAndSpeak;
    private String likeURL;

    public LikeContentCommand(ListenAndSpeak listenAndSpeak, String contentURL) {
        String contentID = contentURL.substring(contentURL.lastIndexOf("/"));
        this.likeURL = api + "/contents" + contentID + "/likes";
        this.listenAndSpeak = listenAndSpeak;
    }

    public void execute() {
        // Make v3 call for liking a content
        try {
            CloseableHttpResponse response = post("", likeURL);
            int code = response.getStatusLine().getStatusCode();
            if (code ==  HttpURLConnection.HTTP_NO_CONTENT || code == HttpURLConnection.HTTP_CONFLICT) {
                // Let user know that liking a content was sent
                listenAndSpeak.speak(getVoice(), "Like has been sent");
            } else {
                // Let user know that there was an error liking a content
                listenAndSpeak.speak(getVoice(), "Failed to send like");
                System.out.println("Error: " + code + " - " + EntityUtils.toString(response.getEntity()));
            }
        } catch (IOException e) {
            // TODO Handle this exception
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private static boolean isEd() {
        return "ed".equals(System.getProperty("username"));
    }

    protected ListenAndSpeak.Voice getVoice() {
        return isEd() ? ListenAndSpeak.Voice.TOM : ListenAndSpeak.Voice.DIEGO;
    }
}
