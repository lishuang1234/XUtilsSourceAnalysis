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

package com.lidroid.xutils.http;

import android.os.SystemClock;

import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.callback.*;
import com.lidroid.xutils.task.PriorityAsyncTask;
import com.lidroid.xutils.util.OtherUtils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.protocol.HttpContext;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;


public class HttpHandler<T> extends PriorityAsyncTask<Object, Object, Void> implements
        RequestCallBackHandler {

    private final AbstractHttpClient client;
    private final HttpContext context;

    private HttpRedirectHandler httpRedirectHandler;

    public void setHttpRedirectHandler(HttpRedirectHandler httpRedirectHandler) {
        if (httpRedirectHandler != null) {
            this.httpRedirectHandler = httpRedirectHandler;
        }
    }

    private String requestUrl;
    private String requestMethod;
    private HttpRequestBase request;
    private boolean isUploading = true;
    private RequestCallBack<T> callback;

    private int retriedCount = 0;
    private String fileSavePath = null;
    private boolean isDownloadingFile = false;
    private boolean autoResume = false; // Whether the downloading could continue from the point
    // of interruption.
    private boolean autoRename = false; // Whether rename the file by response header info when
    // the download completely.
    private String charset; // The default charset of response header info.

    public HttpHandler(AbstractHttpClient client, HttpContext context, String charset,
                       RequestCallBack<T> callback) {
        this.client = client;
        this.context = context;
        this.callback = callback;
        this.charset = charset;
        this.client.setRedirectHandler(notUseApacheRedirectHandler);//设置重定向结构自己处理
    }

    private State state = State.WAITING;

    public State getState() {
        return state;
    }

    private long expiry = HttpCache.getDefaultExpiryTime();

    public void setExpiry(long expiry) {
        this.expiry = expiry;
    }

    public void setRequestCallBack(RequestCallBack<T> callback) {
        this.callback = callback;
    }

    public RequestCallBack<T> getRequestCallBack() {
        return this.callback;
    }

    /**
     * 执行网络请求执行步骤：
     * 1.获取重定向处理对象
     * 2.死循环执行，首先判断如果是续传下载文件，根据新建的文件对象大小设置相应下载范围。
     * 3.获取缓存文件，如果存在缓存直接返回响应对象
     * 4.没有缓存去请求执行网络获取数据
     * 5.处理获取的网络数据，并返回响应对象
     * 6.遇到异常重新请求
     */
    @SuppressWarnings("unchecked")
    private ResponseInfo<T> sendRequest(HttpRequestBase request) throws HttpException {

        HttpRequestRetryHandler retryHandler = client.getHttpRequestRetryHandler();//请求重试回调：重定向

        while (true) {
            if (autoResume && isDownloadingFile) {//对应download(...)下载函数
                File downloadFile = new File(fileSavePath);//文件保存位置
                long fileLen = 0;
                if (downloadFile.isFile() && downloadFile.exists()) {
                    fileLen = downloadFile.length();
                }
                if (fileLen > 0) {//断点接着下载，设置头
                    request.setHeader("RANGE", "bytes=" + fileLen + "-");
                }
            }

            boolean retry = true;
            IOException exception = null;

            try {
                requestMethod = request.getMethod();//请求方式GET？POST？
                if (HttpUtils.sHttpCache.isEnabled(requestMethod)) {//默认GET方法可用
                    String result = HttpUtils.sHttpCache.get(requestUrl);//获取缓存
                    if (result != null) {
                        return new ResponseInfo<T>(null, (T) result, true);
                    }
                }
                //没有缓存
                ResponseInfo<T> responseInfo = null;
                if (!isCancelled()) {
                    HttpResponse response = client.execute(request, context);//执行网络请求
                    responseInfo = handleResponse(response);//根据响应获得信息
                }
                return responseInfo;
            } catch (UnknownHostException e) {
                exception = e;
                retry = retryHandler.retryRequest(exception, ++retriedCount, context);
            } catch (IOException e) {
                exception = e;
                retry = retryHandler.retryRequest(exception, ++retriedCount, context);
            } catch (NullPointerException e) {
                exception = new IOException(e.getMessage());
                exception.initCause(e);
                retry = retryHandler.retryRequest(exception, ++retriedCount, context);
            } catch (HttpException e) {
                throw e;
            } catch (Throwable e) {
                exception = new IOException(e.getMessage());
                exception.initCause(e);
                retry = retryHandler.retryRequest(exception, ++retriedCount, context);
                //确定时候在异常发生时重新请求
            }
            if (!retry) {
                throw new HttpException(exception);
            }
        }
    }

    /**
     * 后台线程池处理执行，此处是在Work线程处理任务
     * 1.根据传入参数长度，初始化变量。长度大于3代表download文件任务，否则是普通任务。
     * 2.获取HttpRequestBase对象，获取请求URL。
     * 3.调用sendRequest方法执行请求。返回ResponseInfo
     */
    @Override
    protected Void doInBackground(Object... params) {

        if (this.state == State.CANCELLED || params == null || params.length == 0) return null;
        //获取参数
        if (params.length > 3) {
            fileSavePath = String.valueOf(params[1]);//target文件存储路径位置
            isDownloadingFile = fileSavePath != null;
            autoResume = (Boolean) params[2];//自动重新开始
            autoRename = (Boolean) params[3];//自动重命名
        }

        try {
            if (this.state == State.CANCELLED) return null;
            // init request & requestUrl
            request = (HttpRequestBase) params[0];//获得请求参数
            requestUrl = request.getURI().toString();//获取网址
            if (callback != null) {//请求回调接口
                callback.setRequestUrl(requestUrl);
            }

            this.publishProgress(UPDATE_START);

            lastUpdateTime = SystemClock.uptimeMillis(); // 从开机到现在的毫秒数（手机睡眠的时间不包括在内）；

            ResponseInfo<T> responseInfo = sendRequest(request);//执行请求，获取响应
            if (responseInfo != null) {
                this.publishProgress(UPDATE_SUCCESS, responseInfo);//发送进度
                return null;
            }
        } catch (HttpException e) {
            this.publishProgress(UPDATE_FAILURE, e, e.getMessage());
        }

        return null;
    }

    private final static int UPDATE_START = 1;
    private final static int UPDATE_LOADING = 2;
    private final static int UPDATE_FAILURE = 3;
    private final static int UPDATE_SUCCESS = 4;

    /**
     * HttpHandler.java
     * 覆写父类方法
     * 处理客户端回调接口方法，通知客户端状态信息
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void onProgressUpdate(Object... values) {
        if (this.state == State.CANCELLED || values == null || values.length == 0 || callback ==
                null)
            return;
        switch ((Integer) values[0]) {
            case UPDATE_START:
                this.state = State.STARTED;
                callback.onStart();
                break;
            case UPDATE_LOADING:
                if (values.length != 3) return;
                this.state = State.LOADING;
                callback.onLoading(Long.valueOf(String.valueOf(values[1])), Long.valueOf(String
                        .valueOf(values[2])), isUploading);
                break;
            case UPDATE_FAILURE:
                if (values.length != 3) return;
                this.state = State.FAILURE;
                callback.onFailure((HttpException) values[1], (String) values[2]);
                break;
            case UPDATE_SUCCESS:
                if (values.length != 2) return;
                this.state = State.SUCCESS;
                callback.onSuccess((ResponseInfo<T>) values[1]);
                break;
            default:
                break;
        }
    }

    /**
     * 处理网络响应执行步骤：
     * 1.检查当前执行状态，如果是取消立即返回方法。
     * 2.获取状态码判断网络请求状态
     * 3.如果是请求成功，判断是否在下载文件或是字符串请求
     * 4.对于文件下载，首先检查是否续传是否自动重命名，然后传入以上参数构造文件
     * 5.对于字符串，首先获取数据，然后放入缓存，最后返回所得
     * 6.对于重定向继续发送网络请求处理
     */
    @SuppressWarnings("unchecked")
    private ResponseInfo<T> handleResponse(HttpResponse response) throws HttpException,
            IOException {
        if (response == null) {
            throw new HttpException("response is null");
        }
        if (isCancelled()) return null;

        StatusLine status = response.getStatusLine();
        int statusCode = status.getStatusCode();
        if (statusCode < 300) {//请求成功
            Object result = null;
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                isUploading = false;
                if (isDownloadingFile) {//是在下载文件
                    autoResume = autoResume && OtherUtils.isSupportRange(response);//自动续传
                    String responseFileName = autoRename ? OtherUtils.getFileNameFromHttpResponse
                            (response) : null;//自动重命名
                    FileDownloadHandler downloadHandler = new FileDownloadHandler();
                    result = downloadHandler.handleEntity(entity, this, fileSavePath, autoResume,
                            responseFileName);//根据响应获得文件
                } else {//不是在下载文件
                    StringDownloadHandler downloadHandler = new StringDownloadHandler();
                    result = downloadHandler.handleEntity(entity, this, charset);//处理获得的数据
                    if (HttpUtils.sHttpCache.isEnabled(requestMethod)) {//缓存字符串数据
                        HttpUtils.sHttpCache.put(requestUrl, (String) result, expiry);
                    }
                }
            }
            return new ResponseInfo<T>(response, (T) result, false);
        } else if (statusCode == 301 || statusCode == 302) {//请求的资源现在不存在
            if (httpRedirectHandler == null) {
                httpRedirectHandler = new DefaultHttpRedirectHandler();
            }
            HttpRequestBase request = httpRedirectHandler.getDirectRequest(response);//重定向请求
            if (request != null) {
                return this.sendRequest(request);
            }
        } else if (statusCode == 416) {// Range 中指定的任何数据范围都与当前资源的可用范围不重合，
            throw new HttpException(statusCode, "maybe the file has downloaded completely");
        } else {
            throw new HttpException(statusCode, status.getReasonPhrase());
        }
        return null;
    }

    /**
     * HttpHandler.java
     * 覆写父类方法处理结束状态
     * cancel request task.
     */
    @Override
    public void cancel() {
        this.state = State.CANCELLED;

        if (request != null && !request.isAborted()) {
            try {
                request.abort();
            } catch (Throwable e) {
            }
        }
        if (!this.isCancelled()) {
            try {
                this.cancel(true);//调用父类方法去结束该异步任务
            } catch (Throwable e) {
            }
        }

        if (callback != null) {
            callback.onCancelled();
        }
    }

    private long lastUpdateTime;


    /**
     * 更新进度
     * 返回任务是否取消
     */
    @Override
    public boolean updateProgress(long total, long current, boolean forceUpdateUI) {
        if (callback != null && this.state != State.CANCELLED) {
            if (forceUpdateUI) {//更新UI 线程
                this.publishProgress(UPDATE_LOADING, total, current);
            } else {
                long currTime = SystemClock.uptimeMillis();
                if (currTime - lastUpdateTime >= callback.getRate()) {//大于设定的比率才去更新进度
                    lastUpdateTime = currTime;
                    this.publishProgress(UPDATE_LOADING, total, current);
                }
            }
        }
        return this.state != State.CANCELLED;
    }

    public enum State {
        WAITING(0), STARTED(1), LOADING(2), FAILURE(3), CANCELLED(4), SUCCESS(5);
        private int value = 0;

        State(int value) {
            this.value = value;
        }

        public static State valueOf(int value) {
            switch (value) {
                case 0:
                    return WAITING;
                case 1:
                    return STARTED;
                case 2:
                    return LOADING;
                case 3:
                    return FAILURE;
                case 4:
                    return CANCELLED;
                case 5:
                    return SUCCESS;
                default:
                    return FAILURE;
            }
        }

        public int value() {
            return this.value;
        }
    }

    private static final NotUseApacheRedirectHandler notUseApacheRedirectHandler = new
            NotUseApacheRedirectHandler();

    /**
     * forward是服务器内部重定向，程序收到请求后重新定向到另一个程序，客户机并不知道；redirect则是服务器收到请求后发送一个状态头给客
     * 户，客户将再请求一次，这里多了两次网络通信的来往。当然forward也有缺点，就是forward的页面的路径如果是相对路径就会有些问题了。
     */
    private static final class NotUseApacheRedirectHandler implements RedirectHandler {
        @Override
        public boolean isRedirectRequested(HttpResponse httpResponse, HttpContext httpContext) {
            return false;
        }

        @Override
        public URI getLocationURI(HttpResponse httpResponse, HttpContext httpContext) throws
                ProtocolException {
            return null;
        }
    }
}
