/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017
 */

package com.ibm.streamsx.rest;

import static com.ibm.streamsx.topology.internal.gson.GsonUtilities.jstring;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpEntity;
import org.apache.http.auth.AUTH;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;

import com.google.gson.JsonObject;
import com.ibm.streamsx.topology.context.remote.RemoteContext;

public class StreamingAnalyticsServiceV2 extends AbstractStreamingAnalyticsService {

    private static final String MEMBER_EXPIRATION = "expiration";
    private static final String MEMBER_ACCESS_TOKEN = "access_token";
    private static final String BUNDLE_ID_PARAM = "bundle_id=";
    private static final long MS = 1000L;
    private static final long EXPIRY_PAD_MS = 300 * MS;

    private long authExpiryTime = -1L;
    
    private String statusUrl;
    private String jobSubmitUrl;
    private String buildsUrl;

    StreamingAnalyticsServiceV2(JsonObject service) {
        super(service);
    }

    // Synchronized because it needs to read and possibly write two members
    // that are interdependent: authExpiryTime and authorization. Should be
    // fast enough without getting tricky: contention should be rare because
    // of the way we use this, and this should be fast compared to the network
    // I/O that typically follows using the returned authorization.
    @Override
    synchronized protected String getAuthorization() {
        if (System.currentTimeMillis() > authExpiryTime) {
            refreshAuthorization();
        }
        return authorization;
    }

    private void refreshAuthorization() {
        String tokenUrl = StreamsRestUtils.getTokenUrl(credentials);
        String apiKey = StreamsRestUtils.getServiceApiKey(credentials);
        JsonObject response = StreamsRestUtils.getToken(tokenUrl, apiKey);
        if (null != response && response.has(MEMBER_ACCESS_TOKEN)
                && response.has(MEMBER_EXPIRATION)) {
            String accessToken = response.get(MEMBER_ACCESS_TOKEN).getAsString();
            setAuthorization(StreamsRestUtils.createBearerAuth(accessToken));
            long expirySecs = response.get(MEMBER_EXPIRATION).getAsLong();
            authExpiryTime = (expirySecs * MS) - EXPIRY_PAD_MS;
        }
    }

    @Override
    protected String getStatusUrl(CloseableHttpClient httpclient) throws IOException {
        if (null == statusUrl) {
            setUrls(httpclient);
        }
        return statusUrl;
    }

    @Override
    protected String getJobSubmitUrl(CloseableHttpClient httpclient, File bundle)
            throws IOException {
        if (null == jobSubmitUrl) {
            setUrls(httpclient);
        }
        String name = URLEncoder.encode(bundle.getName(), StandardCharsets.UTF_8.name());
        StringBuilder sb = new StringBuilder(jobSubmitUrl.length() + 1
                + BUNDLE_ID_PARAM.length() + name.length());
        sb.append(jobSubmitUrl);
        sb.append('?');
        sb.append(BUNDLE_ID_PARAM);
        sb.append(name);

        return sb.toString();
    }

    @Override
    protected String getJobSubmitUrl(JsonObject artifact)
            throws IOException {

        return jstring(artifact, "submit_job");
    }

    @Override
    protected String getBuildsUrl(CloseableHttpClient httpclient)
            throws IOException {
        if (null == buildsUrl) {
            setUrls(httpclient);
        }
        return buildsUrl;
    }

    private synchronized void setUrls(CloseableHttpClient httpClient)
            throws ClientProtocolException, IOException {
        // FIXME: Cloud team says this will be single URL in credentials
        // but member name not yet fixed
        statusUrl = jstring(credentials, "resources_url");
        HttpGet getStatus = new HttpGet(statusUrl);
        getStatus.addHeader(AUTH.WWW_AUTH_RESP, getAuthorization());

        JsonObject response = StreamsRestUtils.getGsonResponse(httpClient, getStatus);
        jobSubmitUrl = jstring(response, "jobs");
        // Builds URL is not public in response, kludge from jobs url
        if (!jobSubmitUrl.endsWith("/jobs")) {
            throw new IllegalStateException("Unexpected jobs URL: " + jobSubmitUrl);
        }
        buildsUrl = jobSubmitUrl.substring(0, jobSubmitUrl.length() - 4) + "builds";
    }

    @Override
    protected String getJobSubmitId() {
        return "id";
    }

