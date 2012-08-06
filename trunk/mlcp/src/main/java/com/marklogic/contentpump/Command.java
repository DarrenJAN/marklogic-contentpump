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

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import com.marklogic.contentpump.utilities.CommandlineOption;
import com.marklogic.mapreduce.ContentType;
import com.marklogic.mapreduce.DocumentURI;
import com.marklogic.mapreduce.Indentation;
import com.marklogic.mapreduce.MarkLogicConstants;
import com.marklogic.mapreduce.MarkLogicDocument;

/**
 * Enum of supported commands.
 * 
 * @author jchen
 */
@SuppressWarnings("static-access")
public enum Command implements ConfigConstants {
    IMPORT {        
        @Override
        public void configOptions(Options options) {
            configCommonOptions(options);
            configConnectionId(options);
            configCopyOptions(options);
            configCommonOutputOptions(options);
            configBatchTxn(options);

            Option inputFilePath = OptionBuilder
                .withArgName("path")
                .hasArg()
                .withDescription("The file system path in which to look for "
                        + "input")
                .create(INPUT_FILE_PATH);
            inputFilePath.setRequired(true);
            options.addOption(inputFilePath);
            Option inputFilePattern = OptionBuilder
                .withArgName("regex pattern")
                .hasArg()
                .withDescription("Matching regex pattern for files found in "
                    + "the input file path")
                .create(INPUT_FILE_PATTERN);
            options.addOption(inputFilePattern);
            Option aggregateRecordElement = OptionBuilder
                .withArgName("QName")
                .hasArg()
                .withDescription("Element name in which each document is "
                        + "found")
                .create(AGGREGATE_RECORD_ELEMENT);
            options.addOption(aggregateRecordElement);
            Option aggregateRecordNamespace = OptionBuilder
                .withArgName("namespace")
                .hasArg()
                .withDescription("Element namespace in which each document "
                        + "is found")
                .create(AGGREGATE_RECORD_NAMESPACE);
            options.addOption(aggregateRecordNamespace);
            Option aggregateUriId = OptionBuilder
                .withArgName("QName")
                .hasArg()
                .withDescription("Name of the first element or attribute "
                        + "within a record element to be used as document URI."
                        + " If omitted, a sequence id will be generated to "
                        + " form the document URI.")                      
                .create(AGGREGATE_URI_ID);
            options.addOption(aggregateUriId);
            Option inputFileType = OptionBuilder
                .withArgName("type")
                .hasArg()
                .withDescription("Type of input file.  Valid choices are: "
                    + "documents, XML aggregates, delimited text, and export "
                    + "archive.")
                .create(INPUT_FILE_TYPE);
            options.addOption(inputFileType);
            Option inputCompressed = OptionBuilder
                .withArgName("true,false")
                .hasOptionalArg()
                .withDescription("Whether the input data is compressed")
                .create(INPUT_COMPRESSED);
            options.addOption(inputCompressed);
            Option inputCompressionCodec = OptionBuilder
                .withArgName("codec")
                .hasArg()
                .withDescription("Codec used for compression: ZIP, GZIP")
                .create(INPUT_COMPRESSION_CODEC);
            options.addOption(inputCompressionCodec);
            Option documentType = OptionBuilder
                .withArgName("type")
                .hasArg()
                .withDescription("Type of document content. Valid choices: "
                    + "XML, TEXT, BINARY")
                .create(DOCUMENT_TYPE);
            options.addOption(documentType);
            Option delimiter = OptionBuilder
                .withArgName(DELIMITER)
                .hasArg()
                .withDescription("Delimiter for delimited text.")
                .create(DELIMITER);
            options.addOption(delimiter);
            Option delimitedUri = OptionBuilder
                .withArgName("column name")
                .hasArg()
                .withDescription("Delimited uri id for delimited text.")
                .create(DELIMITED_URI_ID);
            options.addOption(delimitedUri);
            Option namespace = OptionBuilder
                .withArgName(NAMESPACE)
                .hasArg()
                .withDescription("Namespace used for output document.")
                .create(NAMESPACE);
            options.addOption(namespace);
            Option outputLanguage = OptionBuilder
                .withArgName("language")
                .hasArg()
                .withDescription("Language name to associate with output "
                        + "documents.")
                .create(OUTPUT_LANGUAGE);
            options.addOption(outputLanguage);
            Option outputCleanDir = OptionBuilder
                .withArgName("true,false")
                .hasOptionalArg()
                .withDescription("Whether to clean dir before output.")
                .create(OUTPUT_CLEANDIR);
            options.addOption(outputCleanDir);
            Option outputDir = OptionBuilder
                .withArgName("directory")
                .hasArg()
                .withDescription("Output directory in MarkLogic.")
                .create(OUTPUT_DIRECTORY);
            options.addOption(outputDir);
            Option outputFilenameCollection = OptionBuilder
                .withArgName("true,false")
                .hasOptionalArg()
                .withDescription("Filename as collection in output.")
                .create(OUTPUT_FILENAME_AS_COLLECTION);
            options.addOption(outputFilenameCollection);
            Option repairLevel = OptionBuilder
                .withArgName("level")
                .hasArg()
                .withDescription("Whether to repair documents to make it well "
                        + "formed or throw error.")
                .create(XML_REPAIR_LEVEL);
            options.addOption(repairLevel);
            Option seqKeyClass = OptionBuilder
                .withArgName("class name")
                .hasArg()
                .withDescription("Name of class to be used as key to read the "
                        + " input SequenceFile")
                .create(INPUT_SEQUENCEFILE_KEY_CLASS);
            options.addOption(seqKeyClass);
            Option seqValueClass = OptionBuilder
                .withArgName("class name")
                .hasArg()
                .withDescription("Name of class to be used as value to read "
                        + "the input SequenceFile")
                .create(INPUT_SEQUENCEFILE_VALUE_CLASS);
            options.addOption(seqValueClass);
            Option seqValueType = OptionBuilder
                .withArgName("value type")
                .hasArg()
                .withDescription("Type of the value data returned by the above"
                        + " class.  Valid choices are: Text, BytesWritable, "
                        + "MarkLogicDocument and Path.")
                .create(INPUT_SEQUENCEFILE_VALUE_TYPE);
            options.addOption(seqValueType);
            Option allowEmptyMeta = OptionBuilder
                .withArgName("true,false")
                .hasOptionalArg()
                .withDescription("Whether to allow empty metadata when "
                        + "importing archive")
                .create(ARCHIVE_METADATA_OPTIONAL);
            options.addOption(allowEmptyMeta);
            Option fastLoad = OptionBuilder
                .withArgName("true,false")
                .hasOptionalArg()
                .withDescription("Whether to use the fast load mode to load "
                        + "content into MarkLogic")
                .create(FAST_LOAD);
            options.addOption(fastLoad);
            Option streaming = OptionBuilder
                .withArgName("true,false")
                .hasOptionalArg()
                .withDescription("Whether to use streaming to output data to"
                        + " MarkLogic")
                .create(STREAMING);
            options.addOption(streaming);
            Option splitAgg = OptionBuilder
                .withArgName("true,false")
                .hasOptionalArg()
                .withDescription("Whether to split up an aggregate XML into "
                        + "multiple chunks for processing")
                .create(AGGREGATE_SPLIT);
            CommandlineOption option = new CommandlineOption(splitAgg);
            option.setHidden(true);
            options.addOption(option);
        }

        @Override
        public Job createJob(Configuration conf, CommandLine cmdline)
                        throws IOException {
            applyConfigOptions(conf, cmdline);

            String inputTypeOption = cmdline.getOptionValue(INPUT_FILE_TYPE,
                            INPUT_FILE_TYPE_DEFAULT);
            InputType type = InputType.forName(inputTypeOption);
            
            // construct a job
            Job job = new Job(conf);
            job.setJarByClass(this.getClass());
            job.setInputFormatClass(type.getInputFormatClass(cmdline, conf));

            job.setMapperClass(type.getMapperClass(cmdline, conf));
            job.setMapOutputKeyClass(DocumentURI.class);
            job.setMapOutputValueClass(type.getOutputValueClass(cmdline, 
                    conf));
            job.setOutputFormatClass(type.getOutputFormatClass(cmdline, conf));

            if (cmdline.hasOption(INPUT_FILE_PATH)) {
                String path = cmdline.getOptionValue(INPUT_FILE_PATH);
                FileInputFormat.setInputPaths(job, path);
            }
            if (cmdline.hasOption(INPUT_FILE_PATTERN)) {
                FileInputFormat.setInputPathFilter(job,
                                DocumentPathFilter.class);
            }
            return job;
        }

        @Override
        public void applyConfigOptions(Configuration conf, CommandLine cmdline) {
            applyCopyConfigOptions(conf, cmdline);
            applyCommonOutputConfigOptions(conf, cmdline);
            
            InputType inputType = getInputType(cmdline);   
            inputType.setDocumentType(cmdline, conf);
            if (Command.isStreaming(cmdline, conf)) {
                conf.setBoolean(CONF_STREAMING, true);
            }

            if (cmdline.hasOption(ARCHIVE_METADATA_OPTIONAL)) {
                String arg = cmdline.getOptionValue(
                        ARCHIVE_METADATA_OPTIONAL);
                if (arg == null || arg.equalsIgnoreCase("true")) {
                    conf.setBoolean(CONF_INPUT_ARCHIVE_METADATA_OPTIONAL,
                                    true);
                } else if (arg.equalsIgnoreCase("false")) {
                    conf.setBoolean(CONF_INPUT_ARCHIVE_METADATA_OPTIONAL,
                                    false);
                } else {
                    LOG.warn("Unrecognized option argument for "
                        + ARCHIVE_METADATA_OPTIONAL + ": "
                        + arg);
                }
            }

            if (cmdline.hasOption(INPUT_COMPRESSION_CODEC)) {
                String codec = cmdline.getOptionValue(INPUT_COMPRESSION_CODEC);
                conf.set(CONF_INPUT_COMPRESSION_CODEC, codec.toUpperCase());
            }
            if (cmdline.hasOption(MAX_SPLIT_SIZE)) {
                String maxSize = cmdline.getOptionValue(MAX_SPLIT_SIZE);
                conf.set(ConfigConstants.CONF_MAX_SPLIT_SIZE, maxSize);
            }
            if (cmdline.hasOption(MIN_SPLIT_SIZE)) {
                String minSize = cmdline.getOptionValue(MIN_SPLIT_SIZE);
                conf.set(CONF_MIN_SPLIT_SIZE, minSize);
            }
            if (cmdline.hasOption(AGGREGATE_URI_ID)) {
                String uriId = cmdline.getOptionValue(AGGREGATE_URI_ID);
                conf.set(CONF_AGGREGATE_URI_ID, uriId);
            }
            if (cmdline.hasOption(AGGREGATE_RECORD_ELEMENT)) {
                String recElem = cmdline.getOptionValue(
                        AGGREGATE_RECORD_ELEMENT);
                conf.set(CONF_AGGREGATE_RECORD_ELEMENT, recElem);
            }
            if (cmdline.hasOption(AGGREGATE_RECORD_NAMESPACE)) {
                String recNs = cmdline.getOptionValue(
                        AGGREGATE_RECORD_NAMESPACE);
                conf.set(CONF_AGGREGATE_RECORD_NAMESPACE, recNs);
            }
            if (cmdline.hasOption(DELIMITER)) {
                String delim = cmdline.getOptionValue(DELIMITER);
                conf.set(CONF_DELIMITER, delim);
            }
            if (cmdline.hasOption(DELIMITED_URI_ID)) {
                String delimId = cmdline.getOptionValue(DELIMITED_URI_ID);
                conf.set(CONF_DELIMITED_URI_ID, delimId);
            }
            if (cmdline.hasOption(OUTPUT_FILENAME_AS_COLLECTION)) {
                String arg = cmdline.getOptionValue(
                        OUTPUT_FILENAME_AS_COLLECTION);
                if (arg == null || arg.equalsIgnoreCase("true")) {
                    conf.setBoolean(CONF_OUTPUT_FILENAME_AS_COLLECTION, true);
                } else {
                    conf.setBoolean(CONF_OUTPUT_FILENAME_AS_COLLECTION, false);
                }
            }
            if (cmdline.hasOption(OUTPUT_DIRECTORY)) {
                String outDir = cmdline.getOptionValue(OUTPUT_DIRECTORY);
                conf.set(MarkLogicConstants.OUTPUT_DIRECTORY, outDir);
            }
            if (cmdline.hasOption(OUTPUT_CLEANDIR)) {
                String arg = cmdline.getOptionValue(OUTPUT_CLEANDIR);
                if (arg == null || arg.equalsIgnoreCase("true")) {
                    conf.setBoolean(MarkLogicConstants.OUTPUT_CLEAN_DIR, true);
                } else if (arg.equalsIgnoreCase("false")) {
                    conf.setBoolean(MarkLogicConstants.OUTPUT_CLEAN_DIR, 
                            false);
                } else {
                    LOG.warn("Unrecognized option argument for "
                                    + OUTPUT_CLEANDIR + ": " + arg);
                }
            }
            String batchSize = cmdline.getOptionValue(BATCH_SIZE);
            if (batchSize != null) {       
                conf.set(MarkLogicConstants.BATCH_SIZE, batchSize);
            }

            String txnSize = cmdline.getOptionValue(TRANSACTION_SIZE);
            if (txnSize != null) {
                conf.set(MarkLogicConstants.TXN_SIZE, txnSize);
            }

            if (cmdline.hasOption(NAMESPACE)) {
                String ns = cmdline.getOptionValue(NAMESPACE);
                conf.set(MarkLogicConstants.OUTPUT_CONTENT_NAMESPACE, ns);
            }
            if (cmdline.hasOption(OUTPUT_LANGUAGE)) {
                String language = cmdline.getOptionValue(OUTPUT_LANGUAGE);
                conf.set(MarkLogicConstants.OUTPUT_CONTENT_LANGUAGE, language);
            }
            if (cmdline.hasOption(INPUT_FILE_PATTERN)) {
                String pattern = cmdline.getOptionValue(INPUT_FILE_PATTERN);
                conf.set(CONF_INPUT_FILE_PATTERN, pattern);
            }
            if (cmdline.hasOption(USERNAME)) {
                String username = cmdline.getOptionValue(USERNAME);
                conf.set(MarkLogicConstants.OUTPUT_USERNAME, username);
            }
            if (cmdline.hasOption(PASSWORD)) {
                String password = cmdline.getOptionValue(PASSWORD);
                conf.set(MarkLogicConstants.OUTPUT_PASSWORD, password);
            }
            if (cmdline.hasOption(HOST)) {
                String host = cmdline.getOptionValue(HOST);
                conf.set(MarkLogicConstants.OUTPUT_HOST, host);
            }
            if (cmdline.hasOption(PORT)) {
                String port = cmdline.getOptionValue(PORT);
                conf.set(MarkLogicConstants.OUTPUT_PORT, port);
            }

            String repairLevel = cmdline.getOptionValue(XML_REPAIR_LEVEL,
                    MarkLogicConstants.DEFAULT_OUTPUT_XML_REPAIR_LEVEL);
            conf.set(MarkLogicConstants.OUTPUT_XML_REPAIR_LEVEL, 
                    repairLevel.toUpperCase());
            if (cmdline.hasOption(INPUT_SEQUENCEFILE_KEY_CLASS)) {
                String keyClass = cmdline.getOptionValue(
                        INPUT_SEQUENCEFILE_KEY_CLASS);
                conf.set(CONF_INPUT_SEQUENCEFILE_KEY_CLASS, keyClass);
            }
            if (cmdline.hasOption(INPUT_SEQUENCEFILE_VALUE_CLASS)) {
                String valueClass = cmdline.getOptionValue(
                        INPUT_SEQUENCEFILE_VALUE_CLASS);
                conf.set(CONF_INPUT_SEQUENCEFILE_VALUE_CLASS, valueClass);
            }
            if (cmdline.hasOption(INPUT_SEQUENCEFILE_VALUE_TYPE)) {
                String valueType = cmdline.getOptionValue(
                                INPUT_SEQUENCEFILE_VALUE_TYPE,
                                DEFAULT_SEQUENCEFILE_VALUE_TYPE);
                conf.set(CONF_INPUT_SEQUENCEFILE_VALUE_TYPE, 
                        valueType.toUpperCase());
                if (valueType.equalsIgnoreCase(
                        SequenceFileValueType.BYTESWRITABLE.toString())) {
                    conf.set(MarkLogicConstants.CONTENT_TYPE,
                                    ContentType.BINARY.toString());
                }
            } else if (conf.get(CONF_INPUT_SEQUENCEFILE_VALUE_TYPE) == null) {
                conf.set(CONF_INPUT_SEQUENCEFILE_VALUE_TYPE,
                                DEFAULT_SEQUENCEFILE_VALUE_TYPE);
            }
            if (cmdline.hasOption(INPUT_FILE_TYPE)) {
                String fileType = cmdline.getOptionValue(INPUT_FILE_TYPE);
                if (fileType.equalsIgnoreCase(InputType.ARCHIVE.toString())) {
                    conf.set(MarkLogicConstants.CONTENT_TYPE,
                                    ContentType.UNKNOWN.toString());
                }
            }
            if (cmdline.hasOption(FAST_LOAD)) {
                String arg = cmdline.getOptionValue(FAST_LOAD);
                if (arg == null || arg.equalsIgnoreCase("true")) {
                    conf.setBoolean(MarkLogicConstants.OUTPUT_FAST_LOAD, true);
                } else if (arg.equalsIgnoreCase("false")) {
                    conf.setBoolean(MarkLogicConstants.OUTPUT_FAST_LOAD, false);
                } else {
                    LOG.warn("Unrecognized option argument for " + FAST_LOAD
                                    + ": " + arg);
                }
            }
            if (cmdline.hasOption(AGGREGATE_SPLIT)) {
                String arg = cmdline.getOptionValue(AGGREGATE_SPLIT);
                if (arg == null || arg.equalsIgnoreCase("false")) {
                    conf.setBoolean(CONF_AGGREGATE_SPLIT, false);
                } else if (arg.equalsIgnoreCase("true")) {
                    conf.setBoolean(CONF_AGGREGATE_SPLIT, true);
                } else {
                    LOG.warn("Unrecognized option argument for " + AGGREGATE_SPLIT
                                    + ": " + arg);
                }
            }
        } 
    },
    EXPORT {
        @Override
        public void configOptions(Options options) {
            configCommonOptions(options);
            configConnectionId(options);
            configCopyOptions(options);
            configDocumentFilteringOptions(options);

            Option outputType = OptionBuilder
                .withArgName("type")
                .hasArg()
                .withDescription("export output type")
                .create(OUTPUT_TYPE);
            options.addOption(outputType);
            Option outputFilePath = OptionBuilder
                .withArgName("path")
                .hasArg()
                .withDescription("export output file path")
                .create(OUTPUT_FILE_PATH);
            options.addOption(outputFilePath);
            Option exportCompress = OptionBuilder
                .withArgName("true,false")
                .hasOptionalArg()
                .withDescription("Whether to compress the output document")
                .create(OUTPUT_COMPRESS);
            options.addOption(exportCompress);
            Option exportIndented = OptionBuilder
                .withArgName("true,false")
                .hasArg()
                .withDescription("Whether to format data with indentation")
                .create(OUTPUT_INDENTED);
            options.addOption(exportIndented);
        }

        @Override
        public Job createJob(Configuration conf, CommandLine cmdline)
                        throws IOException {
            applyConfigOptions(conf, cmdline);

            String type = conf.get(CONF_OUTPUT_TYPE, DEFAULT_OUTPUT_TYPE);
            ExportOutputType outputType = ExportOutputType.valueOf(
                            type.toUpperCase());

            if (outputType.equals(ExportOutputType.DOCUMENT)) {
                conf.set(MarkLogicConstants.INPUT_MODE,
                                MarkLogicConstants.BASIC_MODE);
                conf.set(MarkLogicConstants.INPUT_VALUE_CLASS,
                                MarkLogicDocument.class.getCanonicalName());
            } else if (outputType.equals(ExportOutputType.ARCHIVE)) {
                // use basic mode for getSplits; use advanced mode(hardcoded)
                // for record reader
                conf.set(MarkLogicConstants.INPUT_MODE,
                                MarkLogicConstants.BASIC_MODE);
            }
            
            // construct a job
            Job job = new Job(conf);
            job.setJarByClass(this.getClass());
            job.setInputFormatClass(outputType.getInputFormatClass());

            job.setMapperClass(DocumentMapper.class);
            job.setMapOutputKeyClass(DocumentURI.class);
            job.setMapOutputValueClass(MarkLogicDocument.class);
            job.setOutputFormatClass(
                            outputType.getOutputFormatClass(cmdline));
            job.setOutputKeyClass(DocumentURI.class);
            String path = conf.get(ConfigConstants.CONF_OUTPUT_FILEPATH);
            // directory should not exist and it will be created
            FileOutputFormat.setOutputPath(job, new Path(path));
            return job;
        }

        @Override
        public void applyConfigOptions(Configuration conf, CommandLine cmdline) {
            applyCopyConfigOptions(conf, cmdline);
            applyDocumentFilteringConfigOptions(conf, cmdline);

            if (cmdline.hasOption(OUTPUT_TYPE)) {
                String outputType = cmdline.getOptionValue(OUTPUT_TYPE);
                conf.set(CONF_OUTPUT_TYPE, outputType);
            }
            if (cmdline.hasOption(OUTPUT_FILE_PATH)) {
                String path = cmdline.getOptionValue(OUTPUT_FILE_PATH);
                conf.set(ConfigConstants.CONF_OUTPUT_FILEPATH, path);
            }
            if (cmdline.hasOption(OUTPUT_INDENTED)) {
                String isIndented = cmdline.getOptionValue(OUTPUT_INDENTED);
                // check value validity
                Indentation indent = Indentation.forName(isIndented);
                conf.set(MarkLogicConstants.INDENTED, indent.name());
            }
            if (cmdline.hasOption(HOST)) {
                String host = cmdline.getOptionValue(HOST);
                conf.set(MarkLogicConstants.INPUT_HOST, host);
            }
            if (cmdline.hasOption(PORT)) {
                String port = cmdline.getOptionValue(PORT);
                conf.set(MarkLogicConstants.INPUT_PORT, port);
            }
            if (cmdline.hasOption(USERNAME)) {
                String user = cmdline.getOptionValue(USERNAME);
                conf.set(MarkLogicConstants.INPUT_USERNAME, user);
            }
            if (cmdline.hasOption(PASSWORD)) {
                String pswd = cmdline.getOptionValue(PASSWORD);
                conf.set(MarkLogicConstants.INPUT_PASSWORD, pswd);
            }
            String maxSize = cmdline.getOptionValue(MAX_SPLIT_SIZE, 
                DEFAULT_MAX_SPLIT_SIZE);
            conf.set(MarkLogicConstants.MAX_SPLIT_SIZE, maxSize);
        }
    },
    COPY {
        @Override
        public void configOptions(Options options) {
            configCommonOptions(options);
            configCopyOptions(options);
            configCommonOutputOptions(options);
            configDocumentFilteringOptions(options);
            configBatchTxn(options);

            Option inputUsername = OptionBuilder
                .withArgName("username")
                .hasArg()
                .withDescription("User name of the input MarkLogic Server")
                .create(INPUT_USERNAME);
            inputUsername.setRequired(true);
            options.addOption(inputUsername);
            Option inputPassword = OptionBuilder
                .withArgName("password")
                .hasArg()
                .withDescription("Password of the input MarkLogic Server")
                .create(INPUT_PASSWORD);
            inputPassword.setRequired(true);
            options.addOption(inputPassword);
            Option inputHost = OptionBuilder
                .withArgName("host")
                .hasArg()
                .withDescription("Host of the input MarkLogic Server")
                .create(INPUT_HOST);
            inputHost.setRequired(true);
            options.addOption(inputHost);
            Option inputPort = OptionBuilder
                .withArgName("port")
                .hasArg()
                .withDescription("Port of the input MarkLogic Server")
                .create(INPUT_PORT);
            inputPort.setRequired(true);
            options.addOption(inputPort);
            Option outputUsername = OptionBuilder
                .withArgName("username")
                .hasArg()
                .withDescription("User Name of the output MarkLogic Server")
                .create(OUTPUT_USERNAME);
            outputUsername.setRequired(true);
            options.addOption(outputUsername);
            Option outputPassword = OptionBuilder
                .withArgName("password")
                .hasArg()
                .withDescription("Password of the output MarkLogic Server")
                .create(OUTPUT_PASSWORD);
            outputPassword.setRequired(true);
            options.addOption(outputPassword);
            Option outputHost = OptionBuilder
                .withArgName("host")
                .hasArg()
                .withDescription("Host of the output MarkLogic Server")
                .create(OUTPUT_HOST);
            outputHost.setRequired(true);
            options.addOption(outputHost);
            Option outputPort = OptionBuilder
                 .withArgName("port")
                 .hasArg()
                 .withDescription("Port of the output MarkLogic Server")
                 .create(OUTPUT_PORT);
            outputPort.setRequired(true);
            options.addOption(outputPort);
            Option fastLoad = OptionBuilder
                 .withArgName("true,false")
                 .hasOptionalArg()
                 .withDescription("Whether to use the fast load mode to load "
                         + "content into MarkLogic")
                 .create(FAST_LOAD);
            options.addOption(fastLoad);
            Option outputDir = OptionBuilder
                .withArgName("directory")
                .hasArg()
                .withDescription("Output directory in MarkLogic.")
                .create(OUTPUT_DIRECTORY);
            options.addOption(outputDir);
        }

        @Override
        public Job createJob(Configuration conf, CommandLine cmdline)
                        throws IOException {
            applyConfigOptions(conf, cmdline);

            Job job = new Job(conf);
            job.setJarByClass(this.getClass());
            job.setInputFormatClass(DatabaseContentInputFormat.class);
            job.setMapperClass(DocumentMapper.class);
            job.setMapOutputKeyClass(DocumentURI.class);
            job.setMapOutputValueClass(MarkLogicDocument.class);
            job.setOutputFormatClass(DatabaseContentOutputFormat.class);
            job.setOutputKeyClass(DocumentURI.class);
            return job;
        }

        @Override
        public void applyConfigOptions(Configuration conf, CommandLine cmdline) {
            applyCopyConfigOptions(conf, cmdline);
            applyDocumentFilteringConfigOptions(conf, cmdline);
            applyCommonOutputConfigOptions(conf, cmdline);

            if (cmdline.hasOption(OUTPUT_USERNAME)) {
                String username = cmdline.getOptionValue(OUTPUT_USERNAME);
                conf.set(MarkLogicConstants.OUTPUT_USERNAME, username);
            }
            if (cmdline.hasOption(OUTPUT_PASSWORD)) {
                String password = cmdline.getOptionValue(OUTPUT_PASSWORD);
                conf.set(MarkLogicConstants.OUTPUT_PASSWORD, password);
            }
            if (cmdline.hasOption(OUTPUT_HOST)) {
                String host = cmdline.getOptionValue(OUTPUT_HOST);
                conf.set(MarkLogicConstants.OUTPUT_HOST, host);
            }
            if (cmdline.hasOption(OUTPUT_PORT)) {
                String port = cmdline.getOptionValue(OUTPUT_PORT);
                conf.set(MarkLogicConstants.OUTPUT_PORT, port);
            }

            if (cmdline.hasOption(INPUT_USERNAME)) {
                String username = cmdline.getOptionValue(INPUT_USERNAME);
                conf.set(MarkLogicConstants.INPUT_USERNAME, username);
            }
            if (cmdline.hasOption(INPUT_PASSWORD)) {
                String password = cmdline.getOptionValue(INPUT_PASSWORD);
                conf.set(MarkLogicConstants.INPUT_PASSWORD, password);
            }
            if (cmdline.hasOption(INPUT_HOST)) {
                String host = cmdline.getOptionValue(INPUT_HOST);
                conf.set(MarkLogicConstants.INPUT_HOST, host);
            }
            if (cmdline.hasOption(INPUT_PORT)) {
                String port = cmdline.getOptionValue(INPUT_PORT);
                conf.set(MarkLogicConstants.INPUT_PORT, port);
            }
            String maxSize = cmdline.getOptionValue(MAX_SPLIT_SIZE, 
                    DEFAULT_MAX_SPLIT_SIZE);
            conf.set(MarkLogicConstants.MAX_SPLIT_SIZE, maxSize);
            if (cmdline.hasOption(FAST_LOAD)) {
                String arg = cmdline.getOptionValue(FAST_LOAD);
                if (arg == null || arg.equalsIgnoreCase("true")) {
                    conf.setBoolean(MarkLogicConstants.OUTPUT_FAST_LOAD, true);
                } else {
                    conf.setBoolean(MarkLogicConstants.OUTPUT_FAST_LOAD, 
                            false);
                }
            }
            if (cmdline.hasOption(OUTPUT_DIRECTORY)) {
                String outDir = cmdline.getOptionValue(OUTPUT_DIRECTORY);
                conf.set(MarkLogicConstants.OUTPUT_DIRECTORY, outDir);
            }
            String batchSize = cmdline.getOptionValue(BATCH_SIZE);
            if (batchSize != null) {       
                conf.set(MarkLogicConstants.BATCH_SIZE, batchSize);
            }

            String txnSize = cmdline.getOptionValue(TRANSACTION_SIZE);
            if (txnSize != null) {
                conf.set(MarkLogicConstants.TXN_SIZE, txnSize);
            }
        }
    };

