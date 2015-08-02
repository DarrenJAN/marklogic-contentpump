/*
 * Copyright 2003-2015 MarkLogic Corporation
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
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import com.marklogic.contentpump.utilities.DocBuilder;
import com.marklogic.contentpump.utilities.EncodingUtil;
import com.marklogic.contentpump.utilities.FileIterator;
import com.marklogic.contentpump.utilities.IdGenerator;
import com.marklogic.contentpump.utilities.JSONDocBuilder;
import com.marklogic.contentpump.utilities.XMLDocBuilder;
import com.marklogic.mapreduce.MarkLogicConstants;

/**
 * Reader for DelimitedTextInputFormat.
 * 
 * @author ali
 *
 * @param <VALUEIN>
 */
public class DelimitedTextReader<VALUEIN> extends
    ImportRecordReader<VALUEIN> {
    public static final Log LOG = LogFactory.getLog(DelimitedTextReader.class);
    public static final char encapsulator = '"';
    /**
     * header of delimited text
     */
    protected String[] fields;
    protected char delimiter;
    protected CSVParser parser;
    protected InputStreamReader instream;
    protected FSDataInputStream fileIn;
    protected boolean hasNext = true;
    protected String uriName; // the column name used for URI
    protected long fileLen = Long.MAX_VALUE;
    protected long bytesRead;
    protected boolean generateId;
    protected IdGenerator idGen;
    protected int uriId = -1; // the column index used for URI
    protected boolean compressed;
    protected DocBuilder docBuilder;
    
    @Override
    public void close() throws IOException {
        if (instream != null) {
            instream.close();
        }
    }

    @Override
    public float getProgress() throws IOException, InterruptedException {
        return bytesRead/fileLen;
    }

    @Override
    public void initialize(InputSplit inSplit, TaskAttemptContext context)
        throws IOException, InterruptedException {
        initConfig(context);
        initDocType();
        initDelimConf();
        file = ((FileSplit) inSplit).getPath();
        fs = file.getFileSystem(context.getConfiguration());
        FileStatus status = fs.getFileStatus(file);
        if(status.isDirectory()) {
            iterator = new FileIterator((FileSplit)inSplit, context);
            inSplit = iterator.next();
        }
        initParser(inSplit);
    }
    
    protected void initParser(InputSplit inSplit) throws IOException,
        InterruptedException {
        file = ((FileSplit) inSplit).getPath();
        configFileNameAsCollection(conf, file);

        fileIn = fs.open(file);
        instream = new InputStreamReader(fileIn, encoding);

        bytesRead = 0;
        fileLen = inSplit.getLength();
        if (uriName == null) {
            generateId = conf.getBoolean(CONF_DELIMITED_GENERATE_URI, false);
            if (generateId) {
                idGen = new IdGenerator(file.toUri().getPath() + "-"
                    + ((FileSplit) inSplit).getStart());
            } else {
                uriId = 0;
            }
        }
        parser = new CSVParser(instream, new CSVStrategy(delimiter,
            encapsulator, CSVStrategy.COMMENTS_DISABLED,
            CSVStrategy.ESCAPE_DISABLED, true, true, false, true));
    }

    protected void initDelimConf() {
        String delimStr = conf.get(ConfigConstants.CONF_DELIMITER,
                ConfigConstants.DEFAULT_DELIMITER);
        if (delimStr.length() == 1) {
            delimiter = delimStr.charAt(0);
        } else {  
            throw new UnsupportedOperationException("Invalid delimiter: " +
                    delimStr);
        }
        uriName = conf.get(ConfigConstants.CONF_DELIMITED_URI_ID, null);
        docBuilder.init(conf);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean nextKeyValue() throws IOException, InterruptedException {
        if (parser == null) {
            return false;
        }
        try {
            String[] values = parser.getLine();
            if (values == null) {
                if(compressed) {
                    bytesRead = fileLen;
                    return false;
                } else { 
                    if (iterator != null && iterator.hasNext()) {
                        close();
                        initParser(iterator.next());
                        return nextKeyValue();
                    } else {
                        bytesRead = fileLen;
                        return false;
                    }
                }
            }
            if (fields == null) {
                fields = values;
                if (Charset.defaultCharset().equals(Charset.forName("UTF-8"))) {
                    EncodingUtil.handleBOMUTF8(fields, 0);
                }
                boolean found = generateId || uriId == 0;
                for (int i = 0; i < fields.length && !found; i++) {
                    // skip empty column in header generated by trailing
                    // delimiter
                    if(fields[i].trim().equals("")) continue;
                    if (fields[i].equals(uriName)) {
                        uriId = i;
                        found = true;
                        break;
                    }
                }
                if (found == false) {
                    // idname doesn't match any columns
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Header: " + convertToLine(fields));
                    }
                    throw new IOException("Delimited_uri_id " + uriName
                        + " is not found.");
                }
                docBuilder.configFields(conf, fields);
                values = parser.getLine();
                if (values == null) {
                    if(compressed) {
                        bytesRead = fileLen;
                        return false;
                    } else { 
                        if (iterator != null && iterator.hasNext()) {
                            close();
                            initParser(iterator.next());
                            return nextKeyValue();
                        } else {
                            bytesRead = fileLen;
                            return false;
                        }
                    }
                }
            }
            int line = parser.getLineNumber();
            if (values.length != fields.length) {
                setSkipKey(line, 0, 
                        "number of fields do not match number of columns");
                return true;
            }  
            docBuilder.newDoc();           
            for (int i = 0; i < fields.length; i++) {
                //skip the empty column in header
                if(fields[i].trim().equals("")) {
                    continue;
                }
                if (!generateId && uriId == i) {
                    if (setKey(values[i], line, 0, true)) {
                        return true;
                    }
                }
                docBuilder.put(fields[i], values[i]);
            }
            docBuilder.build();
            if (generateId &&
                setKey(idGen.incrementAndGet(), line, 0, true)) {
                return true;
            }
            if (value instanceof Text) {
                ((Text)value).set(docBuilder.getDoc());
            } else {
                ((Text)((ContentWithFileNameWritable<VALUEIN>)
                        value).getValue()).set(docBuilder.getDoc());
            }
        } catch (IOException ex) {
            if (ex.getMessage().contains(
                "invalid char between encapsulated token end delimiter")) {
                setSkipKey(parser.getLineNumber(), 0, ex.getMessage());
            } else {
                throw ex;
            }
        }
        return true;
    }
    
    protected String convertToLine(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (String s : values) {
            sb.append(s);
            sb.append(delimiter);
        }
        return sb.substring(0, sb.length() - 1);
    }
 
    protected void initDocType() {
        // CONTENT_TYPE validation is in Command.java: applyConfigOptions. 
        // We can assume here that the value in conf is always valid.
        String docType = conf.get(MarkLogicConstants.CONTENT_TYPE,
            MarkLogicConstants.DEFAULT_CONTENT_TYPE);
        
        if (docType.equals("XML")) {
            docBuilder = new XMLDocBuilder();
        } else {
            docBuilder = new JSONDocBuilder();
        }
    }

}
