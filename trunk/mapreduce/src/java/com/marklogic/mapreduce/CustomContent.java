/*
 * Copyright (c) 2003-2012 MarkLogic Corporation. All rights reserved.
 */
package com.marklogic.mapreduce;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Writable;

import com.marklogic.xcc.Content;
import com.marklogic.xcc.ContentCreateOptions;

/**
 * This is a Writable Interface for custom content.
 * @author ali
 */
public interface CustomContent extends Writable{
    /**
     * Get the content that is about to inserted.  
     * 
     * @param conf job configuration
     * @param options a template for ContentCreateOptions to be used for 
     * the content to be created.
     * @param uri URI String of DocumentURI
     * @return Content
     */
    public Content getContent(Configuration conf, ContentCreateOptions options,
                    String uri);
}
