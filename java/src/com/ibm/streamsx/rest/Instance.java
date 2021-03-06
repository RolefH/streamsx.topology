/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017
 */
package com.ibm.streamsx.rest;

import static com.ibm.streamsx.topology.internal.context.streamsrest.StreamsKeys.getStreamsInstanceURL;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import com.ibm.streamsx.rest.internal.ICP4DAuthenticator;
import com.ibm.streamsx.rest.internal.StandaloneAuthenticator;
import com.ibm.streamsx.topology.internal.context.streamsrest.StreamsKeys;
import com.ibm.streamsx.topology.internal.streams.Util;

/**
 * 
 * An object describing an IBM Streams Instance
 * 
 */
public class Instance extends Element {

    @Expose
    private String activeServices;
    @Expose
    private ActiveVersion activeVersion;
    @Expose
    private String productVersion;
    @Expose
    private String activeViews;
    @Expose
    private String configuredViews;
    @Expose
    private long creationTime;
    @Expose
    private String creationUser;
    @Expose
    String domain;
    @Expose
    private String exportedStreams;
    @Expose
    private String health;
    @Expose
    private String hosts;
    @Expose
    private String id;
    @Expose
    private String importedStreams;
    @Expose
    private String jobs;
    @Expose
    private String operatorConnections;
    @Expose
    private String operators;
    @Expose
    private String owner;
    @Expose
    private String peConnections;
    @Expose
    private String pes;
    @Expose
    private String resourceAllocations;
    @Expose
    private String resourceType;
    @Expose
    private String restid;
    @Expose
    private long startTime;
    @Expose
    private String startedBy;
    @Expose
    private String status;
    @Expose
    private String views;

    private static final String STREAMS_REST_RESOURCES = "/streams/rest/resources";

    final static List<Instance> createInstanceList(AbstractStreamsConnection sc, String uri)
       throws IOException {        
        return createList(sc, uri, InstancesArray.class);
    }

    /**
     * Connect to a Cloud Pak for Data IBM Streams instance.
     * <P>
     * This call is equivalent to {@link #ofEndpoint(String, String, String, String, boolean)}
     * passing {@code true} for <em>verify</em>.
     * </P><P>
     * Two configurations are supported:
     * <DL>
     * <DT>Integrated configuration</DT>
     * 
     * <DD>The Streams instance is defined using the Cloud Pak for Data
     * deployment endpoint (URL) and the Streams service name.
     * <P>
     * The endpoint defaults to the environment variable  {@code CP4D_URL}.
     * An example is @{code https://cp4d_server:31843}.
     * <P>
     * The Streams service name is passed in as name, defaulting to the
     * environment variables {@code STREAMS_INSTANCE_ID}.
     * </DD>
     * 
     * <DT>Standalone configuration</DT>
     * 
     * <DD>The Streams instance is defined using its Streams REST API
     * endpoint, which is its SWS service.
     * <P>
     * The endpoint defaults to the environment variable {@code STREAMS_REST_URL}.
     * An example is {@code https://streams_sws_service:34679}.
     * <P>
     * No service name is specified thus name should be {@code null}.
     * 
     * @param endpoint Endpoint defining the Streams instance.
     * @param name Streams instance name for a integrated configuration. This
     *             value is ignored for a standalone configuration.
     * @param userName User name, if {@code null} defaults to environment variable {@code STREAMS_USERNAME} if set,
     *     otherwise the operating user identifier.
     * @param password Password, if {@code null} defaults to environment variable {@code STREAMS_PASSWORD}.

     * @return Connection to Streams instance using REST API.
     * @throws IOException Error connecting to instance.
     * 
     * @since 1.13
     */
    public static Instance ofEndpoint(String endpoint, String name, String userName, String password) throws IOException {
        return ofEndpoint(endpoint, name, userName, password, true);
    }
    
