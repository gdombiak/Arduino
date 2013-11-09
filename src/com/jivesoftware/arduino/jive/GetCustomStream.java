package com.jivesoftware.arduino.jive;

import com.jivesoftware.arduino.ArduinoConnection;
import com.jivesoftware.arduino.ListenAndSpeak;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: gaston
 * Date: 11/5/13
 * Time: 1:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class GetCustomStream extends JiveCommand {

    private static final String stream = isEd() ? "16197" : "16172";

    private static final String service = api + "/streams/" + stream + "/activities";

    private String lastTimestamp = "";
    private ListenAndSpeak listenAndSpeak;
    private Thread pollingThread;
    private final AtomicReference<Boolean> running = new AtomicReference<Boolean>(false);

    private GetDirectMessages getDirectMessages;

    public static void main(String[] args) throws Exception {
        ArduinoConnection arduinoConnection = new ArduinoConnection();
        new GetCustomStream(new ListenAndSpeak(arduinoConnection)).execute();
    }

    public GetCustomStream(ListenAndSpeak listenAndSpeak) {
        this.listenAndSpeak = listenAndSpeak;
        getDirectMessages = new GetDirectMessages(listenAndSpeak);
    }

    public void execute() {
        try {
            System.out.println(new Date() + " - Polling " + (isEd() ? "Ed" : "Gato") + "'s custom stream");
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
                        // Check entry type
                        String verb = firstObject.getString("verb");
                        if ("jive:replied".equals(verb)) {
                            String whatHappened = firstObject.getString("content");

                            Pattern pattern = Pattern.compile("(<a href[\\d\\D&&[^>]]*>)([\\d\\D&&[^<]]*)(</a>)([\\d\\D&&[^<]]*)");
                            Matcher matcher = pattern.matcher(whatHappened);
                            matcher.find();
                            String user = matcher.group(2);
                            String action = matcher.group(4);
                            matcher.find();
                            String object = matcher.group(2);

                            // Say this text
                            String sayThis = user + action + object;
                            System.out.println("Got from stream: " + sayThis);
                            listenAndSpeak.speak(getVoice(), sayThis);

                        } else if ("jive:liked".equals(verb)) {
                            String whatHappened = firstObject.getString("content");

                            Pattern pattern = Pattern.compile("(<a href[\\d\\D&&[^>]]*>)([\\d\\D&&[^<]]*)(</a>)([\\d\\D&&[^<]]*)");
                            Matcher matcher = pattern.matcher(whatHappened);
                            matcher.find();
                            String user = matcher.group(2);
                            String action = matcher.group(4);
                            matcher.find();
                            String object = matcher.group(2);

                            String adjective = "";
                            JSONObject activityObject2 = firstObject.optJSONObject("object");
                            if (activityObject2 != null) {
                                JSONObject author = activityObject2.optJSONObject("author");
                                if (author != null) {
                                    if (author.getString("id").endsWith(isEd() ? ED_USERNAME : GATO_USERNAME)) {
                                        // Someone liked your content
                                        adjective = "your";
                                    } else {
                                        // Someone liked someone else's content
                                        adjective = author.getString("displayName");
                                    }
                                }
                            }
//                            JSONObject jive = firstObject.optJSONObject("jive");
//                            if (jive != null) {
//                                JSONObject parentActor = jive.optJSONObject("parentActor");
//                                if (parentActor != null) {
//                                    if (parentActor.getString("id").endsWith(isEd() ? ED_USERNAME : GATO_USERNAME)) {
//                                        // Someone liked your content
//                                        adjective = "your";
//                                    } else {
//                                        // Someone liked someone else's content
//                                        adjective = parentActor.getString("displayName");
//                                    }
//                                }
//                            }

                            String contentType = "";
                            JSONObject activityObject = firstObject.optJSONObject("object");
                            if (activityObject != null) {
                                String objectType = activityObject.optString("objectType");
                                if ("jive:discussion".equals(objectType)) {
                                    contentType = " discussion ";
                                } else if ("jive:document".equals(objectType)) {
                                    contentType = " document ";
                                } else if ("jive:message".equals(objectType)) {
                                    contentType = " reply ";
                                } else {
                                    System.out.println("Add IF case for this objectType: " + objectType);
                                }
                            }

                            // Say this text  (eg. "Ed Venaglia likes your discussion test")
                            String sayThis = user + action + adjective + contentType + object;
                            System.out.println("Got from stream: " + sayThis);
                            listenAndSpeak.speak(getVoice(), sayThis);
                        } else if ("jive:created".equals(verb)) {
                            JSONObject activityObject = firstObject.optJSONObject("object");
                            if (activityObject != null) {
                                String objectType = activityObject.optString("objectType");
                                if ("jive:discussion".equals(objectType)) {
                                    // Discussion was created
                                    boolean question = "true".equals(activityObject.optString("question"));
                                    String subject = activityObject.getString("displayName");
                                    String body = activityObject.getString("summary");
                                    String actorName = firstObject.getJSONObject("actor").getString("displayName");
                                    String contentURL = activityObject.getString("id");

                                    String headline;
                                    if (question) {
                                        headline = actorName + " asked " + subject;
                                    } else {
                                        headline = actorName + " wants to discuss " + subject;
                                    }
                                    System.out.println("New discussion detected. Headline: " + headline + ". Summary: " + body);
                                    LikeContentCommand likeContentCommand = new LikeContentCommand(listenAndSpeak, contentURL);
                                    ReplyDiscussion replyDiscussion = new ReplyDiscussion(listenAndSpeak, contentURL);
                                    listenAndSpeak.remember(likeContentCommand, replyDiscussion, headline, body);
                                    listenAndSpeak.speak(getVoice(), headline);
                                } else if ("jive:document".equals(objectType)) {
                                    // Document was created
                                    // TODO Implement me if we want
                                } else {
                                    System.out.println("Add IF case for this objectType: " + objectType);
                                }
                            }

                        } else {
                            System.out.println("Found unexpected verb: " + verb + ". Entry: " + firstObject);
                        }
                    }
                } catch (JSONException e) {
                    // TODO Handle this exception
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                // Lets check now for direct messages
                getDirectMessages.execute();
            }
        } catch (IOException e) {
            // TODO Handle this exception
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void start() {
        //To change body of created methods use File | Settings | File Templates.
        pollingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running.get()) {
                    execute();
                    // Sleep for a second
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            }
        });
        running.set(true);
        pollingThread.start();
    }

    public void stop() {
        running.set(false);
    }
}