    public static final Log LOG = LogFactory.getLog(LocalJobRunner.class);

    public static Command forName(String cmd) {
        if (cmd.equalsIgnoreCase(IMPORT.name())) {
            return IMPORT;
        } else if (cmd.equalsIgnoreCase(EXPORT.name())) {
            return EXPORT;
        } else if (cmd.equalsIgnoreCase(COPY.name())) {
            return COPY;
        } else {
            throw new IllegalArgumentException("Unknown command: " + cmd);
        }
    }
    
    protected static boolean isInputCompressed(CommandLine cmdline) {
        if (cmdline.hasOption(INPUT_COMPRESSED)) {
            String isCompress = cmdline.getOptionValue(INPUT_COMPRESSED);
            if (isCompress == null || isCompress.equalsIgnoreCase("true")) {
                return true;
            }
        }
        return false;
    }
    
    protected static boolean isOutputCompressed(CommandLine cmdline) {
        if (cmdline.hasOption(OUTPUT_COMPRESS)) {
            String isCompress = cmdline.getOptionValue(OUTPUT_COMPRESS);
            if (isCompress == null || isCompress.equalsIgnoreCase("true")) {
                return true;
            }
        }
        return false;
    }
    
    protected static boolean isStreaming(CommandLine cmdline, 
            Configuration conf) {
        if (conf.get(CONF_STREAMING) != null) {
            return conf.getBoolean(CONF_STREAMING, false);
        }
        String arg = null;
        if (cmdline.hasOption(STREAMING)) {
            arg = cmdline.getOptionValue(STREAMING);
            if (arg == null || arg.equalsIgnoreCase("true")) {
                InputType inputType = getInputType(cmdline);
                if (inputType != InputType.DOCUMENTS) {
                    LOG.warn("Streaming option is not applicable to input " +
                            "type " + inputType);
                }
                return true;
            } 
        } else if (isMixedType(cmdline)) {
            if ("false".equalsIgnoreCase(arg)) {
                LOG.warn("Streaming cannot be turned off when input " +
                    "contain mixed-typed documents.");
            }
            return true;
        }
        return false;
    }
    