    /**
     * Connect to a Cloud Pak for Data IBM Streams instance.
     * <P>
     * Two configurations are supported:
     * <DL>
     * <DT>Integrated configuration</DT>
     * 
     * <DD>The Streams instance is defined using the Cloud Pak for Data
     * deployment endpoint (URL) and the Streams service name.
     * <P>
     * The endpoint defaults to the environment variable  {@code CP4D_URL}.
     * An example is @{code https://cp4d_server:31843}.
     * <P>
     * The Streams service name is passed in as name, defaulting to the
     * environment variables {@code STREAMS_INSTANCE_ID}.
     * </DD>
     * 
     * <DT>Standalone configuration</DT>
     * 
     * <DD>The Streams instance is defined using its Streams REST API
     * endpoint, which is its SWS service.
     * <P>
     * The endpoint defaults to the environment variable {@code STREAMS_REST_URL}.
     * An example is {@code https://streams_sws_service:34679}.
     * <P>
     * No service name is specified thus name should be {@code null}.
     * 
     * @param endpoint Endpoint defining the Streams instance.
     * @param name Streams instance name for a integrated configuration. This
     *             value is ignored for a standalone configuration.
     * @param userName User name, if {@code null} defaults to environment variable {@code STREAMS_USERNAME} if set,
     *     otherwise the operating user identifier.
     * @param password Password, if {@code null} defaults to environment variable {@code STREAMS_PASSWORD}.

     * @return Connection to Streams instance using REST API.
     * @throws IOException Error connecting to instance.
     * 
     * @since 1.13
     */
    public static Instance ofEndpoint(String endpoint, String name, String userName, String password,
            boolean verify) throws IOException {

        boolean possible_integ = true;
        if (endpoint == null) {
            endpoint = System.getenv(Util.ICP4D_DEPLOYMENT_URL);
            if (endpoint == null) {
                possible_integ = false;
                endpoint = System.getenv(Util.STREAMS_REST_URL);
                if (endpoint == null) {
                    return null;
                }
            }
        }
        if (possible_integ && name == null)
            name = System.getenv(Util.STREAMS_INSTANCE_ID);

        StreamsConnection conn;
        Instance instance = null;
        if (name != null) {
            // Integrated configuration
            conn = createIntegratedConnection(endpoint, name, userName, password, verify);
            if (!verify) {
                conn.allowInsecureHosts(true);
            }
            instance = conn.getInstance(name);
        } else {
            conn = createStandaloneConnection(endpoint, userName, password, verify);
            if (!verify) {
                conn.allowInsecureHosts(true);
            }
            // Should only be one instance, use it, or fail.        
            List<Instance> instances = conn.getInstances();
            if (instances.size() != 1) {
                return null;
            }
            instance = instances.get(0);
        }

        return instance;
    }

    /*
     * Create a connection to an instance with an integrated configuration,
     * using CP4D authentication.
     */
    private static StreamsConnection createIntegratedConnection(String endpoint, String name, String userName, String password,
            boolean verify) throws IOException {
        ICP4DAuthenticator authenticator = ICP4DAuthenticator.of(
                endpoint, name, userName, password);

        JsonObject deploy = new JsonObject();
        deploy.add(StreamsKeys.SERVICE_DEFINITION, authenticator.config(verify));

        URL instanceUrl  = new URL(getStreamsInstanceURL(deploy));
        URL restUrl;
        final String restResourcesUrl = StreamsKeys.getStreamsRestResourcesUrl(deploy);
        if (restResourcesUrl != null) {
            // here we end in CPD >= 3.0
            restUrl = new URL(restResourcesUrl);
        } else {
            // legacy way
            restUrl = new URL(instanceUrl.getProtocol(), instanceUrl.getHost(), instanceUrl.getPort(),
                    STREAMS_REST_RESOURCES);
        }

        return StreamsConnection.ofAuthenticator(restUrl.toExternalForm(), authenticator);
    }

