/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.weaver.maven;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

/**
 * This class redirects calls to java.util Logging to Mojo logging.
 */
public class JavaLoggingToMojoLoggingRedirector {
    private final List<Handler> removedHandlers = new ArrayList<>();

    /**
     * The Maven mojo logger to delegate messages to.
     */
    final Log mojoLogger;

    private JDKLogHandler activeHandler;

    /**
     * @param mojoLogger
     *            the Maven mojo logger to delegate messages to.
     */
    public JavaLoggingToMojoLoggingRedirector(final Log mojoLogger) {
        this.mojoLogger = mojoLogger;
    }

    /**
     * Activates this feature.
     * @throws MojoExecutionException in the event of failure
     */
    public void activate() throws MojoExecutionException {
        try {
            final Logger rootLogger = LogManager.getLogManager().getLogger("");
            // remove old handlers
            for (final Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
                removedHandlers.add(handler);
            }
            if (removedHandlers.isEmpty()) {
                throw new MojoExecutionException("could not remove any handler. aborting.");
            }
            // add our own
            activeHandler = new JDKLogHandler();
            activeHandler.setLevel(Level.ALL);
            rootLogger.setLevel(Level.ALL);
            rootLogger.addHandler(activeHandler);
        } catch (Exception exc) {
            throw new MojoExecutionException("failed to activate the jul logging redirector", exc);
        }
    }

    /**
     * Deactivate the redirection and put the original Handlers back in place
     * again.
     */
    public void deactivate() {
        final Logger rootLogger = LogManager.getLogManager().getLogger("");
        // remove old handlers

        if (Stream.of(rootLogger.getHandlers()).anyMatch(h -> h == activeHandler)) {
            rootLogger.removeHandler(activeHandler);
        }
        removedHandlers.forEach(rootLogger::addHandler);
    }

    private class JDKLogHandler extends Handler {

        @Override
        public void publish(final LogRecord record) {
            final Throwable exception = record.getThrown();
            final Level level = record.getLevel();
            if (level == Level.SEVERE && mojoLogger.isErrorEnabled()) {
                if (exception == null) {
                    mojoLogger.error(getMessage(record));
                } else {
                    mojoLogger.error(getMessage(record), exception);
                }
            } else if (level == Level.WARNING && mojoLogger.isWarnEnabled()) {
                if (exception == null) {
                    mojoLogger.warn(getMessage(record));
                } else {
                    mojoLogger.warn(getMessage(record), exception);
                }
            } else if (level == Level.INFO && mojoLogger.isInfoEnabled()) {
                if (exception == null) {
                    mojoLogger.info(getMessage(record));
                } else {
                    mojoLogger.info(getMessage(record), exception);
                }
            } else if (mojoLogger.isDebugEnabled()) {
                if (exception == null) {
                    mojoLogger.debug(getMessage(record));
                } else {
                    mojoLogger.debug(getMessage(record), exception);
                }
            }
        }

        private String getMessage(final LogRecord record) {
            final ResourceBundle bundle = record.getResourceBundle();
            final String message;
            if (bundle != null && bundle.containsKey(record.getMessage())) {
                // todo: cannot enforce Locale.ENGLISH here
                message = bundle.getString(record.getMessage());
            } else {
                message = record.getMessage();
            }
            final Object[] params = record.getParameters();
            if (ArrayUtils.isNotEmpty(params)) {
                return new MessageFormat(message).format(params);
            }
            return message;
        }

        @Override
        public void flush() {
            // nothing to do
        }

        @Override
        public void close() {
            // nothing to do
        }
    }
}
