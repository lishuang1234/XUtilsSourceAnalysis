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

package com.lidroid.xutils.bitmap.core;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;

import com.lidroid.xutils.BitmapUtils;
import com.lidroid.xutils.bitmap.BitmapDisplayConfig;
import com.lidroid.xutils.bitmap.BitmapGlobalConfig;
import com.lidroid.xutils.bitmap.factory.BitmapFactory;
import com.lidroid.xutils.cache.FileNameGenerator;
import com.lidroid.xutils.cache.LruDiskCache;
import com.lidroid.xutils.cache.LruMemoryCache;
import com.lidroid.xutils.util.IOUtils;
import com.lidroid.xutils.util.LogUtils;
import com.lidroid.xutils.util.OtherUtils;

import java.io.*;


public class BitmapCache {

    private final int DISK_CACHE_INDEX = 0;

    private LruDiskCache mDiskLruCache;
    private LruMemoryCache<MemoryCacheKey, Bitmap> mMemoryCache;

    private final Object mDiskCacheLock = new Object();

    private BitmapGlobalConfig globalConfig;

    /**
     * Creating a new ImageCache object using the specified parameters.
     *
     * @param globalConfig The cache parameters to use to initialize the cache
     */
    public BitmapCache(BitmapGlobalConfig globalConfig) {
        if (globalConfig == null)
            throw new IllegalArgumentException("globalConfig may not be null");
        this.globalConfig = globalConfig;
    }


    /**
     * Initialize the memory cache
     * 1.判断是否打开内存缓存，未打开就返回
     * 2.如果LruMemoryCache对象不为空就清空缓存
     * 3.LruMemoryCache对象为空就去新建对象覆写方法返回每个缓存单元的大小
     */
    public void initMemoryCache() {
        if (!globalConfig.isMemoryCacheEnabled()) return;

        // Set up memory cache
        if (mMemoryCache != null) {
            try {
                clearMemoryCache();
            } catch (Throwable e) {
            }
        }
        mMemoryCache = new LruMemoryCache<MemoryCacheKey, Bitmap>(globalConfig.getMemoryCacheSize
                ()) {
            /**
             * Measure item size in bytes rather than units which is more practical
             * for a bitmap cache
             */
            @Override
            protected int sizeOf(MemoryCacheKey key, Bitmap bitmap) {
                if (bitmap == null) return 0;
                return bitmap.getRowBytes() * bitmap.getHeight();
            }
        };
    }

    /**
     * Initializes the disk cache.  Note that this includes disk access so this should not be
     * executed on the main/UI thread. By default an ImageCache does not initialize the disk
     * cache when it is created, instead you should call initDiskCache() to initialize it on a
     * background thread.
     * 初始化磁盘缓存
     * 1.获取缓存路径生成文件目录，确定可缓存磁盘占用空间大小
     * 2.根据条件open相应LruDiskCache对象
     * 3.设置文件名设置器
     */
    public void initDiskCache() {
        // Set up disk cache
        synchronized (mDiskCacheLock) {
            if (globalConfig.isDiskCacheEnabled() && (mDiskLruCache == null || mDiskLruCache
                    .isClosed())) {
                File diskCacheDir = new File(globalConfig.getDiskCachePath());
                if (diskCacheDir.exists() || diskCacheDir.mkdirs()) {
                    long availableSpace = OtherUtils.getAvailableSpace(diskCacheDir);//目录总可用空间
                    long diskCacheSize = globalConfig.getDiskCacheSize();//磁盘缓存大小
                    diskCacheSize = availableSpace > diskCacheSize ? diskCacheSize : availableSpace;
                    try {
                        //第一个参数指定的是数据的缓存地址，第二个参数指定当前应用程序的版本号，第三个参数指定同一个key可以对应多少个缓存文件，基本都是传1
                        // ，第四个参数指定最多可以缓存多少字节的数据。
                        mDiskLruCache = LruDiskCache.open(diskCacheDir, 1, 1, diskCacheSize);
                        mDiskLruCache.setFileNameGenerator(globalConfig.getFileNameGenerator());
                        LogUtils.d("create disk cache success");
                    } catch (Throwable e) {
                        mDiskLruCache = null;
                        LogUtils.e("create disk cache error", e);
                    }
                }
            }
        }
    }

