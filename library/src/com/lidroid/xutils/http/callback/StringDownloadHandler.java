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

package com.lidroid.xutils.http.callback;

import com.lidroid.xutils.util.IOUtils;
import com.lidroid.xutils.util.OtherUtils;
import org.apache.http.HttpEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StringDownloadHandler {


/**
 * StringDownloadHandler.java
 * 执行步骤：
 *1.获得数据总长度，更新状态
 * 2.获取响应数据输入流，指定特定编码
 * 3.循环组装获得的字符串，并更新状态
 * 4.关闭流返回字符串
 * */
    public String handleEntity(HttpEntity entity, RequestCallBackHandler callBackHandler, String charset) throws IOException {
        if (entity == null) return null;

        long current = 0;
        long total = entity.getContentLength();//字符串总长度

        if (callBackHandler != null && !callBackHandler.updateProgress(total, current, true)) {//更新状态，如果是取消状态就返回
            return null;
        }

        InputStream inputStream = null;
        StringBuilder sb = new StringBuilder();
        try {
            inputStream = entity.getContent();//获取输入流
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset));
            String line = "";
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                current += OtherUtils.sizeOfString(line, charset);//累加字符串长度
                if (callBackHandler != null) {
                    if (!callBackHandler.updateProgress(total, current, false)) {//更新状态，如果是取消状态就退出
                        break;
                    }
                }
            }
            if (callBackHandler != null) {//更新状态
                callBackHandler.updateProgress(total, current, true);
            }
        } finally {//关闭流
            IOUtils.closeQuietly(inputStream);
        }
        return sb.toString().trim();//返回字符串
    }

}