    protected static boolean isMixedType(CommandLine cmdline) {
        String type = cmdline.getOptionValue(DOCUMENT_TYPE);
        InputType inputType = getInputType(cmdline);
        if (type != null && !type.equalsIgnoreCase(ContentType.XML.name()) &&
            inputType != InputType.DOCUMENTS) {
            LOG.warn("Document type " + type + 
                    " is not applicable to input type " + inputType);
        } else if (type == null || // default is mixed
                type.equalsIgnoreCase(ContentType.MIXED.name())) {
            return true;
        }
        return false;
    }

    /**
     * Add supported config options.
     * 
     * @param options
     */
    public abstract void configOptions(Options options);

    /**
     * Create a job based on Hadoop configuration and options.
     * 
     * @param conf
     *            Hadoop configuration
     * @param options
     *            options
     * @return a Hadoop job
     * @throws Exception
     */
    public abstract Job createJob(Configuration conf, CommandLine cmdline)
                    throws IOException;

    /**
     * Apply config options set from the command-line to the configuration.
     * 
     * @param conf
     *            configuration
     * @param cmdline
     *            command line options
     */
    public abstract void applyConfigOptions(Configuration conf,
                    CommandLine cmdline);

    @SuppressWarnings("deprecation")
    static void configCommonOptions(Options options) {
        Option mode = OptionBuilder
            .withArgName(MODE)
            .hasArg()
            .withDescription("Whether to run in local or distributed mode.")
            .create(MODE);
        options.addOption(mode);
        Option hadoopConfDir = OptionBuilder
            .withArgName("directory")
            .hasArg()
            .withDescription("Override $HADOOP_CONF_DIR")
            .create(HADOOP_CONF_DIR);
        options.addOption(hadoopConfDir);
        Option hadoopHome = OptionBuilder
            .withArgName(HADOOP_HOME)
            .hasArg()
            .withDescription("Override $HADOOP_HOME")
            .create(HADOOP_HOME);
        CommandlineOption opt = new CommandlineOption(hadoopHome);
        opt.setHidden(true);
        options.addOption(opt);
        Option threadCount = OptionBuilder
            .withArgName("count")
            .hasArg()
            .withDescription("Number of threads")
            .create(THREAD_COUNT);
        options.addOption(threadCount);
        Option maxSplitSize = OptionBuilder
            .withArgName("number")
            .hasArg()
            .withDescription("Maximum number of MarkLogic documents per each "
                    + "input split in export or copy, or maximum number of " 
                    + "bytes in file per each split in import")
            .create(MAX_SPLIT_SIZE);
        options.addOption(maxSplitSize);
        Option minSplitSize = OptionBuilder
            .withArgName("number")
            .hasArg()
            .withDescription("Minimum number of bytes in file per each split "
                    + "in import")
            .create(MIN_SPLIT_SIZE);
        options.addOption(minSplitSize);
    }

