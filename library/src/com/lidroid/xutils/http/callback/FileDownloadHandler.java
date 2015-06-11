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

import android.text.TextUtils;
import com.lidroid.xutils.util.IOUtils;
import org.apache.http.HttpEntity;

import java.io.*;

public class FileDownloadHandler {


    /**
     * FileDownloadHandler.java
     *1.先检查是否是续传，如果是构造追加输出流否则直接构造输出流
     * 2.构造响应输入流，同时构造文件输出流
     * 3.更新状态，如果处于任务取消状态直接返回当前文件
     * 4.循环逐个读取输入写出到文件，同时更新状态，如果取消状态立即返回文件
     * 5.强制更新状态，关闭流资源
     * 6.重命名字段不为空，新建文件去重命名获得文件
     * 7.返回文件
     * */
    public File handleEntity(HttpEntity entity,
                             RequestCallBackHandler callBackHandler,
                             String target,
                             boolean isResume,
                             String responseFileName) throws IOException {
        if (entity == null || TextUtils.isEmpty(target)) {
            return null;
        }

        File targetFile = new File(target);

        if (!targetFile.exists()) {
            File dir = targetFile.getParentFile();
            if (dir.exists() || dir.mkdirs()) {
                targetFile.createNewFile();//生成文件
            }
        }

        long current = 0;
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            FileOutputStream fileOutputStream = null;
            if (isResume) {//续传
                current = targetFile.length();//记录已存在文件大小
                fileOutputStream = new FileOutputStream(target, true);//文件流追加到文件后面
            } else {
                fileOutputStream = new FileOutputStream(target);
            }

            long total = entity.getContentLength() + current;//获取文件总长度
            bis = new BufferedInputStream(entity.getContent());//获取输入流
            bos = new BufferedOutputStream(fileOutputStream);

            if (callBackHandler != null && !callBackHandler.updateProgress(total, current, true)) {//去强制更新进度，如果任务取消返回
                return targetFile;//任务状态已取消，返回当前文件
            }

            byte[] tmp = new byte[4096];
            int len;
            while ((len = bis.read(tmp)) != -1) {
                bos.write(tmp, 0, len);//写文件
                current += len;//记录文件长度
                if (callBackHandler != null) {
                    if (!callBackHandler.updateProgress(total, current, false)) {//去强制更新进度，如果任务取消返回
                        return targetFile;//任务状态已取消，返回当前文件
                    }
                }
            }
            bos.flush();
            if (callBackHandler != null) {
                callBackHandler.updateProgress(total, current, true);//去强制更新进度
            }
        } finally {//最后关闭流
            IOUtils.closeQuietly(bis);
            IOUtils.closeQuietly(bos);
        }

        if (targetFile.exists() && !TextUtils.isEmpty(responseFileName)) {//重命名文件
            File newFile = new File(targetFile.getParent(), responseFileName);
            while (newFile.exists()) {//避免文件名重复，新建直到文件不重复
                newFile = new File(targetFile.getParent(), System.currentTimeMillis() + responseFileName);
            }
            return targetFile.renameTo(newFile) ? newFile : targetFile;//修该命名
        } else {
            return targetFile;
        }
    }

}
