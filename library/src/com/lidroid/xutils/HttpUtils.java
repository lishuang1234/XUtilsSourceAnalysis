/*
 * Copyright (c) 2013. wyouflf (wyouflf@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lidroid.xutils;

import android.text.TextUtils;

import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.*;
import com.lidroid.xutils.http.callback.HttpRedirectHandler;
import com.lidroid.xutils.http.callback.RequestCallBack;
import com.lidroid.xutils.http.client.DefaultSSLSocketFactory;
import com.lidroid.xutils.http.client.HttpRequest;
import com.lidroid.xutils.http.client.RetryHandler;
import com.lidroid.xutils.http.client.entity.GZipDecompressingEntity;
import com.lidroid.xutils.task.PriorityExecutor;
import com.lidroid.xutils.util.OtherUtils;

import org.apache.http.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

import java.io.File;
import java.io.IOException;

public class HttpUtils {

    public final static HttpCache sHttpCache = new HttpCache();

    private final DefaultHttpClient httpClient;
    private final HttpContext httpContext = new BasicHttpContext();

    private HttpRedirectHandler httpRedirectHandler;

    public HttpUtils() {
        this(HttpUtils.DEFAULT_CONN_TIMEOUT, null);
    }

    public HttpUtils(int connTimeout) {
        this(connTimeout, null);
    }

    public HttpUtils(String userAgent) {
        this(HttpUtils.DEFAULT_CONN_TIMEOUT, userAgent);
    }


    public HttpUtils(int connTimeout, String userAgent) {
        HttpParams params = new BasicHttpParams();
        //1.设置超时参数，超时释放资源
        ConnManagerParams.setTimeout(params, connTimeout);
        HttpConnectionParams.setSoTimeout(params, connTimeout);//Sets the default socket timeout
        // (SO_TIMEOUT) in milliseconds which is the timeout for waiting for data.
        HttpConnectionParams.setConnectionTimeout(params, connTimeout);//Sets the timeout until a
        // connection is etablished.
        //2.User-Agent请求报头域允许客户端将它的操作系统、浏览器和其它属性告诉服务器。
        if (TextUtils.isEmpty(userAgent)) {
            userAgent = OtherUtils.getUserAgent(null);
        }
        HttpProtocolParams.setUserAgent(params, userAgent);
        //3.设置连接池最大连接数，每台主机最多连接数
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(10));//Sets
        // lookup interface for maximum number of connections allowed per route.
        ConnManagerParams.setMaxTotalConnections(params, 10);//Sets the maximum number of
        // connections allowed.
        //4.关闭算法延迟
        HttpConnectionParams.setTcpNoDelay(params, true);
        HttpConnectionParams.setSocketBufferSize(params, 1024 * 8);
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        //5.声明协议
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", DefaultSSLSocketFactory.getSocketFactory(),
                443));

        //6.设置多线程管理
        httpClient = new DefaultHttpClient(new ThreadSafeClientConnManager(params,
                schemeRegistry), params);
        //7.请求失败处理,
        httpClient.setHttpRequestRetryHandler(new RetryHandler(DEFAULT_RETRY_TIMES));
        //8.请求发送前执行设置拦截器。 默认加上gizp压缩。 通过gizp压缩后的数据传输效率高很多。
        httpClient.addRequestInterceptor(new HttpRequestInterceptor() {//Processes a request. On
        // the client side, this step is performed before the request is sent to the server. On
        // the server side, this step is performed on incoming messages before the message body
        // is evaluated.

            @Override
            public void process(org.apache.http.HttpRequest httpRequest, HttpContext httpContext)
                    throws org.apache.http.HttpException, IOException {
                if (!httpRequest.containsHeader(HEADER_ACCEPT_ENCODING)) {
                    httpRequest.addHeader(HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
                }
            }
        });
        //9.对接受的数据处理
        httpClient.addResponseInterceptor(new HttpResponseInterceptor() {
            @Override
            public void process(HttpResponse response, HttpContext httpContext) throws org.apache
                    .http.HttpException, IOException {
                final HttpEntity entity = response.getEntity();
                if (entity == null) {
                    return;
                }
                final Header encoding = entity.getContentEncoding();
                if (encoding != null) {
                    for (HeaderElement element : encoding.getElements()) {
                        if (element.getName().equalsIgnoreCase("gzip")) {//压缩标志
                            response.setEntity(new GZipDecompressingEntity(response.getEntity()));
                            return;
                        }
                    }
                }
            }
        });
    }

    // ************************************    default settings & fields
    // ****************************

    private String responseTextCharset = HTTP.UTF_8;

    private long currentRequestExpiry = HttpCache.getDefaultExpiryTime();

    private final static int DEFAULT_CONN_TIMEOUT = 1000 * 15; // 15s

    private final static int DEFAULT_RETRY_TIMES = 3;

    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String ENCODING_GZIP = "gzip";

    private final static int DEFAULT_POOL_SIZE = 3;
    private final static PriorityExecutor EXECUTOR = new PriorityExecutor(DEFAULT_POOL_SIZE);

    public HttpClient getHttpClient() {
        return this.httpClient;
    }

    // ***************************************** config *******************************************

    public HttpUtils configResponseTextCharset(String charSet) {
        if (!TextUtils.isEmpty(charSet)) {
            this.responseTextCharset = charSet;
        }
        return this;
    }

    public HttpUtils configHttpRedirectHandler(HttpRedirectHandler httpRedirectHandler) {
        this.httpRedirectHandler = httpRedirectHandler;
        return this;
    }

    public HttpUtils configHttpCacheSize(int httpCacheSize) {
        sHttpCache.setCacheSize(httpCacheSize);
        return this;
    }

    public HttpUtils configDefaultHttpCacheExpiry(long defaultExpiry) {
        HttpCache.setDefaultExpiryTime(defaultExpiry);
        currentRequestExpiry = HttpCache.getDefaultExpiryTime();
        return this;
    }

    public HttpUtils configCurrentHttpCacheExpiry(long currRequestExpiry) {
        this.currentRequestExpiry = currRequestExpiry;
        return this;
    }

    public HttpUtils configCookieStore(CookieStore cookieStore) {
        httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
        return this;
    }

    public HttpUtils configUserAgent(String userAgent) {
        HttpProtocolParams.setUserAgent(this.httpClient.getParams(), userAgent);
        return this;
    }

    public HttpUtils configTimeout(int timeout) {
        final HttpParams httpParams = this.httpClient.getParams();
        ConnManagerParams.setTimeout(httpParams, timeout);
        HttpConnectionParams.setConnectionTimeout(httpParams, timeout);
        return this;
    }

    public HttpUtils configSoTimeout(int timeout) {
        final HttpParams httpParams = this.httpClient.getParams();
        HttpConnectionParams.setSoTimeout(httpParams, timeout);
        return this;
    }

    public HttpUtils configRegisterScheme(Scheme scheme) {
        this.httpClient.getConnectionManager().getSchemeRegistry().register(scheme);
        return this;
    }

    public HttpUtils configSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
        Scheme scheme = new Scheme("https", sslSocketFactory, 443);
        this.httpClient.getConnectionManager().getSchemeRegistry().register(scheme);
        return this;
    }

    public HttpUtils configRequestRetryCount(int count) {
        this.httpClient.setHttpRequestRetryHandler(new RetryHandler(count));
        return this;
    }

    public HttpUtils configRequestThreadPoolSize(int threadPoolSize) {
        HttpUtils.EXECUTOR.setPoolSize(threadPoolSize);
        return this;
    }

    // ***************************************** send request
    // *******************************************

    public <T> HttpHandler<T> send(HttpRequest.HttpMethod method, String url, RequestCallBack<T>
            callBack) {
        return send(method, url, null, callBack);
    }

    public <T> HttpHandler<T> send(HttpRequest.HttpMethod method, String url, RequestParams
            params, RequestCallBack<T> callBack) {
        //1.网址为空处理
        if (url == null) throw new IllegalArgumentException("url may not be null");
        //2.创建请求对象
        HttpRequest request = new HttpRequest(method, url);
        return sendRequest(request, params, callBack);
    }

    public ResponseStream sendSync(HttpRequest.HttpMethod method, String url) throws HttpException {
        return sendSync(method, url, null);
    }

    public ResponseStream sendSync(HttpRequest.HttpMethod method, String url, RequestParams
            params) throws HttpException {
        if (url == null) throw new IllegalArgumentException("url may not be null");

        HttpRequest request = new HttpRequest(method, url);
        return sendSyncRequest(request, params);
    }

    // ***************************************** download
    // *******************************************

    public HttpHandler<File> download(String url, String target, RequestCallBack<File> callback) {
        return download(HttpRequest.HttpMethod.GET, url, target, null, false, false, callback);
    }

    public HttpHandler<File> download(String url, String target, boolean autoResume,
                                      RequestCallBack<File> callback) {
        return download(HttpRequest.HttpMethod.GET, url, target, null, autoResume, false, callback);
    }

    public HttpHandler<File> download(String url, String target, boolean autoResume, boolean
            autoRename, RequestCallBack<File> callback) {
        return download(HttpRequest.HttpMethod.GET, url, target, null, autoResume, autoRename,
                callback);
    }

    public HttpHandler<File> download(String url, String target, RequestParams params,
                                      RequestCallBack<File> callback) {
        return download(HttpRequest.HttpMethod.GET, url, target, params, false, false, callback);
    }

    public HttpHandler<File> download(String url, String target, RequestParams params, boolean
            autoResume, RequestCallBack<File> callback) {
        return download(HttpRequest.HttpMethod.GET, url, target, params, autoResume, false,
                callback);
    }

    public HttpHandler<File> download(String url, String target, RequestParams params, boolean
            autoResume, boolean autoRename, RequestCallBack<File> callback) {
        return download(HttpRequest.HttpMethod.GET, url, target, params, autoResume, autoRename,
                callback);
    }

    public HttpHandler<File> download(HttpRequest.HttpMethod method, String url, String target,
                                      RequestParams params, RequestCallBack<File> callback) {
        return download(method, url, target, params, false, false, callback);
    }

    public HttpHandler<File> download(HttpRequest.HttpMethod method, String url, String target,
                                      RequestParams params, boolean autoResume,
                                      RequestCallBack<File> callback) {
        return download(method, url, target, params, autoResume, false, callback);
    }

    public HttpHandler<File> download(HttpRequest.HttpMethod method, String url, String target,
                                      RequestParams params, boolean autoResume, boolean
                                              autoRename, RequestCallBack<File> callback) {

        if (url == null) throw new IllegalArgumentException("url may not be null");
        if (target == null) throw new IllegalArgumentException("target may not be null");

        HttpRequest request = new HttpRequest(method, url);

        HttpHandler<File> handler = new HttpHandler<File>(httpClient, httpContext,
                responseTextCharset, callback);

        handler.setExpiry(currentRequestExpiry);
        handler.setHttpRedirectHandler(httpRedirectHandler);

        if (params != null) {
            request.setRequestParams(params, handler);
            handler.setPriority(params.getPriority());
        }
        handler.executeOnExecutor(EXECUTOR, request, target, autoResume, autoRename);
        return handler;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * 发送执行HttpRequest
     * 1.新建HttpHandler对象，用于工作线程处理网络请求
     * 2.HttpRequest设置用户的RequestParams
     * 3.HttpHandler对象异步线程池执行网络请求HttpRequest
     */
    private <T> HttpHandler<T> sendRequest(HttpRequest request, RequestParams params,
                                           RequestCallBack<T> callBack) {

        HttpHandler<T> handler = new HttpHandler<T>(httpClient, httpContext, responseTextCharset,
                callBack);
        handler.setExpiry(currentRequestExpiry);//设置终止时间
        handler.setHttpRedirectHandler(httpRedirectHandler);
        request.setRequestParams(params, handler);//设置请求参数，同时添加Handler引用
        if (params != null) {
            handler.setPriority(params.getPriority());//设置优先级
        }
        handler.executeOnExecutor(EXECUTOR, request);
        return handler;
    }

    private ResponseStream sendSyncRequest(HttpRequest request, RequestParams params) throws
            HttpException {

        SyncHttpHandler handler = new SyncHttpHandler(httpClient, httpContext, responseTextCharset);

        handler.setExpiry(currentRequestExpiry);
        handler.setHttpRedirectHandler(httpRedirectHandler);
        request.setRequestParams(params);

        return handler.sendRequest(request);
    }
}