    public void setMemoryCacheSize(int maxSize) {
        if (mMemoryCache != null) {
            mMemoryCache.setMaxSize(maxSize);
        }
    }

    public void setDiskCacheSize(int maxSize) {
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache != null) {
                mDiskLruCache.setMaxSize(maxSize);
            }
        }
    }

    public void setDiskCacheFileNameGenerator(FileNameGenerator fileNameGenerator) {
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache != null && fileNameGenerator != null) {
                mDiskLruCache.setFileNameGenerator(fileNameGenerator);
            }
        }
    }

    /**
     * BitmapCache.java
     * 下载Bitmap
     * 1.如果配置开启磁盘缓存，下载Bitmap到dirtyFile，根据结果clean或者delete，构造Bitmap
     * 2.如果上述下载Bitmap为null，将Bitmap下载到内存输出流中，构造Bitmap
     * 3.处理Bitmap旋转以及添加到内存缓存中
     */
    public Bitmap downloadBitmap(String uri, BitmapDisplayConfig config, final BitmapUtils
            .BitmapLoadTask<?> task) {

        BitmapMeta bitmapMeta = new BitmapMeta();

        OutputStream outputStream = null;
        LruDiskCache.Snapshot snapshot = null;

        try {
            Bitmap bitmap = null;

            // try download to disk，下载到磁盘缓存
            if (globalConfig.isDiskCacheEnabled()) {
                if (mDiskLruCache == null) {
                    initDiskCache();
                }

                if (mDiskLruCache != null) {
                    try {
                        snapshot = mDiskLruCache.get(uri);
                        if (snapshot == null) {//缓存不存在
                            LruDiskCache.Editor editor = mDiskLruCache.edit(uri);
                            if (editor != null) {
                                outputStream = editor.newOutputStream(DISK_CACHE_INDEX);//dirtyFile
                                bitmapMeta.expiryTimestamp = globalConfig.getDownloader()
                                        .downloadToStream(uri, outputStream, task);
                                //下载Bitmap存储到dirtyFile
                                if (bitmapMeta.expiryTimestamp < 0) {//下载出错
                                    editor.abort();
                                    return null;
                                } else {//下载成功
                                    editor.setEntryExpiryTimestamp(bitmapMeta.expiryTimestamp);
                                    editor.commit();
                                }
                                snapshot = mDiskLruCache.get(uri);
                            }
                        }
                        if (snapshot != null) {
                            bitmapMeta.inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
                            bitmap = decodeBitmapMeta(bitmapMeta, config);//构造Bitmap
                            if (bitmap == null) {//获取的Bitmap为null，删除对应clean文件
                                bitmapMeta.inputStream = null;
                                mDiskLruCache.remove(uri);
                            }
                        }
                    } catch (Throwable e) {
                        LogUtils.e(e.getMessage(), e);
                    }
                }
            }
            // try download to memory stream，
            if (bitmap == null) {
                outputStream = new ByteArrayOutputStream();
                bitmapMeta.expiryTimestamp = globalConfig.getDownloader().downloadToStream(uri,
                        outputStream, task);
                if (bitmapMeta.expiryTimestamp < 0) {//下载失败了
                    return null;
                } else {//成功，去生成Bitmap
                    bitmapMeta.data = ((ByteArrayOutputStream) outputStream).toByteArray();
                    bitmap = decodeBitmapMeta(bitmapMeta, config);
                }
            }

            if (bitmap != null) {//处理旋转，添加到内存缓存
                bitmap = rotateBitmapIfNeeded(uri, config, bitmap);
                bitmap = addBitmapToMemoryCache(uri, config, bitmap, bitmapMeta.expiryTimestamp);
            }
            return bitmap;
        } catch (Throwable e) {
            LogUtils.e(e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(outputStream);
            IOUtils.closeQuietly(snapshot);
        }

        return null;
    }

    /**
     * 将Bitmap添加到内存中
     */
    private Bitmap addBitmapToMemoryCache(String uri, BitmapDisplayConfig config, Bitmap bitmap,
                                          long expiryTimestamp) throws IOException {
        if (config != null) {
            BitmapFactory bitmapFactory = config.getBitmapFactory();
            if (bitmapFactory != null) {//
                bitmap = bitmapFactory.cloneNew().createBitmap(bitmap);
            }
        }
        if (uri != null && bitmap != null && globalConfig.isMemoryCacheEnabled() && mMemoryCache
                != null) {
            MemoryCacheKey key = new MemoryCacheKey(uri, config);
            mMemoryCache.put(key, bitmap, expiryTimestamp);//添加到内存缓存
        }
        return bitmap;
    }

    /**
     * Get the bitmap from memory cache.
     *
     * @param uri    Unique identifier for which item to get
     * @param config
     * @return The bitmap if found in cache, null otherwise
     * 从内存缓存中查找是否存在Bitmap
     */
    public Bitmap getBitmapFromMemCache(String uri, BitmapDisplayConfig config) {
        if (mMemoryCache != null && globalConfig.isMemoryCacheEnabled()) {
            MemoryCacheKey key = new MemoryCacheKey(uri, config);
            return mMemoryCache.get(key);
        }
        return null;
    }

    /**
     * Get the bitmap file from disk cache.
     *
     * @param uri Unique identifier for which item to get
     * @return The file if found in cache.
     */
    public File getBitmapFileFromDiskCache(String uri) {
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache != null) {
                return mDiskLruCache.getCacheFile(uri, DISK_CACHE_INDEX);
            } else {
                return null;
            }
        }
    }

    /**
     * Get the bitmap from disk cache.
     * @param uri
     * @param config
     * @return
     * BitmapCache.java
     * 从磁盘缓存中获取Bitmap
     * 1.检查LruDiskCache是否存在，初始化LruDiskCache对象
     * 2.根据传入url获取磁盘缓存处理Snapshot对象，如果为null即不存在缓存，直接返回null
     * 3.判断配置是否为空或者显示原图，如果是，直接根据输入流构造Bitmap。否则根据传入配置压缩Bitmap
     * 4.根据需要处理Bitmap旋转
     * 5.将Bitmap添加到内存缓存中，返回Bitmap
     */
    public Bitmap getBitmapFromDiskCache(String uri, BitmapDisplayConfig config) {
        if (uri == null || !globalConfig.isDiskCacheEnabled()) return null;
        if (mDiskLruCache == null) {
            initDiskCache();
        }
        if (mDiskLruCache != null) {
            LruDiskCache.Snapshot snapshot = null;
            try {
                snapshot = mDiskLruCache.get(uri);
                if (snapshot != null) {
                    Bitmap bitmap = null;
                    if (config == null || config.isShowOriginal()) {//显示原图
                        bitmap = BitmapDecoder.decodeFileDescriptor(snapshot.getInputStream
                                (DISK_CACHE_INDEX).getFD());
                    } else {//图片压缩
                        bitmap = BitmapDecoder.decodeSampledBitmapFromDescriptor(snapshot
                                .getInputStream(DISK_CACHE_INDEX).getFD(), config
                                .getBitmapMaxSize(), config.getBitmapConfig());
                    }
                    bitmap = rotateBitmapIfNeeded(uri, config, bitmap);//图片旋转
                    bitmap = addBitmapToMemoryCache(uri, config, bitmap, mDiskLruCache
                            .getExpiryTimestamp(uri));
                    return bitmap;
                }
            } catch (Throwable e) {
                LogUtils.e(e.getMessage(), e);
            } finally {
                IOUtils.closeQuietly(snapshot);
            }
        }
        return null;
    }

    /**
     * Clears both the memory and disk cache associated with this ImageCache object. Note that
     * this includes disk access so this should not be executed on the main/UI thread.
     */
    public void clearCache() {
        clearMemoryCache();
        clearDiskCache();
    }

    public void clearMemoryCache() {
        if (mMemoryCache != null) {
            mMemoryCache.evictAll();
        }
    }

    public void clearDiskCache() {
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
                try {
                    mDiskLruCache.delete();
                    mDiskLruCache.close();
                } catch (Throwable e) {
                    LogUtils.e(e.getMessage(), e);
                }
                mDiskLruCache = null;
            }
        }
        initDiskCache();
    }


    public void clearCache(String uri) {
        clearMemoryCache(uri);
        clearDiskCache(uri);
    }

    public void clearMemoryCache(String uri) {
        MemoryCacheKey key = new MemoryCacheKey(uri, null);
        if (mMemoryCache != null) {
            while (mMemoryCache.containsKey(key)) {
                mMemoryCache.remove(key);
            }
        }
    }

    public void clearDiskCache(String uri) {
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
                try {
                    mDiskLruCache.remove(uri);
                } catch (Throwable e) {
                    LogUtils.e(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Flushes the disk cache associated with this ImageCache object. Note that this includes
     * disk access so this should not be executed on the main/UI thread.
     */
    public void flush() {
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache != null) {
                try {
                    mDiskLruCache.flush();
                } catch (Throwable e) {
                    LogUtils.e(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Closes the disk cache associated with this ImageCache object. Note that this includes
     * disk access so this should not be executed on the main/UI thread.
     */
    public void close() {
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache != null) {
                try {
                    if (!mDiskLruCache.isClosed()) {
                        mDiskLruCache.close();
                    }
                } catch (Throwable e) {
                    LogUtils.e(e.getMessage(), e);
                }
                mDiskLruCache = null;
            }
        }
    }

    private class BitmapMeta {
        public FileInputStream inputStream;
        public byte[] data;
        public long expiryTimestamp;
    }

    /**
     * 通过BitmapMeta对象构造Bitmap
     * 1.如果BitmapMeta对象输入流不为空，以输入流获取Bitmap
     * 2.如果BitmapMeta对象byte数据不为空，以此数据获取Bitmap
     */
    private Bitmap decodeBitmapMeta(BitmapMeta bitmapMeta, BitmapDisplayConfig config) throws
            IOException {
        if (bitmapMeta == null) return null;
        Bitmap bitmap = null;
        if (bitmapMeta.inputStream != null) {
            if (config == null || config.isShowOriginal()) {
                bitmap = BitmapDecoder.decodeFileDescriptor(bitmapMeta.inputStream.getFD());
            } else {
                bitmap = BitmapDecoder.decodeSampledBitmapFromDescriptor(bitmapMeta.inputStream
                        .getFD(), config.getBitmapMaxSize(), config.getBitmapConfig());
            }
        } else if (bitmapMeta.data != null) {
            if (config == null || config.isShowOriginal()) {
                bitmap = BitmapDecoder.decodeByteArray(bitmapMeta.data);
            } else {
                bitmap = BitmapDecoder.decodeSampledBitmapFromByteArray(bitmapMeta.data, config
                        .getBitmapMaxSize(), config.getBitmapConfig());
            }
        }
        return bitmap;
    }

    /**
     * 1.获取配置判断是否自动旋转
     * 2.如果是，获取Bitmap文件构造ExifInterface对象，获取图片方向参数
     * 3.如果方向不是0，矫正Bitmap方法返回
     */
    private synchronized Bitmap rotateBitmapIfNeeded(String uri, BitmapDisplayConfig config,
                                                     Bitmap bitmap) {
        Bitmap result = bitmap;
        if (config != null && config.isAutoRotation()) {
            File bitmapFile = this.getBitmapFileFromDiskCache(uri);
            if (bitmapFile != null && bitmapFile.exists()) {//获取缓存Bitmap文件对象
                ExifInterface exif = null;//这个接口提供了图片文件的旋转，gps，时间等信息。
                try {
                    exif = new ExifInterface(bitmapFile.getPath());
                } catch (Throwable e) {
                    return result;
                }
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED);//获取图片方向参数
                int angle = 0;
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        angle = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        angle = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        angle = 270;
                        break;
                    default:
                        angle = 0;
                        break;
                }
                if (angle != 0) {
                    Matrix m = new Matrix();
                    m.postRotate(angle);
                    result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap
                            .getHeight(), m, true);//重新构造Bitmap
                    bitmap.recycle();
                    bitmap = null;
                }
            }
        }
        return result;
    }

    public class MemoryCacheKey {
        private String uri;
        private String subKey;

        private MemoryCacheKey(String uri, BitmapDisplayConfig config) {
            this.uri = uri;
            this.subKey = config == null ? null : config.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MemoryCacheKey)) return false;

            MemoryCacheKey that = (MemoryCacheKey) o;

            if (!uri.equals(that.uri)) return false;

            if (subKey != null && that.subKey != null) {
                return subKey.equals(that.subKey);
            }

            return true;
        }

        @Override
        public int hashCode() {
            return uri.hashCode();
        }
    }
}
