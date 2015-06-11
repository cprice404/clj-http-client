package com.puppetlabs.http.client.impl;

import org.apache.http.ContentTooLongException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncCharConsumer;
import org.apache.http.nio.entity.ContentBufferEntity;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Asserts;

import java.io.IOException;
import java.nio.CharBuffer;

/**
 * Basic implementation of {@link HttpAsyncResponseConsumer}. Please note that
 * this consumer buffers response content in memory and should be used for
 * relatively small response messages.
 *
 * @since 4.2
 */
//@ThreadSafe
//public class StreamingAsyncResponseConsumer extends AbstractAsyncResponseConsumer<HttpResponse> {
//
//    private volatile HttpResponse response;
//    private volatile SimpleInputBuffer buf;
//
//    public StreamingAsyncResponseConsumer() {
//        super();
//    }
//
//    @Override
//    protected void onResponseReceived(final HttpResponse response) throws IOException {
//        System.out.println("ON RESPONSE RECIEVED");
//        this.response = response;
//    }
//
//    @Override
//    protected void onEntityEnclosed(
//            final HttpEntity entity, final ContentType contentType) throws IOException {
//        System.out.println("ON ENTITY ENCLOSED");
//        long len = entity.getContentLength();
//        if (len > Integer.MAX_VALUE) {
//            throw new ContentTooLongException("Entity content is too long: " + len);
//        }
//        if (len < 0) {
//            len = 4096;
//        }
//        this.buf = new SimpleInputBuffer((int) len, new HeapByteBufferAllocator());
//        this.response.setEntity(new ContentBufferEntity(entity, this.buf));
//    }
//
//    @Override
//    protected void onContentReceived(
//            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
//        System.out.println("ON CONTENT RECEIVED");
//        Asserts.notNull(this.buf, "Content buffer");
//        this.buf.consumeContent(decoder);
//    }
//
//    @Override
//    protected void releaseResources() {
//        System.out.println("RELEASE RESOURCES");
//        this.response = null;
//        this.buf = null;
//    }
//
//    @Override
//    protected HttpResponse buildResult(final HttpContext context) {
//        System.out.println("BUILD RESULT");
//        return this.response;
//    }
//
//}

public class StreamingAsyncResponseConsumer extends AsyncCharConsumer<Boolean> {

    @Override
    protected void onResponseReceived(final HttpResponse response) {
        System.out.println("ON RESPONSE RECEIVED.");
    }

    @Override
    protected void onCharReceived(final CharBuffer buf, final IOControl ioctrl) throws IOException {
        System.out.println("ON CHAR RECEIVED.");
        while (buf.hasRemaining()) {
            System.out.print(buf.get());
        }
    }

    @Override
    protected void releaseResources() {
        System.out.println("RELEASE RESOURCES.");
    }

    @Override
    protected Boolean buildResult(final HttpContext context) {
        System.out.println("BUILD RESULT.");
        return Boolean.TRUE;
    }

}
