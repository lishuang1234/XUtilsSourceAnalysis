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

package com.lidroid.xutils.http.client;

import android.os.SystemClock;
import com.lidroid.xutils.util.LogUtils;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;

public class RetryHandler implements HttpRequestRetryHandler {

    private static final int RETRY_SLEEP_INTERVAL = 500;

    private static HashSet<Class<?>> exceptionWhiteList = new HashSet<Class<?>>();

    private static HashSet<Class<?>> exceptionBlackList = new HashSet<Class<?>>();

    static {
        exceptionWhiteList.add(NoHttpResponseException.class);
        exceptionWhiteList.add(UnknownHostException.class);
        exceptionWhiteList.add(SocketException.class);

        exceptionBlackList.add(InterruptedIOException.class);
        exceptionBlackList.add(SSLHandshakeException.class);
    }

    private final int maxRetries;

    public RetryHandler(int maxRetries) {
        this.maxRetries = maxRetries;//最大重试次数
    }

    //覆写方法重试调用
    @Override
    public boolean retryRequest(IOException exception, int retriedTimes, HttpContext context) {
        boolean retry = true;

        if (exception == null || context == null) {
            return false;
        }

        //判断是否已发送请求
        Object isReqSent = context.getAttribute(ExecutionContext.HTTP_REQ_SENT);
        boolean sent = isReqSent == null ? false : (Boolean) isReqSent;

        if (retriedTimes > maxRetries) {//大于最大重试次数
            retry = false;
        } else if (exceptionBlackList.contains(exception.getClass())) {//IO操作异常中断，SSL握手异常
            retry = false;
        } else if (exceptionWhiteList.contains(exception.getClass())) {//服务器丢掉了连接，未知主机，Socket异常
            retry = true;
        } else if (!sent) {//未发送
            retry = true;
        }

        if (retry) {//重试
            try {
                Object currRequest = context.getAttribute(ExecutionContext.HTTP_REQUEST);
                if (currRequest != null) {
                    if (currRequest instanceof HttpRequestBase) {//基础请求类
                        HttpRequestBase requestBase = (HttpRequestBase) currRequest;
                        retry = "GET".equals(requestBase.getMethod());//是否是GET方法
                    } else if (currRequest instanceof RequestWrapper) {//包装请求类
                        RequestWrapper requestWrapper = (RequestWrapper) currRequest;
                        retry = "GET".equals(requestWrapper.getMethod());
                    }
                } else {
                    retry = false;
                    LogUtils.e("retry error, curr request is null");
                }
            } catch (Throwable e) {
                retry = false;
                LogUtils.e("retry error", e);
            }
        }

        if (retry) {
            SystemClock.sleep(RETRY_SLEEP_INTERVAL); // sleep a while and retry http request again.
        }

        return retry;
    }

}