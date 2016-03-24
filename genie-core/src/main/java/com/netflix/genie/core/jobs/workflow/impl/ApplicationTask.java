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

import com.netflix.genie.common.dto.Application;
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
 * Implementation of the workflow task for handling Applications that a job needs.
 *
 * @author amsharma
 * @since 3.0.0
 */
@Slf4j
public class ApplicationTask extends GenieBaseTask {

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeTask(
        @NotNull
        final Map<String, Object> context
    ) throws GenieException {
        log.debug("Executing Application Task in the workflow.");
        super.executeTask(context);

        if (this.jobExecEnv.getApplications() != null) {
            for (Application application : this.jobExecEnv.getApplications()) {

                // Create the directory for this application under applications in the cwd
                createEntityInstanceDirectory(
                    application.getId(),
                    AdminResources.APPLICATION
                );

                // Create the config directory for this id
                createEntityInstanceConfigDirectory(
                    application.getId(),
                    AdminResources.APPLICATION
                );

                // Create the dependencies directory for this id
                createEntityInstanceDependenciesDirectory(
                    application.getId(),
                    AdminResources.APPLICATION
                );

                // Get the setup file if specified and add it as source command in launcher script
                final String applicationSetupFile = application.getSetupFile();
                if (applicationSetupFile != null && StringUtils.isNotBlank(applicationSetupFile)) {
                    final String localPath = super.buildLocalFilePath(
                        this.jobWorkingDirectory,
                        application.getId(),
                        applicationSetupFile,
                        FileType.SETUP,
                        AdminResources.APPLICATION
                    );
                    this.fts.getFile(applicationSetupFile, localPath);
                    Utils.appendToWriter(writer, "# Sourcing setup file from application " + application.getId());
                    Utils.appendToWriter(writer,
                        JobConstants.SOURCE
                        + localPath.replace(this.jobWorkingDirectory, "${" + JobConstants.GENIE_JOB_DIR_ENV_VAR + "}")
                        + JobConstants.SEMICOLON_SYMBOL);

                    // Append new line
                    Utils.appendToWriter(writer, " ");
                }

                // Iterate over and get all dependencies
                for (final String dependencyFile: application.getDependencies()) {
                    final String localPath = super.buildLocalFilePath(
                        this.jobWorkingDirectory,
                        application.getId(),
                        dependencyFile,
                        FileType.DEPENDENCIES,
                        AdminResources.APPLICATION
                    );
                    this.fts.getFile(dependencyFile, localPath);
                }

                // Iterate over and get all configuration files
                for (final String configFile: application.getConfigs()) {
                    final String localPath = super.buildLocalFilePath(
                        this.jobWorkingDirectory,
                        application.getId(),
                        configFile,
                        FileType.CONFIG,
                        AdminResources.APPLICATION
                    );
                    this.fts.getFile(configFile, localPath);
                }
            }
        }
    }
}
