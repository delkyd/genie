/*
 *
 *  Copyright 2014 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.genie.client;

import com.google.common.collect.Multimap;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.genie.common.exceptions.GenieException;
import com.netflix.genie.common.model.Job;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton class, which acts as the client library for the Genie Execution
 * Service.
 *
 * @author skrishnan
 * @author tgianos
 */
public final class ExecutionServiceClient extends BaseGenieClient {

    private static final Logger LOG = LoggerFactory
            .getLogger(ExecutionServiceClient.class);

    private static final String BASE_EXECUTION_REST_URL = BASE_REST_URL + "jobs";

    // reference to the instance object
    private static ExecutionServiceClient instance;

    /**
     * Private constructor for singleton class.
     *
     * @throws IOException if there is any error during initialization
     */
    private ExecutionServiceClient() throws IOException {
        super();
    }

    /**
     * Returns the singleton instance that can be used by clients.
     *
     * @return ExecutionServiceClient instance
     * @throws IOException if there is an error instantiating client
     */
    public static synchronized ExecutionServiceClient getInstance() throws IOException {
        if (instance == null) {
            instance = new ExecutionServiceClient();
        }

        return instance;
    }

    /**
     * Submits a job using given parameters.
     *
     * @param job for submitting job (can't be null)<br>
     *            <p/>
     *            More details can be found on the Genie User Guide on GitHub.
     * @return updated jobInfo for submitted job, if there is no error
     * @throws GenieException
     */
    public Job submitJob(final Job job) throws GenieException {
        if (job == null) {
            throw new GenieException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "No job entered to validate");
        }
        job.validate();
        final HttpRequest request = this.buildRequest(
                Verb.POST,
                BASE_EXECUTION_REST_URL,
                null,
                job);
        return (Job) this.executeRequest(request, null, Job.class);
    }

    /**
     * Gets job information for a given jobID.
     *
     * @param id the Genie jobID (can't be null)
     * @return the jobInfo for this jobID
     * @throws GenieException
     */
    public Job getJob(final String id) throws GenieException {
        if (StringUtils.isBlank(id)) {
            final String msg = "Missing required parameter: id";
            LOG.error(msg);
            throw new GenieException(
                    HttpURLConnection.HTTP_BAD_REQUEST, msg);
        }

        final HttpRequest request = this.buildRequest(
                Verb.GET,
                StringUtils.join(
                        new String[]{BASE_EXECUTION_REST_URL, id},
                        SLASH),
                null,
                null);
        return (Job) this.executeRequest(request, null, Job.class);
    }

    /**
     * Gets a set of jobs for the given parameters.
     *
     * @param params key/value pairs in a map object.<br>
     *               <p/>
     *               More details on the parameters can be found on the Genie User Guide on
     *               GitHub.
     * @return List of jobs that match the filter
     * @throws GenieException
     */
    public List<Job> getJobs(final Multimap<String, String> params) throws GenieException {
        final HttpRequest request = this.buildRequest(
                Verb.GET,
                BASE_EXECUTION_REST_URL,
                params,
                null);
        return (List<Job>) this.executeRequest(request, List.class, Job.class);
    }

    /**
     * Wait for job to complete, until the given timeout.
     *
     * @param id           the Genie job ID to wait for completion
     * @param blockTimeout the time to block for (in ms), after which a
     *                     GenieException will be thrown
     * @return the jobInfo for the job after completion
     * @throws GenieException       on service errors
     * @throws InterruptedException on timeout/thread errors
     */
    public Job waitForCompletion(final String id, final long blockTimeout)
            throws GenieException, InterruptedException {
        //Should we use Future? See:
        //https://github.com/Netflix/ribbon/blob/master/ribbon-examples
        ///src/main/java/com/netflix/ribbon/examples/GetWithDeserialization.java
        final long pollTime = 10000;
        return waitForCompletion(id, blockTimeout, pollTime);
    }

    /**
     * Wait for job to complete, until the given timeout.
     *
     * @param id           the Genie job ID to wait for completion
     * @param blockTimeout the time to block for (in ms), after which a
     *                     GenieException will be thrown
     * @param pollTime     the time to sleep between polling for job status
     * @return the jobInfo for the job after completion
     * @throws GenieException       on service errors
     * @throws InterruptedException on timeout/thread errors
     */
    public Job waitForCompletion(final String id, final long blockTimeout, final long pollTime)
            throws GenieException, InterruptedException {
        if (StringUtils.isEmpty(id)) {
            final String msg = "Missing required parameter: id.";
            LOG.error(msg);
            throw new GenieException(
                    HttpURLConnection.HTTP_BAD_REQUEST, msg);
        }

        final long startTime = System.currentTimeMillis();

        while (true) {
            final Job job = getJob(id);

            // wait for job to finish - and finish time to be updated
            if (!job.getFinished().equals(new Date(0))) {
                return job;
            }

            // block until timeout
            long currTime = System.currentTimeMillis();
            if (currTime - startTime < blockTimeout) {
                Thread.sleep(pollTime);
            } else {
                final String msg = "Timed out waiting for job to finish";
                LOG.error(msg);
                throw new InterruptedException(msg);
            }
        }
    }

    /**
     * Kill a job using its jobID.
     *
     * @param id the Genie jobID for the job to kill
     * @return the final job status for this job
     * @throws GenieException
     */
    public Job killJob(final String id) throws GenieException {
        if (StringUtils.isBlank(id)) {
            final String msg = "Missing required parameter: id";
            LOG.error(msg);
            throw new GenieException(
                    HttpURLConnection.HTTP_BAD_REQUEST, msg);
        }

        // this assumes that the service will forward the delete to the right
        // instance
        final HttpRequest request = this.buildRequest(
                Verb.DELETE,
                StringUtils.join(
                        new String[]{BASE_EXECUTION_REST_URL, id},
                        SLASH),
                null,
                null);
        return (Job) this.executeRequest(request, null, Job.class);
    }

    /**
     * Add some more tags to a given job.
     *
     * @param id   The id of the job to add tags to. Not
     *             Null/empty/blank.
     * @param tags The tags to add. Not null or empty.
     * @return The new set of tags for the given job.
     * @throws GenieException
     */
    public Set<String> addTagsToJob(
            final String id,
            final Set<String> tags) throws GenieException {
        if (StringUtils.isBlank(id)) {
            final String msg = "Missing required parameter: id";
            LOG.error(msg);
            throw new GenieException(HttpURLConnection.HTTP_BAD_REQUEST, msg);
        }
        if (tags == null || tags.isEmpty()) {
            final String msg = "Missing required parameter: tags";
            LOG.error(msg);
            throw new GenieException(HttpURLConnection.HTTP_BAD_REQUEST, msg);
        }

        final HttpRequest request = this.buildRequest(
                Verb.POST,
                StringUtils.join(
                        new String[]{BASE_EXECUTION_REST_URL, id, "tags"},
                        SLASH),
                null,
                tags);
        return (Set<String>) this.executeRequest(request, Set.class, String.class);
    }

    /**
     * Get the active set of tags for the given job.
     *
     * @param id The id of the job to get tags for. Not
     *           Null/empty/blank.
     * @return The set of tags for the given job.
     * @throws GenieException
     */
    public Set<String> getTagsForJob(final String id) throws GenieException {
        if (StringUtils.isBlank(id)) {
            String msg = "Missing required parameter: id";
            LOG.error(msg);
            throw new GenieException(HttpURLConnection.HTTP_BAD_REQUEST, msg);
        }

        final HttpRequest request = this.buildRequest(
                Verb.GET,
                StringUtils.join(
                        new String[]{BASE_EXECUTION_REST_URL, id, "tags"},
                        SLASH),
                null,
                null);
        return (Set<String>) this.executeRequest(request, Set.class, String.class);
    }

    /**
     * Update the tags for a given job.
     *
     * @param id   The id of the job to update the tags for.
     *             Not null/empty/blank.
     * @param tags The tags to replace existing tag
     *             files with. Not null.
     * @return The new set of job tags.
     * @throws GenieException
     */
    public Set<String> updateTagsForJob(
            final String id,
            final Set<String> tags) throws GenieException {
        if (StringUtils.isBlank(id)) {
            final String msg = "Missing required parameter: id";
            LOG.error(msg);
            throw new GenieException(
                    HttpURLConnection.HTTP_BAD_REQUEST, msg);
        }
        if (tags == null) {
            final String msg = "Missing required parameter: tags";
            LOG.error(msg);
            throw new GenieException(
                    HttpURLConnection.HTTP_BAD_REQUEST, msg);
        }

        final HttpRequest request = this.buildRequest(
                Verb.PUT,
                StringUtils.join(
                        new String[]{BASE_EXECUTION_REST_URL, id, "tags"},
                        SLASH),
                null,
                tags);
        return (Set<String>) this.executeRequest(request, Set.class, String.class);
    }

    /**
     * Delete all the tags from a given job.
     *
     * @param id The id of the job to delete the tags from.
     *           Not null/empty/blank.
     * @return Empty set if successful
     * @throws GenieException
     */
    public Set<String> removeAllTagsForJob(
            final String id) throws GenieException {
        if (StringUtils.isBlank(id)) {
            final String msg = "Missing required parameter: id";
            LOG.error(msg);
            throw new GenieException(
                    HttpURLConnection.HTTP_BAD_REQUEST, msg);
        }

        final HttpRequest request = this.buildRequest(
                Verb.DELETE,
                StringUtils.join(
                        new String[]{BASE_EXECUTION_REST_URL, id, "tags"},
                        SLASH),
                null,
                null);
        return (Set<String>) this.executeRequest(request, Set.class, String.class);
    }

    /**
     * Remove tag from a given job.
     *
     * @param id The id of the job to delete the tag from. Not
     *           null/empty/blank.
     * @return The tag for the job.
     * @throws GenieException
     */
    public Set<String> removeTagForJob(
            final String id) throws GenieException {
        if (StringUtils.isBlank(id)) {
            final String msg = "Missing required parameter: id";
            LOG.error(msg);
            throw new GenieException(
                    HttpURLConnection.HTTP_BAD_REQUEST, msg);
        }

        final HttpRequest request = this.buildRequest(
                Verb.DELETE,
                StringUtils.join(
                        new String[]{
                                BASE_EXECUTION_REST_URL,
                                id,
                                "tags"
                        },
                        SLASH),
                null,
                null);
        return (Set<String>) this.executeRequest(request, Set.class, String.class);
    }
}