    private static StreamsConnection createStandaloneConnection(String endpoint,
            String userName, String password, boolean verify) throws IOException {
        if (!endpoint.endsWith(STREAMS_REST_RESOURCES)) {
            URL url = new URL(endpoint);
            URL resourcesUrl = new URL(url.getProtocol(), url.getHost(),
                    url.getPort(), STREAMS_REST_RESOURCES);
            endpoint = resourcesUrl.toExternalForm();
        }
        StandaloneAuthenticator auth = StandaloneAuthenticator.of(endpoint, userName, password);
        if (auth.config(verify) != null) {
            return StreamsConnection.ofAuthenticator(endpoint, auth);
        } else {
            // Couldn't configure standalone authenticator, try Basic
            return StreamsConnection.createInstance(userName, password, endpoint);
        }
    }

    /**
     * Gets a list of {@link Job jobs} that this instance knows about
     * 
     * @return List of {@link Job IBM Streams Jobs}
     * @throws IOException
     */
    public List<Job> getJobs() throws IOException {
        return Job.createJobList(this, jobs);
    }
    
    /**
     * Gets a list of {@link ProcessingElement processing elements} for this instance.
     * 
     * @return List of {@link ProcessingElement Processing Elements}
     * @throws IOException
     * 
     * @since 1.9
     */
    public List<ProcessingElement> getPes() throws IOException {
        return ProcessingElement.createPEList(connection(), pes);
    }
    
    /**
     * Gets a list of {@link ResourceAllocation resource allocations} for this instance.
     * 
     * @return List of {@link ResourceAllocation resource allocations}
     * @throws IOException
     * 
     * @since 1.9
     */
    public List<ResourceAllocation> getResourceAllocations() throws IOException {
        return ResourceAllocation.createResourceAllocationList(connection(), resourceAllocations);
    }

    /**
     * Gets the {@link Job} for a given jobId in this instance
     * 
     * @param jobId
     *            String identifying the job
     * @return a single {@link Job}
     * @throws IOException
     */
    public Job getJob(String jobId) throws IOException {
        requireNonNull(jobId);
        
        String sGetJobURI = jobs + "/" + jobId;

        String sReturn = connection().getResponseString(sGetJobURI);
        Job job = Job.create(this, sReturn);
        return job;
    }

    /**
     * Gets information about the IBM Streams Installation that was used to
     * start this instance
     * 
     * @return {@link ActiveVersion}. Please note that Streams version >= 5.x does not have this element.
     */
    public ActiveVersion getActiveVersion() {
        return activeVersion;
    }

    /** Gets the product version, please note that this is null for Streams version < 5.2 */
    public String getProductVersion() {
        return productVersion;
    }

    /**
     * Tests if the instance has a minimum major.minor product version.
     * @param major The minimum major version
     * @param minor the minimum minor version
     * @return true if the product version is at least the major and minor version.
     *         When the version cannot be found in the REST object, <tt>false</tt> is returned.
     */
    public boolean isProductVersionAtLeast(int major, int minor) {
        String pv = productVersion;
        if (pv == null) {
            // test activeVersion
            if (activeVersion != null) {
                pv = activeVersion.getProductVersion();
            }
        }
        if (pv == null) {
            // No productVersion or activeVersion found in instance REST object
            return false;
        }
        return Util.versionAtLeast(pv, major, minor);
    }

    /**
     * Gets the time in milliseconds when this instance was created
     * 
     * @return the epoch time in milliseconds when the instance was created as a
     *         long
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Gets the user ID that created this instance
     * 
     * @return the creation user ID
     */
    public String getCreationUser() {
        return creationUser;
    }

    /**
     * Gets the summarized status of jobs in this instance
     *
     * @return the summarized status that contains one of the following values:
     *         <ul>
     *         <li>healthy</li>
     *         <li>partiallyHealthy</li>
     *         <li>partiallyUnhealthy</li>
     *         <li>unhealthy</li>
     *         <li>unknown</li>
     *         </ul>
     * 
     */
    public String getHealth() {
        return health;
    }