    static void configCommonOutputOptions(Options options) {
        Option outputUriReplace = OptionBuilder
            .withArgName("list")
            .hasArg()
            .withDescription("Comma separated list of regex pattern and "
                    + "string pairs, 1st to match a uri segment, 2nd the "
                    + "string to replace with, with the 2nd one in ''")
            .create(OUTPUT_URI_REPLACE);
        options.addOption(outputUriReplace);
        Option outputUriPrefix = OptionBuilder
            .withArgName("prefix")
            .hasArg()
            .withDescription("String to prepend to all document URIs")
            .create(OUTPUT_URI_PREFIX);
        options.addOption(outputUriPrefix);
        Option outputUriSuffix = OptionBuilder
            .withArgName("suffix")
            .hasArg()
            .withDescription("String to append to all document URIs")
            .create(OUTPUT_URI_SUFFIX);
        options.addOption(outputUriSuffix);

        Option outputCollections = OptionBuilder
            .withArgName("collections")
            .hasArg()
            .withDescription("Comma separated list of collection to be applied"
                    + " to output documents")
            .create(OUTPUT_COLLECTIONS);
        options.addOption(outputCollections);
        Option outputPermissions = OptionBuilder
            .withArgName("permissions")
            .hasArg()
            .withDescription("Comma separated list of user-privilege pairs to "
                    + "be applied to output documents")
            .create(OUTPUT_PERMISSIONS);
        options.addOption(outputPermissions);
        Option outputQuantity = OptionBuilder
            .withArgName("quality")
            .hasArg()
            .withDescription("Quality to be applied to output documents")
            .create(OUTPUT_QUALITY);
        options.addOption(outputQuantity);
    }

