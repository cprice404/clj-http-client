package com.puppetlabs.http.client.impl;

import org.apache.http.ContentTooLongException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncByteConsumer;
import org.apache.http.nio.client.methods.AsyncCharConsumer;
import org.apache.http.nio.conn.ManagedNHttpClientConnection;
import org.apache.http.nio.entity.ContentBufferEntity;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.nio.util.SharedInputBuffer;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Asserts;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;

/**
 * Basic implementation of {@link HttpAsyncResponseConsumer}. Please note that
 * this consumer buffers response content in memory and should be used for
 * relatively small response messages.
 *
 * @since 4.2
 */
@ThreadSafe
public class StreamingAsyncResponseConsumer //extends AsyncByteConsumer<HttpResponse> {
                                        extends AbstractAsyncResponseConsumer<HttpResponse> {

    // NOTE: this class is basically a direct copy of the BasicAsyncResponseConsumer,
    // except for two things:
    //
    // 1. We require the `FutureCallback` object as a constructor arg, and we
    //    call its `completed` method as soon as we've initialized the HttpEntity
    //    (before we've written all of the bytes to the entity's stream).  By this
    //    point, we've parsed all the headers and such, so there's no real reason
    //    why we can't make the response accessible to the caller.
    // 2. We replace the `SimpleInputBuffer` with a `SharedInputBuffer`, for
    //    thread safety.
    //
    // TODO:
    //
    // 1. tests
    // 2. consider using a different mechanism for managing the streams; the
    //    Buffer classes here extend a class called ExpandableBuffer, and at a
    //    brief glance, it's not clear whether there is a maximum size for that
    //    buffer.  It might make more sense to just use a PipedInputStream and
    //    PipedOutputStream pair, directly.  I think that in that case, the
    //    maximum amount of memory used would be limited by the buffer size of
    //    the PipedInputStream (though, the PipedInputStream will probably always
    //    allocate that amount of memory up front.)
    //

    private final ByteBuffer bbuf;

    private volatile HttpResponse response;
//    private volatile SimpleInputBuffer buf;
//    private volatile SharedInputBuffer buf;
//    private volatile PipedInputStream piped_in;
    private volatile PipedOutputStream piped_out;
    private volatile FutureCallback<HttpResponse> callback;

    public StreamingAsyncResponseConsumer(FutureCallback<HttpResponse> callback) {
        super();
        this.callback = callback;
        // TODO: think about this default buffer size
        this.bbuf = ByteBuffer.allocate(8 * 1024);
    }

    @Override
    protected void onResponseReceived(final HttpResponse response) throws IOException {
        System.out.println("ON RESPONSE RECIEVED");
        this.response = response;
    }


    @Override
    protected void onEntityEnclosed(
            final HttpEntity entity, final ContentType contentType) throws IOException {
        System.out.println("ON ENTITY ENCLOSED");
        long len = entity.getContentLength();
        if (len > Integer.MAX_VALUE) {
            throw new ContentTooLongException("Entity content is too long: " + len);
        }
        if (len < 0) {
            len = 4096;
        }
        System.out.println("ON ENTITY CREATING BUFFERS");
//        this.buf = new SimpleInputBuffer((int) len, new HeapByteBufferAllocator());
//        this.buf = new SharedInputBuffer((int) len, new HeapByteBufferAllocator());
//        this.response.setEntity(new ContentBufferEntity(entity, this.buf));

        BasicHttpEntity http_entity = (BasicHttpEntity) entity;

        // TODO: maybe use the content length from the header here?  Or come up
        //  with some preferred default value?  And/or allow the consumer of the
        //  library to pass in a preferred buffer size?
        PipedInputStream piped_in = new PipedInputStream();
        piped_out = new PipedOutputStream();
        piped_in.connect(piped_out);

        http_entity.setContent(piped_in);
        this.response.setEntity(http_entity);
        System.out.println("ON ENTITY DONE CREATING BUFFERS, CALLING CALLBACK");
        this.callback.completed(this.response);
    }

//    @Override
//    protected void onContentReceived(
//            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
//        System.out.println("ON CONTENT RECEIVED");
////        Asserts.notNull(this.buf, "Content buffer");
//        Asserts.notNull(this.piped_out, "Piped output stream");
////        this.buf.consumeContent(decoder, ioctrl);
//        this.piped_out
//    }
//

    @Override
    protected final void onContentReceived(
            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
        Asserts.notNull(this.bbuf, "Byte buffer");

        //FIXME: IOControl needs to expose event mask in order to avoid this extreme ugliness
        final IOSession iosession;
        if (ioctrl instanceof ManagedNHttpClientConnection) {
            final ManagedNHttpClientConnection conn = (ManagedNHttpClientConnection) ioctrl;
            iosession = conn != null ? conn.getIOSession() : null;
        } else {
            iosession = null;
        }
        while (!this.isDone()) {
            final int bytesRead = decoder.read(this.bbuf);
            if (bytesRead <= 0) {
                break;
            }
            this.bbuf.flip();
            onByteReceived(this.bbuf, ioctrl);
            this.bbuf.clear();
            if (decoder.isCompleted()) {
                break;
            } else {
                if (iosession != null && (iosession.isClosed()
                        || (iosession.getEventMask() & SelectionKey.OP_READ) == 0)) {
                    break;
                }
            }
        }
    }


//    @Override
    protected void onByteReceived(ByteBuffer buf, IOControl ioctrl) throws IOException {
        while (buf.hasRemaining()) {
            piped_out.write(buf.get());
        }
    }

    @Override
    protected void releaseResources() {
        System.out.println("RELEASE RESOURCES");
        this.response = null;
//        this.buf = null;
    }

    @Override
    protected HttpResponse buildResult(final HttpContext context) {
        System.out.println("BUILD RESULT");
        try {
            this.piped_out.close();
        } catch (IOException e) {
            throw new RuntimeException("FFFFUUUUUU", e);
        }
        return this.response;
    }

}

