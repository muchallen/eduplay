package com.eduplay.backend.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts gzip-compressed request bodies ({@code Content-Encoding: gzip}). A 500-result sync
 * payload compresses roughly 10x, which matters on slow or metered mobile connections.
 * Decompressed size is capped to defend against compression bombs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GzipRequestFilter extends OncePerRequestFilter {

    static final long MAX_DECOMPRESSED_BYTES = 10L * 1024 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String encoding = request.getHeader("Content-Encoding");
        return encoding == null || !encoding.trim().equalsIgnoreCase("gzip");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new GzipRequestWrapper(request), response);
    }

    private static final class GzipRequestWrapper extends HttpServletRequestWrapper {

        private ServletInputStream stream;

        GzipRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                InputStream bounded = new BoundedInputStream(new GZIPInputStream(super.getInputStream()));
                stream = new ServletInputStream() {
                    private boolean finished;

                    @Override
                    public int read() throws IOException {
                        int b = bounded.read();
                        finished = b == -1;
                        return b;
                    }

                    @Override
                    public int read(byte[] buffer, int off, int len) throws IOException {
                        int n = bounded.read(buffer, off, len);
                        finished = n == -1;
                        return n;
                    }

                    @Override
                    public boolean isFinished() {
                        return finished;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Async reads are not supported");
                    }
                };
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String charset = getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name();
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public String getHeader(String name) {
            if ("Content-Encoding".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name)) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Content-Encoding".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }
    }

    private static final class BoundedInputStream extends FilterInputStream {

        private long count;

        BoundedInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                increment(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            int n = super.read(buffer, off, len);
            if (n > 0) {
                increment(n);
            }
            return n;
        }

        private void increment(long n) throws IOException {
            count += n;
            if (count > MAX_DECOMPRESSED_BYTES) {
                throw new IOException("Decompressed request body exceeds limit");
            }
        }
    }
}