    static void configConnectionId(Options options) {
        Option username = OptionBuilder
            .withArgName(USERNAME)
            .hasArg()
            .withDescription("User name of MarkLogic Server")
            .create(USERNAME);
        username.setRequired(true);
        options.addOption(username);
        Option password = OptionBuilder
            .withArgName(PASSWORD)
            .hasArg()
            .withDescription("Password of MarkLogic Server")
            .create(PASSWORD);
        password.setRequired(true);
        options.addOption(password);
        Option host = OptionBuilder
            .withArgName(HOST)
            .hasArg()
            .withDescription("Host of MarkLogic Server")
            .create(HOST);
        host.setRequired(true);
        options.addOption(host);
        Option port = OptionBuilder
            .withArgName(PORT)
            .hasArg()
            .withDescription("Port of MarkLogic Server")
            .create(PORT);
        port.setRequired(true);
        options.addOption(port);
    }

    static void configCopyOptions(Options options) {
        Option cpcol = OptionBuilder
            .withArgName("true,false")
            .hasOptionalArg()
            .withDescription("Whether to copy document collections from source"
                   + " to destination")
            .create(COPY_COLLECTIONS);
        options.addOption(cpcol);
        Option cppm = OptionBuilder
            .withArgName("true,false")
            .hasOptionalArg()
            .withDescription("Whether to copy document permissions from source"
                   + " to destination")
            .create(COPY_PERMISSIONS);
        options.addOption(cppm);
        Option cppt = OptionBuilder
            .withArgName("true,false")
            .hasOptionalArg()
            .withDescription("Whether to copy document properties from source"
                   + " to destination")
            .create(COPY_PROPERTIES);
        options.addOption(cppt);
        Option cpqt = OptionBuilder
            .withArgName("true,false")
            .hasOptionalArg()
            .withDescription("Whether to copy document quality from source"
                   + " to destination")
            .create(COPY_QUALITY);
        options.addOption(cpqt);
    }

