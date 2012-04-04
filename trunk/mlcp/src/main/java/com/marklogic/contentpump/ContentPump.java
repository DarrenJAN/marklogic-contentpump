/*
 * Copyright 2003-2012 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.contentpump;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.util.GenericOptionsParser;

/**
 * ContentPump entry point.  MarkLogic ContentPump is a tool that moves content 
 * between a MarkLogic database and file system or copies content from one 
 * MarkLogic database to another.
 * 
 * @author jchen
 *
 */
public class ContentPump implements ConfigConstants {
    
    public static final Log LOG = LogFactory.getLog(ContentPump.class);
    
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        String[] expandedArgs = null;
        try {
            expandedArgs = OptionsFileUtil.expandArguments(args);
        } catch (Exception ex) {
            LOG.error("Error while expanding arguments", ex);
            System.err.println(ex.getMessage());
            System.err.println("Try 'mloader help' for usage.");
        }
        
        int rc = runCommand(expandedArgs);
        System.exit(rc);
    }

    private static int runCommand(String[] args) {
        // get command
        String cmd = args[0];
        if (cmd.equals("help")) {
            printUsage();
            return 1;
        }
        Command command = Command.forName(cmd);
        
        // get options arguments
        String[] optionArgs = Arrays.copyOfRange(args, 1, args.length);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Command: " + command);
            LOG.debug("arguments: " + optionArgs);
        }
        
        // parse hadoop specific options
        Configuration conf = new Configuration();
        GenericOptionsParser genericParser = new GenericOptionsParser(
                conf, optionArgs);
        String[] remainingArgs = genericParser.getRemainingArgs();
        
        // parse command specific options
        Options options = new Options();
        command.configOptions(options);
        CommandLineParser parser = new GnuParser();
        CommandLine cmdline;
        try {
            cmdline = parser.parse(options, remainingArgs);
        } catch (Exception e) {
            LOG.error("Error parsing the command arguments", e);
            System.err.println(e.getMessage());
            // Print the command usage message and exit.
            command.printUsage();
            return 1; // Exit on exception here.
        }
        
        // check running mode and hadoop home configuration       
        String mode = cmdline.getOptionValue(MODE);
        String hadoopHome = System.getenv(HADOOP_HOME_ENV_NAME);
        if (cmdline.hasOption(HADOOP_HOME)) {
            hadoopHome = cmdline.getOptionValue(HADOOP_HOME);
        }
        boolean distributed = hadoopHome != null && (mode == null ||
                mode.equals(MODE_DISTRIBUTED));
        if (LOG.isDebugEnabled()) {
            LOG.debug("Running in: " + (distributed ? "distributed " : "local")
                + "mode");
        }
        
        if (distributed) {
            // set new class loader based on Hadoop home
            ClassLoader parent = conf.getClassLoader();
            File file = new File(hadoopHome, "conf");
            if (file.exists()) {
                URL url = null;
                try {
                    url = file.toURI().toURL();
                } catch (MalformedURLException e) {
                    LOG.error("Error converting $HADOOP_HOME/conf to URL", e);
                    System.err.println(e.getMessage());
                    return 1;
                }
                URL[] urls = new URL[1];
                urls[0] = url;
                ClassLoader classLoader = new URLClassLoader(urls, parent);
                Thread.currentThread().setContextClassLoader(classLoader);
            } else {
                Exception ex = new FileNotFoundException(file.getAbsolutePath());
                LOG.error("Hadoop conf directory is not found: " + file, ex);
                System.err.println(ex.getMessage());
                return 1;
            }
        }
        
        // create job
        Job job = null;
        try {
            job = command.createJob(conf, cmdline);
        } catch (Exception e) {
            // Print exception message.
            System.err.println(e.getMessage());
            return 1;
        }
        
        // run job
        if (distributed) {
            // submit job
            return submitJob(job); 
        } else {
            return runJobLocally(job, cmdline);
        }
    }

    private static int submitJob(Job job) {
        String cpHome = 
            System.getProperty(CONTENTPUMP_HOME_PROPERTY_NAME);
        String cpVersion =
            System.getProperty(CONTENTPUMP_VERSION_PROPERTY_NAME);
        String cpJarName = CONTENTPUMP_JAR_NAME.replace("<VERSION>", 
                cpVersion);
        // set job jar
        File cpJar = new File(cpHome, cpJarName);
        if (!cpJar.exists()) {
            Exception ex = new FileNotFoundException(cpJar.getAbsolutePath());
            LOG.error("ContentPump jar file not found: " + 
                    cpJar.getAbsolutePath(), ex);
            System.err.println(ex.getMessage());
            return 1;
        }
        
        Configuration conf = job.getConfiguration();
        try {
            conf.set("mapred.jar", cpJar.toURI().toURL().toString());

            File cpHomeDir= new File(cpHome);
            FilenameFilter filter = new FilenameFilter() {

                @Override
                public boolean accept(File dir, String name) {
                    if (name.endsWith(".jar")) {
                        return true;
                    } else {
                        return false;
                    }
                }

            };
        
            // set lib jars
            StringBuilder jars = new StringBuilder();
            for (File jar : cpHomeDir.listFiles(filter)) {
                if (jars.length() > 0) {
                    jars.append(',');
                }
                jars.append(jar.toURI().toURL().toString());
            }
            conf.set("tmpjars", jars.toString());
        
        
            job.waitForCompletion(true);
            return 0;
        } catch (Exception e) {
            LOG.error("Error executing job", e);
            System.err.println(e.getMessage());
            return 1;
        }    
    }
    
    private static int runJobLocally(Job job, CommandLine cmdline) {
        try {
            LocalJobRunner runner = new LocalJobRunner(job, cmdline);
            runner.run();
            return 0;
        } catch (Exception e) {
            LOG.error("Error running a job locally", e);
            System.err.println(e.getMessage());
            return 1;
        }      
    }

    private static void printUsage() {
        // TODO Auto-generated method stub
        
    }
}
