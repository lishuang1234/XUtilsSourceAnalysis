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

package com.lidroid.xutils.bitmap;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.lidroid.xutils.bitmap.core.BitmapSize;

import java.lang.reflect.Field;

public class BitmapCommonUtils {

    private BitmapCommonUtils() {
    }

    private static BitmapSize screenSize = null;

    /**
     * 手机窗口屏幕大小
     */
    public static BitmapSize getScreenSize(Context context) {
        if (screenSize == null) {
            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            screenSize = new BitmapSize(displayMetrics.widthPixels, displayMetrics.heightPixels);
        }
        return screenSize;
    }


    /**
     * BitmapCommonUtils.java
     * 设置Bitmap大小返回BitmapSize。
     * 1.如果用户设置了最大最小值，直接构造返回BitmapSize对象。
     * 2.获取View的Layout参数设置大小
     * 3.如果上述得到值小于0，反射获取ImageView的“mMaxWidth”“mMaxHeight”值
     * 4.如果上述得到值小于0，获取窗口大小，并进行设置。
     * */
    public static BitmapSize optimizeMaxSizeByView(View view, int maxImageWidth,
                                                   int maxImageHeight) {
        int width = maxImageWidth;
        int height = maxImageHeight;

        if (width > 0 && height > 0) {
            return new BitmapSize(width, height);
        }

        final ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {//根据父容器参数设置View显示大小
            if (params.width > 0) {
                width = params.width;
            } else if (params.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
                width = view.getWidth();
            }

            if (params.height > 0) {
                height = params.height;
            } else if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                height = view.getHeight();
            }
        }

        if (width <= 0) width = getImageViewFieldValue(view, "mMaxWidth");//根据ImageView声明字段获取大小
        if (height <= 0) height = getImageViewFieldValue(view, "mMaxHeight");

        BitmapSize screenSize = getScreenSize(view.getContext());
        if (width <= 0) width = screenSize.getWidth();//根据窗口大小设置大小
        if (height <= 0) height = screenSize.getHeight();

        return new BitmapSize(width, height);
    }

    /**
     * 获得ImageView声明的mMaxWidth，mMaxHeight字段数值
     */
    private static int getImageViewFieldValue(Object object, String fieldName) {
        int value = 0;
        if (object instanceof ImageView) {
            try {
                Field field = ImageView.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                int fieldValue = (Integer) field.get(object);
                if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE) {//不是默认值
                    value = fieldValue;
                }
            } catch (Throwable e) {
            }
        }
        return value;
    }
}
