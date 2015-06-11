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

package com.lidroid.xutils.sample.entities;

import com.lidroid.xutils.db.annotation.Column;
import com.lidroid.xutils.db.annotation.Foreign;
import com.lidroid.xutils.db.annotation.Table;
import com.lidroid.xutils.db.annotation.Transient;

/**
 * Author: wyouflf
 * Date: 13-7-29
 * Time: 涓嬪崍5:04
 */
@Table(name = "child")  // 寤鸿鍔犱笂娉ㄨВ锛�娣锋穯鍚庤〃鍚嶄笉鍙楀奖鍝�
public class Child extends EntityBase {

    @Column(column = "name")
    public String name;

    @Column(column = "email")
    private String email;

    //@Foreign(column = "parentId", foreign = "id")
    //public ForeignLazyLoader<Parent> parent;
    //@Foreign(column = "parentId", foreign = "isVIP")
    //public List<Parent> parent;
    @Foreign(column = "parentId", foreign = "id")
    public Parent parent;

    // Transient浣胯繖涓垪琚拷鐣ワ紝涓嶅瓨鍏ユ暟鎹簱
    @Transient
    public String willIgnore;

    public static String staticFieldWillIgnore; // 闈欐�瀛楁涔熶笉浼氬瓨鍏ユ暟鎹簱

    @Column(column = "text")
    private String text;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "Child{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", parent=" + parent +
                ", willIgnore='" + willIgnore + '\'' +
                ", text='" + text + '\'' +
                '}';
    }
}
