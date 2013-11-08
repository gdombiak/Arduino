package com.jivesoftware.arduino.jive;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

/**
 * System properties:
 * <ul>
 *     <li>username - <b>ed</b> or null means gato</li>
 *     <li>password - <b>must be defined</b> or kaboom</li>
 * </ul>
 */
public abstract class JiveCommand {

    protected static final String schema = "https";
    protected static final String hostname = "brewspace.jiveland.com";
    protected static final String api = schema + "://" + hostname + "/api/core/v3";
    protected static final String clientUsername = "ed".equals(System.getProperty("username")) ? "ed.venaglia" : "gato";
    protected static final String clientPassword = System.getProperty("password");
    public static final String ED_USERNAME = "3210";
    public static final String GATO_USERNAME = "2029";

    protected String getUsername(String user) {
        String username = api + "/people/";
        if ("ed".equalsIgnoreCase(user)) {
            username += ED_USERNAME;
        } else if ("gato".equalsIgnoreCase(user) || "fred".equalsIgnoreCase(user)) {
            username += GATO_USERNAME;
        } else if ("craig".equalsIgnoreCase(user)) {
            username += "2890";
        }
        return username;
    }

    protected CloseableHttpResponse post(String json, String service) throws IOException {
        HttpPost httpPost = new HttpPost(service);
        StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
        httpPost.setEntity(entity);
        return execute(httpPost);

    }

    protected CloseableHttpResponse get(String service) throws IOException {
        return execute(new HttpGet(service));
    }

    private CloseableHttpResponse execute(HttpRequestBase request) throws IOException {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM),
                new UsernamePasswordCredentials(clientUsername, clientPassword));
        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider).build();
        // Add pre-emptive BASIC auth
        AuthCache authCache = new BasicAuthCache();
        BasicScheme basicScheme = new BasicScheme();
        HttpHost httpHost = new HttpHost(hostname, -1, schema);
        authCache.put(httpHost, basicScheme);
        HttpContext httpContext = new BasicHttpContext();
        httpContext.setAttribute(ClientContext.AUTH_CACHE, authCache);

        return httpclient.execute(request, httpContext);

    }
}
