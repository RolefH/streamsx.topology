/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017
 */
package com.ibm.streamsx.rest;

import static com.ibm.streamsx.topology.internal.gson.GsonUtilities.jstring;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import javax.xml.bind.DatatypeConverter;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AUTH;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class StreamsRestUtils {

    public enum StreamingAnalyticsServiceVersion { V1, V2, UNKNOWN }

    private static final String AUTH_BEARER = "Bearer ";
    private static final String AUTH_BASIC = "Basic ";
    private static final String TOKEN_PARAMS = genTokenParams();

    private static final Logger traceLog = Logger.getLogger("com.ibm.streamsx.rest.StreamsConnectionUtils");

    private StreamsRestUtils() {}

    /**
     * Create an encoded Basic auth header for the given credentials.
     */
    static String createBasicAuth(JsonObject credentials) {
        return createBasicAuth(jstring(credentials,  "userid"),
                jstring(credentials, "password"));
    };

    /**
     * Create an encoded Basic auth header for the given userName and authToken
     */
    static String createBasicAuth(String userName, String authToken) {
        String apiCredentials = userName + ":" + authToken;
        return AUTH_BASIC + DatatypeConverter.printBase64Binary(
                apiCredentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create an encoded Bearer auth header for the given token.
     */
    static String createBearerAuth(String tokenBase64) {
        StringBuilder sb = new StringBuilder(AUTH_BEARER.length()
                + tokenBase64.length());
        sb.append(AUTH_BEARER);
        sb.append(tokenBase64);
        return sb.toString();
    }

    static JsonObject getGsonResponse(CloseableHttpClient httpClient,
            HttpRequestBase request) throws IOException {
        request.addHeader("accept",
                ContentType.APPLICATION_JSON.getMimeType());

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            return gsonFromResponse(response);
        }
    }

    /**
     * Gets a JSON response to an HTTP call
     * 
     * @param executor HTTP client executor to use for call
     * @param auth Authentication header contents, or null
     * @param inputString
     *            REST call to make
     * @return response from the inputString
     * @throws IOException
     */
    static JsonObject getGsonResponse(Executor executor, String auth, String inputString)
            throws IOException {
        Request request = Request
                .Get(inputString)
                .useExpectContinue();
        if (null != auth) {
            request = request.addHeader(AUTH.WWW_AUTH_RESP, auth);
        }

        Response response = executor.execute(request);
        return gsonFromResponse(response.returnResponse());
    }

    static String getRequiredMember(JsonObject json, String member)
            throws IllegalStateException {
        JsonElement element = json.get(member);
        if (null == element || element.isJsonNull()) {
            throw new IllegalStateException("JSON missing required member "
                    + member);
        }
        return element.getAsString();
    }

    /**
     * Gets a response to an HTTP call as a string
     * 
     * @param executor HTTP client executor to use for call
     * @param auth Authentication header contents, or null
     * @param inputString
     *            REST call to make
     * @return response from the inputString
     * @throws IOException
     * 
     * TODO: unify error handling between this and gsonFromResponse(), and
     * convert callers that want JSON to getGsonResponse()
     */
    static String getResponseString(Executor executor,
            String auth, String inputString) throws IOException {
        String sReturn = "";
        Request request = Request
                .Get(inputString)
                .useExpectContinue();
        if (null != auth) {
            request = request.addHeader(AUTH.WWW_AUTH_RESP, auth);
        }

        Response response = executor.execute(request);
        HttpResponse hResponse = response.returnResponse();
        int rcResponse = hResponse.getStatusLine().getStatusCode();

        if (HttpStatus.SC_OK == rcResponse) {
            sReturn = EntityUtils.toString(hResponse.getEntity());
        } else if (HttpStatus.SC_NOT_FOUND == rcResponse) {
            // with a 404 message, we are likely to have a message from Streams
            // but if not, provide a better message
            sReturn = EntityUtils.toString(hResponse.getEntity());
            if ((sReturn != null) && (!sReturn.equals(""))) {
                throw RESTException.create(rcResponse, sReturn);
            } else {
                String httpError = "HttpStatus is " + rcResponse + " for url " + inputString;
                throw new RESTException(rcResponse, httpError);
            }
        } else {
            // all other errors...
            String httpError = "HttpStatus is " + rcResponse + " for url " + inputString;
            throw new RESTException(rcResponse, httpError);
        }
        traceLog.finest(rcResponse + ": " + sReturn);
        return sReturn;
    }

    /**
     * Determine service version based on credential contents.
     * <p>
     * Ideally, the service would return version information directly, but for
     * now key off contents we expect in the credentials.
     * <p>
     * Note also that while service version and authentication mechanism are
     * conceptually distinct, at present they are coupled so the version implies
     * the authentication mechanism.
     *  
     * @param credentials Credentials for the service.
     * @return A version or UNKNOWN.
     */
    static StreamingAnalyticsServiceVersion getStreamingAnalyticsServiceVersion(
            JsonObject credentials) {
        if (credentials.has("service_id")) { // FIXME: correct member name
            return StreamingAnalyticsServiceVersion.V2;
        } else if (credentials.has("userid") && credentials.has("password")) {
            return StreamingAnalyticsServiceVersion.V1;
        }
        return StreamingAnalyticsServiceVersion.UNKNOWN;
    }

    static JsonObject getToken(String iamUrl, String apiKey) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String key = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name());
            StringBuilder sb = new StringBuilder(iamUrl.length()
                    + TOKEN_PARAMS.length() + key.length());
            sb.append(iamUrl);
            sb.append(TOKEN_PARAMS);
            sb.append(key);
            HttpPost httpPost = new HttpPost(sb.toString());
            httpPost.addHeader("Content-Type", ContentType.APPLICATION_FORM_URLENCODED.getMimeType());
            httpPost.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());

            return StreamsRestUtils.getGsonResponse(httpClient, httpPost);
        } catch (IOException e) {
        }

        return null;
    }

    // FIXME: Where does this come from?
    // Cloud team says this might be in credentials, but values are "well-known"
    // for stage1 / production so worst case we would have to look up based on
    // service URL.
    static String getTokenUrl(JsonObject credentials) {
        return jstring(credentials, "iam_url");
    }

    // FIXME: Where does this come from?
    // Cloud team says it should be in credentials, but not sure with what
    // member name yet.
    static String getServiceApiKey(JsonObject credentials) {
        return jstring(credentials, "apiKey");
    }

    private static String genTokenParams() {
        try {
            String grantParam = "?grant_type=";
            String grantType = URLEncoder.encode("urn:ibm:params:oauth:grant-type:apikey", StandardCharsets.UTF_8.name());
            String apikeyParam = "&apikey=";
            StringBuilder sb = new StringBuilder(grantParam.length()
                    + grantType.length() + apikeyParam.length());
            sb.append(grantParam);
            sb.append(grantType);
            sb.append(apikeyParam);
            return sb.toString();
        } catch (UnsupportedEncodingException never) {
            // Can't happen since UTF-8 is always supported
        }
        return null;
    }

    // TODO: unify error handling between this and getResponseString()
    private static JsonObject gsonFromResponse(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
            final String errorInfo;
            if (entity != null)
                errorInfo = " -- " + EntityUtils.toString(entity);
            else
                errorInfo = "";
            throw new IllegalStateException(
                    "Unexpected HTTP resource from service:"
                            + response.getStatusLine().getStatusCode() + ":" +
                            response.getStatusLine().getReasonPhrase() + errorInfo);
        }

        if (entity == null)
            throw new IllegalStateException("No HTTP resource from service");

        Reader r = new InputStreamReader(entity.getContent());
        JsonObject jsonResponse = new Gson().fromJson(r, JsonObject.class);
        EntityUtils.consume(entity);
        return jsonResponse;
    }

}
