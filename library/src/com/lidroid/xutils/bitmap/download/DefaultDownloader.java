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

package com.lidroid.xutils.bitmap.download;

import com.lidroid.xutils.BitmapUtils;
import com.lidroid.xutils.util.IOUtils;
import com.lidroid.xutils.util.LogUtils;
import com.lidroid.xutils.util.OtherUtils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;

public class DefaultDownloader extends Downloader {

    /**
     * Download bitmap to outputStream by uri.
     *
     * @param uri          file path, assets path(assets/xxx) or http url.
     * @param outputStream
     * @param task
     * @return The expiry time stamp or -1 if failed to download.
     * 下载文件到dirtyFile
     * 1.url是文件路径，获取响应输入流和文件长度，构造过期时间戳
     * 2.url是assets路径获取响应输入流和文件长度，构造过期时间戳为最大值
     * 3.url是网址，获取URLConnection，获取输入流，构造时间戳
     * 4.将获取的输入流读入资源，写出到提供的输出流
     * 5.返回过期时间戳
     */
    @Override
    public long downloadToStream(String uri, OutputStream outputStream, final BitmapUtils
            .BitmapLoadTask<?> task) {

        if (task == null || task.isCancelled() || task.getTargetContainer() == null) return -1;

        URLConnection urlConnection = null;
        BufferedInputStream bis = null;

        OtherUtils.trustAllHttpsURLConnection();

        long result = -1;
        long fileLen = 0;
        long currCount = 0;
        try {
            if (uri.startsWith("/")) {//url是一个文件路径
                FileInputStream fileInputStream = new FileInputStream(uri);
                fileLen = fileInputStream.available();//该方法返回可估算从这个输入流中可无阻塞读取剩余的字节数。
                bis = new BufferedInputStream(fileInputStream);
                result = System.currentTimeMillis() + this.getDefaultExpiry();
            } else if (uri.startsWith("assets/")) {//assets文件夹中的资源
                InputStream inputStream = this.getContext().getAssets().open(uri.substring(7, uri
                        .length()));

                fileLen = inputStream.available();
                bis = new BufferedInputStream(inputStream);
                result = Long.MAX_VALUE;
            } else {//设置一个资源网址
                final URL url = new URL(uri);
                urlConnection = url.openConnection();
                urlConnection.setConnectTimeout(this.getDefaultConnectTimeout());
                urlConnection.setReadTimeout(this.getDefaultReadTimeout());
                bis = new BufferedInputStream(urlConnection.getInputStream());
                result = urlConnection.getExpiration();//响应过期时间戳
                result = result < System.currentTimeMillis() ? System.currentTimeMillis() + this
                        .getDefaultExpiry() : result;
                fileLen = urlConnection.getContentLength();
            }

            if (task.isCancelled() || task.getTargetContainer() == null) return -1;

            byte[] buffer = new byte[4096];
            int len = 0;
            BufferedOutputStream out = new BufferedOutputStream(outputStream);
            while ((len = bis.read(buffer)) != -1) {
                out.write(buffer, 0, len);//向提供的输出流写出，这个输出流一般是dirtyFile
                currCount += len;
                if (task.isCancelled() || task.getTargetContainer() == null) return -1;
                task.updateProgress(fileLen, currCount);
            }
            out.flush();
        } catch (Throwable e) {
            result = -1;
            LogUtils.e(e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(bis);
        }
        return result;
    }
}