    /**
     * Gets the IBM Streams unique identifier for this instance
     * 
     * @return the IBM Streams unique idenitifer
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the user ID that represents the owner of this instance
     * 
     * @return the owner user ID
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Identifies the REST resource type
     *
     * @return "instance"
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Gets the time in milliseconds when the instance was started.
     * 
     * @return the epoch time in milliseconds when the instance was started as a
     *         long
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Gets the status of the instance
     *
     * @return the instance status that contains one of the following values:
     *         <ul>
     *         <li>running</li>
     *         <li>failed</li>
     *         <li>stopped</li>
     *         <li>partiallyFailed</li>
     *         <li>partiallyRunning</li>
     *         <li>starting</li>
     *         <li>stopping</li>
     *         <li>unknown</li>
     *         </ul>
     * 
     */
    public String getStatus() {
        return status;
    }
    

    private String appConsoleURL;
    void setApplicationConsoleURL(String baseUrl) throws UnsupportedEncodingException {
        appConsoleURL = baseUrl
               +  "#application/dashboard/Application%20Dashboard?instance="
               + URLEncoder.encode(getId(), "UTF-8");
    }
    /**
     * Streams application console URL.
     * Returns the Streams application console URL with
     * a filter preset to this instance identifier.
     * @return Streams application console URL
     * 
     * @since 1.11
     */
    public String getApplicationConsoleURL() {
        return appConsoleURL;
    }
    
    private Domain _domain;
    /**
     * Get the Streams domain for this instance.
     * 
     * @return Domain for this instance.ull if no domain is associated with an instance.
     * 
     * @throws IOException Error communicating with REST api.
     * 
     * @since 1.8
     */
    public Domain getDomain() throws IOException {
        if (_domain == null) {
            _domain = create(connection(), domain, Domain.class);
        }
        return _domain;
    }
    
    /**
     *  Upload a Streams application bundle (sab) to the instance.
     *  
     *  Uploading a bundle allows job submission from the returned {@link ApplicationBundle}.
     *  
     *  <BR>
     *  Note: When an instance does not support uploading a bundle the returned
     *  {@code ApplicationBundle} represents the local file {@code bundle} tied to this
     *  instance. The returned object  may still be used for job submission.
     *  
     * @param bundle path to a Streams application bundle (sab file) containing
     *     the application to be uploaded
     * @return  Application bundle representing the uploaded bundle.
     * 
     * @throws IOException Error uploading the bundle.
     * 
     * @since 1.11
     */
    public ApplicationBundle uploadBundle(File bundle) throws IOException {
    	return connection().uploadBundle(this, bundle);
    }
    
    /**
     * Submit a Streams bundle to run on the Streaming Analytics Service.
     * <P>
     * The returned {@link Result} instance has:
     * <UL>
     * <LI>{@link Result#getId()} returning the job identifier or {@code null} if
     * a job was not created..</LI>
     * <LI>{@link Result#getElement()} returning a {@link Job} instance for the submitted job or {@code null} if
     * a job was not created.</LI>
     * <LI>{@link Result#getRawResult()} return the raw JSON response.</LI>
     * </UL>
     * </P>
     * @param bundle A streams application bundle
     * @param jco Job configuration overlay in JSON format.
     * @return Result of the job submission.
     * @throws IOException Error communicating with the service.
     * 
     * @since 1.11
     */
    public Result<Job,JsonObject> submitJob(File bundle, JsonObject jco) throws IOException {
    	return uploadBundle(bundle).submitJob(jco);
    }

    /**
     * internal usae to get list of instances
     */
    private static class InstancesArray extends ElementArray<Instance> {
        @Expose
        private ArrayList<Instance> instances;
        
        @Override
        List<Instance> elements() { return instances; }
    }
}
