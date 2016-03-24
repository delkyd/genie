/*
 *
 *  Copyright 2015 Netflix, Inc.
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
package com.netflix.genie.core.jobs.workflow.impl;

import com.netflix.genie.common.exceptions.GenieException;
import com.netflix.genie.core.jobs.AdminResources;
import com.netflix.genie.core.jobs.FileType;
import com.netflix.genie.core.jobs.JobConstants;
import com.netflix.genie.core.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Implementation of the workflow task for processing cluster information a job needs.
 *
 * @author amsharma
 * @since 3.0.0
 */
@Slf4j
public class ClusterTask extends GenieBaseTask {
    /**
     * {@inheritDoc}
     */
    @Override
    public void executeTask(
        @NotNull
        final Map<String, Object> context
    ) throws GenieException {
        log.debug("Executing Cluster Task in the workflow.");

        super.executeTask(context);

        // Create the directory for this application under applications in the cwd
        createEntityInstanceDirectory(
            this.jobExecEnv.getCluster().getId(),
            AdminResources.CLUSTER
        );

        // Create the config directory for this id
        createEntityInstanceConfigDirectory(
            this.jobExecEnv.getCluster().getId(),
            AdminResources.CLUSTER
        );

        // Create the dependencies directory for this id
        createEntityInstanceDependenciesDirectory(
            this.jobExecEnv.getCluster().getId(),
            AdminResources.CLUSTER
        );

        // Get the set up file for cluster and add it to source in launcher script
        final String clusterSetupFile = jobExecEnv.getCluster().getSetupFile();

        if (clusterSetupFile != null && StringUtils.isNotBlank(clusterSetupFile)) {
            final String localPath = super.buildLocalFilePath(
                this.jobWorkingDirectory,
                jobExecEnv.getCluster().getId(),
                clusterSetupFile,
                FileType.SETUP,
                AdminResources.CLUSTER
            );

            fts.getFile(clusterSetupFile, localPath);
            Utils.appendToWriter(writer, "# Sourcing setup file from cluster " + jobExecEnv.getCluster().getId());
            Utils.appendToWriter(writer,
                JobConstants.SOURCE
                    + localPath.replace(this.jobWorkingDirectory, "${" + JobConstants.GENIE_JOB_DIR_ENV_VAR + "}")
                    + JobConstants.SEMICOLON_SYMBOL);

            // Append new line
            Utils.appendToWriter(writer, " ");
        }

        // Iterate over and get all configuration files
        for (final String configFile: jobExecEnv.getCluster().getConfigs()) {
            final String localPath = super.buildLocalFilePath(
                this.jobWorkingDirectory,
                jobExecEnv.getCluster().getId(),
                configFile,
                FileType.CONFIG,
                AdminResources.CLUSTER
            );
            this.fts.getFile(configFile, localPath);
        }
    }
}