    static void configBatchTxn(Options options) {
        Option batchSize = OptionBuilder
            .withArgName("number")
            .hasArg()
            .withDescription("Number of document in one request")
            .create(BATCH_SIZE);
        options.addOption(batchSize);
        Option txnSize = OptionBuilder
            .withArgName("number")
            .hasArg()
            .withDescription("Number of requests in one transaction")
            .create(TRANSACTION_SIZE);
        options.addOption(txnSize);
    }

    static void configDocumentFilteringOptions(Options options) {
        Option filter = OptionBuilder
            .withArgName("expression")
            .hasArg()
            .withDescription("Path expression used to retrieve records from "
                   + "MarkLogic Server")
            .create(DOCUMENT_FILTER);
        options.addOption(filter);
        Option ns = OptionBuilder
            .withArgName("namespace")
            .hasArg()
            .withDescription("Comma-separated alias-URI pairs, used with "
                  + "document_filter")
            .create(DOCUMENT_NAMESPACE);
        options.addOption(ns);
    }

    static void applyCopyConfigOptions(Configuration conf, CommandLine cmdline) {
        if (cmdline.hasOption(COPY_COLLECTIONS)) {
            String arg = cmdline.getOptionValue(COPY_COLLECTIONS);
            if (arg == null || arg.equalsIgnoreCase("true")) {
                conf.setBoolean(CONF_COPY_COLLECTIONS, true);
            } else if (arg.equalsIgnoreCase("false")) {
                conf.setBoolean(CONF_COPY_COLLECTIONS, false);
            } else {
                LOG.warn("Unrecognized option argument for " + COPY_COLLECTIONS
                                + ": " + arg);
                conf.set(CONF_COPY_COLLECTIONS, DEFAULT_COPY_COLLECTIONS);
            }
        } else {
            conf.set(CONF_COPY_COLLECTIONS, DEFAULT_COPY_COLLECTIONS);
        }
        if (cmdline.hasOption(COPY_PERMISSIONS)) {
            String arg = cmdline.getOptionValue(COPY_PERMISSIONS);
            if (arg == null || arg.equalsIgnoreCase("true")) {
                conf.setBoolean(CONF_COPY_PERMISSIONS, true);
            } else if (arg.equalsIgnoreCase("false")) {
                conf.setBoolean(CONF_COPY_PERMISSIONS, false);
            } else {
                LOG.warn("Unrecognized option argument for " + COPY_PERMISSIONS
                                + ": " + arg);
                conf.set(CONF_COPY_PERMISSIONS, DEFAULT_COPY_PERMISSIONS);
            }
        } else {
            conf.set(CONF_COPY_PERMISSIONS, DEFAULT_COPY_PERMISSIONS);
        }
        if (cmdline.hasOption(COPY_PROPERTIES)) {
            String arg = cmdline.getOptionValue(COPY_PROPERTIES);
            if (arg == null || arg.equalsIgnoreCase("true")) {
                conf.setBoolean(CONF_COPY_PROPERTIES, true);
            } else {
                conf.setBoolean(CONF_COPY_PROPERTIES, false);
            }
        } else {
            conf.set(CONF_COPY_PROPERTIES, DEFAULT_COPY_PROPERTIES);
        }
        if (cmdline.hasOption(COPY_QUALITY)) {
            String arg = cmdline.getOptionValue(COPY_QUALITY);
            if (arg == null || arg.equalsIgnoreCase("true")) {
                conf.setBoolean(CONF_COPY_QUALITY, true);
            } else if (arg.equalsIgnoreCase("false")) {
                conf.setBoolean(CONF_COPY_QUALITY, false);
            } else {
                LOG.warn("Unrecognized option argument for " + COPY_QUALITY
                                + ": " + arg);
                conf.set(CONF_COPY_QUALITY, DEFAULT_COPY_QUALITY);
            }
        } else {
            conf.set(CONF_COPY_QUALITY, DEFAULT_COPY_QUALITY);
        }
    }