    @Override
    protected JsonObject getBuild(String buildId, CloseableHttpClient httpclient,
            String authorization) throws IOException {
        String buildURL = getBuildsUrl(httpclient) + "/"
            + URLEncoder.encode(buildId, StandardCharsets.UTF_8.name());
        HttpGet httpget = new HttpGet(buildURL);
        httpget.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        httpget.addHeader("Authorization", authorization);

        return StreamsRestUtils.getGsonResponse(httpclient, httpget);
    }

    @Override
    protected JsonObject getBuildOutput(String buildId, String outputId,
            CloseableHttpClient httpclient, String authorization)
            throws IOException {
        String buildOutputURL = getBuildsUrl(httpclient) + "/"
                + URLEncoder.encode(buildId, StandardCharsets.UTF_8.name())
                + "&output_id="
                + URLEncoder.encode(outputId, StandardCharsets.UTF_8.name());
        System.out.println(buildOutputURL);
        HttpGet httpget = new HttpGet(buildOutputURL);
        httpget.addHeader("Authorization", authorization);
        httpget.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());

        return StreamsRestUtils.getGsonResponse(httpclient, httpget);
    }

    @Override
    protected JsonObject submitBuild(CloseableHttpClient httpclient,
            String authorization, File archive, String buildName)
            throws IOException {
        String newBuildURL = getBuildsUrl(httpclient);
        HttpPost httppost = new HttpPost(newBuildURL);
        httppost.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        httppost.addHeader("Authorization", authorization);

        JsonObject buildParams = new JsonObject();
        buildParams.addProperty("buildName", buildName);

        StringBody paramsBody= new StringBody(buildParams.toString(),
                ContentType.APPLICATION_JSON);

        FileBody archiveBody = new FileBody(archive,
                ContentType.create("application/zip"), archive.getName());

        HttpEntity reqEntity = MultipartEntityBuilder.create()
                .addPart("build_options", paramsBody)
                .addPart("archive_file", archiveBody).build();

        httppost.setEntity(reqEntity);
        JsonObject build = StreamsRestUtils.getGsonResponse(httpclient, httppost);
        return build;
    }

    /**
     * Submit the job from the built artifact.
     */
    protected JsonObject submitBuildArtifact(CloseableHttpClient httpclient,
            JsonObject jobConfigOverlays, String authorization, String submitUrl)
            throws IOException {
        System.err.println("Job submit: " + submitUrl);
        HttpPost postArtifact = new HttpPost(submitUrl);
        postArtifact.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        postArtifact.addHeader("Authorization", authorization);
        postArtifact.addHeader("content-type", ContentType.APPLICATION_JSON.getMimeType());

        StringBody paramsBody = new StringBody(jobConfigOverlays.toString(),
                ContentType.APPLICATION_JSON);

        HttpEntity reqEntity = MultipartEntityBuilder.create()
                .addPart("job_options", paramsBody).build();

        postArtifact.setEntity(reqEntity);

        JsonObject jso = StreamsRestUtils.getGsonResponse(httpclient, postArtifact);
        System.err.println("Artifact submit response: " + jso.toString());

        RemoteContext.REMOTE_LOGGER.info("Streaming Analytics service (" + serviceName + "): submit job response: " + jso.toString());
        return jso;
    }

    /**
     * Submit an application bundle to execute as a job.
     */
    protected JsonObject postJob(CloseableHttpClient httpClient,
            JsonObject service, File bundle, JsonObject jobConfigOverlay)
            throws IOException {

        String url = getJobSubmitUrl(httpClient, bundle);

        HttpPost postJobWithConfig = new HttpPost(url);
        postJobWithConfig.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        postJobWithConfig.addHeader(AUTH.WWW_AUTH_RESP, getAuthorization());
        FileBody bundleBody = new FileBody(bundle, ContentType.APPLICATION_OCTET_STREAM);
        StringBody configBody = new StringBody(jobConfigOverlay.toString(), ContentType.APPLICATION_JSON);

        HttpEntity reqEntity = MultipartEntityBuilder.create()
                .addPart("bundle_file", bundleBody)
                .addPart("job_options", configBody).build();

        postJobWithConfig.setEntity(reqEntity);

        JsonObject jsonResponse = StreamsRestUtils.getGsonResponse(httpClient, postJobWithConfig);

        RemoteContext.REMOTE_LOGGER.info("Streaming Analytics service (" + serviceName + "): submit job response:" + jsonResponse.toString());

        return jsonResponse;
    }

}
