package com.jivesoftware.arduino.jive;

import com.jivesoftware.arduino.ListenAndSpeak;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: gaston
 * Date: 11/8/13
 * Time: 1:14 PM
 * To change this template use File | Settings | File Templates.
 */
public class GetDirectMessages extends JiveCommand {

    private static final String service = api + "/inbox?filter=type(dm)&count=2";

    private String lastTimestamp = "";
    private ListenAndSpeak listenAndSpeak;

    public GetDirectMessages(ListenAndSpeak listenAndSpeak) {
        this.listenAndSpeak = listenAndSpeak;
    }

    public void execute() {
        try {
            System.out.println(new Date() + " - Polling " + (isEd() ? "Ed" : "Gato") + "'s inbox for direct messages");
            CloseableHttpResponse response = get(service);
            int code = response.getStatusLine().getStatusCode();
            if (code == HttpURLConnection.HTTP_OK) {

                String s = EntityUtils.toString(response.getEntity());
                JSONObject jsonObject = null;
                try {
                    s = s.replaceFirst("^throw [^;]*;", "");
                    jsonObject = new JSONObject(s);
                    JSONArray list = jsonObject.getJSONArray("list");
                    JSONObject firstObject = list.getJSONObject(0);
                    String published = firstObject.getString("published");
                    if (!published.equals(lastTimestamp)) {
                        // Remember last updated entry
                        lastTimestamp = published;

                        String whatHappened = firstObject.getString("content");
                        Pattern pattern = Pattern.compile("(<a href[\\d\\D&&[^>]]*>)([\\d\\D&&[^<]]*)(</a>)([\\d\\D&&[^<]]*)");
                        Matcher matcher = pattern.matcher(whatHappened);
                        matcher.find();
                        String user = matcher.group(2);
                        String action = " said ";
                        matcher.find();
                        String object = matcher.group(2);

                        // Say this text  (eg. "Ed Venaglia said blablabla")
                        String sayThis = user + action + object;
                        System.out.println("Got direct message: " + sayThis);
                        listenAndSpeak.speak(getVoice(), sayThis);
                    }
                } catch (JSONException e) {
                    // TODO Handle this exception
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        } catch (IOException e) {
            // TODO Handle this exception
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

}