    static void applyDocumentFilteringConfigOptions(Configuration conf,
                    CommandLine cmdline) {
        String c = cmdline.getOptionValue(DOCUMENT_FILTER,
                        DEFAULT_DOCUMENT_FILTER);
        conf.set(MarkLogicConstants.DOCUMENT_SELECTOR, c);
        if (cmdline.hasOption(DOCUMENT_NAMESPACE)) {
            String ns = cmdline.getOptionValue(DOCUMENT_NAMESPACE);
            conf.set(MarkLogicConstants.PATH_NAMESPACE, ns);
        }
    }

    static void applyCommonOutputConfigOptions(Configuration conf,
                    CommandLine cmdline) {

        if (cmdline.hasOption(OUTPUT_URI_REPLACE)) {
            String uriReplace = cmdline.getOptionValue(OUTPUT_URI_REPLACE);
            if (uriReplace == null) {
                LOG.error(OUTPUT_URI_REPLACE + " is not configured correctly.");
            } else {
                conf.setStrings(CONF_OUTPUT_URI_REPLACE, uriReplace);
            }
        }
        if (cmdline.hasOption(OUTPUT_URI_PREFIX)) {
            String outPrefix = cmdline.getOptionValue(OUTPUT_URI_PREFIX);
            conf.set(CONF_OUTPUT_URI_PREFIX, outPrefix);
        }
        if (cmdline.hasOption(OUTPUT_URI_SUFFIX)) {
            String outSuffix = cmdline.getOptionValue(OUTPUT_URI_SUFFIX);
            conf.set(CONF_OUTPUT_URI_SUFFIX, outSuffix);
        }

        if (cmdline.hasOption(OUTPUT_COLLECTIONS)) {
            String collectionsString = cmdline.getOptionValue(
                    OUTPUT_COLLECTIONS);
            conf.set(MarkLogicConstants.OUTPUT_COLLECTION, collectionsString);
        }
        if (cmdline.hasOption(OUTPUT_PERMISSIONS)) {
            String permissionString = cmdline.getOptionValue(
                    OUTPUT_PERMISSIONS);
            conf.set(MarkLogicConstants.OUTPUT_PERMISSION, permissionString);
        }
        if (cmdline.hasOption(OUTPUT_QUALITY)) {
            String quantity = cmdline.getOptionValue(OUTPUT_QUALITY);
            conf.set(MarkLogicConstants.OUTPUT_QUALITY, quantity);
        }
    }
    
    static InputType getInputType(CommandLine cmdline) {
        String inputTypeOption = cmdline.getOptionValue(INPUT_FILE_TYPE,
                INPUT_FILE_TYPE_DEFAULT);
        return InputType.forName(inputTypeOption);
    }

    public void printUsage(Command cmd, Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(HelpFormatter.DEFAULT_WIDTH, 
                cmd.name(), null, options, null, true);
    }
}
