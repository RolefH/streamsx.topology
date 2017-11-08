/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017
 */

package com.ibm.streamsx.rest;

import static com.ibm.streamsx.topology.internal.gson.GsonUtilities.array;
import static com.ibm.streamsx.topology.internal.gson.GsonUtilities.jstring;
import static com.ibm.streamsx.topology.internal.gson.GsonUtilities.object;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpEntity;
import org.apache.http.auth.AUTH;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.streamsx.topology.context.remote.RemoteContext;
import com.ibm.streamsx.topology.internal.context.remote.DeployKeys;

/**
 * Implementation of StreamingAnalyticsService for Version 1.
 * <p>
 * This is cut & paste of the code from the original uses. 
 */
class StreamingAnalyticsServiceV1 extends AbstractStreamingAnalyticsService {
    StreamingAnalyticsServiceV1(JsonObject service) {
        super(service);
        // Authorization header never changes in V1 once set
        setAuthorization(StreamsRestUtils.createBasicAuth(credentials));
    }

    @Override
    protected String getStatusUrl(CloseableHttpClient httpClient) {
        StringBuilder sb = new StringBuilder(500);
        sb.append(jstring(credentials, "rest_url"));
        sb.append(jstring(credentials, "status_path"));
        return sb.toString();
    }

    @Override
    protected String getJobSubmitUrl(CloseableHttpClient httpClient, File bundle)
            throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder(500);
        sb.append(jstring(credentials, "rest_url"));
        sb.append(jstring(credentials, "jobs_path"));
        sb.append("?");
        sb.append("bundle_id=");
        sb.append(URLEncoder.encode(bundle.getName(), StandardCharsets.UTF_8.name()));
        return sb.toString();
    }

    @Override
    protected String getJobSubmitUrl(JsonObject artifact)
            throws UnsupportedEncodingException {
        String artifactId = jstring(artifact, "id");
        System.err.println("Submitting id: " + artifactId);
        StringBuilder sb = new StringBuilder(500);
        sb.append(jstring(credentials, "rest_url"));
        sb.append(jstring(credentials, "jobs_path").replace("jobs", "builds"));
        sb.append("?");
        sb.append("artifact_id=");
        sb.append(URLEncoder.encode(artifactId, StandardCharsets.UTF_8.name()));
        return sb.toString();
}

    @Override
    protected String getBuildsUrl(CloseableHttpClient httpClient){
        String buildURL = jstring(credentials, "jobs_path").replace("jobs", "builds");
        return jstring(credentials, "rest_url") + buildURL;
    }

    @Override
    protected String getAuthorization() {
        return authorization;
    }

    @Override
    protected String getJobSubmitId() {
        return "jobId";
    }

    @Override
    protected JsonObject getBuild(String buildId, CloseableHttpClient httpclient,
            String authorization) throws IOException {
        String buildURL = getBuildsUrl(httpclient) + "?build_id="
            + URLEncoder.encode(buildId, StandardCharsets.UTF_8.name());
        HttpGet httpget = new HttpGet(buildURL);
        httpget.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        httpget.addHeader("Authorization", authorization);

        JsonObject response = StreamsRestUtils.getGsonResponse(httpclient, httpget);
        // Get the correct build
        JsonObject build = null;
        JsonArray builds = array(response, "builds");
        for (JsonElement iterBuildElem : builds) {
            JsonObject iterBuild = iterBuildElem.getAsJsonObject();
            if (jstring(iterBuild, "id").equals(buildId))
                build = iterBuild;
        }
        return build;
    }

    @Override
    protected JsonObject getBuildOutput(String buildId, String outputId,
            CloseableHttpClient httpclient, String authorization)
            throws IOException {
        String buildOutputURL = getBuildsUrl(httpclient) + "?build_id="
                + URLEncoder.encode(buildId, StandardCharsets.UTF_8.name())
                + "&output_id="
                + URLEncoder.encode(outputId, StandardCharsets.UTF_8.name());
        System.out.println(buildOutputURL);
        HttpGet httpget = new HttpGet(buildOutputURL);
        httpget.addHeader("Authorization", authorization);
        httpget.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());

        JsonObject response = StreamsRestUtils.getGsonResponse(httpclient, httpget);
        for(JsonElement outputElem : array(response, "builds")){
            JsonObject output = outputElem.getAsJsonObject();
            if(jstring(output, "id").equals(buildId))
                return output;
        }

        return null;
    }

    @Override
    protected JsonObject submitBuild(CloseableHttpClient httpclient,
            String authorization, File archive, String buildName)
            throws IOException {
        String newBuildURL = getBuildsUrl(httpclient) + "?build_name=" +
                URLEncoder.encode(buildName, StandardCharsets.UTF_8.name());
        HttpPost httppost = new HttpPost(newBuildURL);
        httppost.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        httppost.addHeader("Authorization", authorization);

        FileBody archiveBody = new FileBody(archive,
                ContentType.create("application/zip"));

        HttpEntity reqEntity = MultipartEntityBuilder.create()
                .addPart(archive.getName(), archiveBody).build();

        httppost.setEntity(reqEntity);
        JsonObject jso = StreamsRestUtils.getGsonResponse(httpclient, httppost);
        JsonObject build = object(jso, "build");
        return build;
    }

    /**
     * Submit the job from the built artifact.
     */
    protected JsonObject submitBuildArtifact(CloseableHttpClient httpclient,
            JsonObject jobConfigOverlays, String authorization, String submitUrl)
            throws IOException {
        System.err.println("Job submit: " + submitUrl);
        HttpPut httpput = new HttpPut(submitUrl);
        httpput.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        httpput.addHeader("Authorization", authorization);
        httpput.addHeader("content-type", ContentType.APPLICATION_JSON.getMimeType());

        StringEntity params = new StringEntity(jobConfigOverlays.toString(),
                ContentType.APPLICATION_JSON);
        httpput.setEntity(params);

        JsonObject jso = StreamsRestUtils.getGsonResponse(httpclient, httpput);

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

        HttpEntity reqEntity = MultipartEntityBuilder.create().addPart("sab", bundleBody)
                .addPart(DeployKeys.JOB_CONFIG_OVERLAYS, configBody).build();

        postJobWithConfig.setEntity(reqEntity);

        JsonObject jsonResponse = StreamsRestUtils.getGsonResponse(httpClient, postJobWithConfig);

        RemoteContext.REMOTE_LOGGER.info("Streaming Analytics service (" + serviceName + "): submit job response:" + jsonResponse.toString());

        return jsonResponse;
    }
}
