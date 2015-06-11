package com.lidroid.xutils.sample.entities;

import com.lidroid.xutils.db.annotation.Column;
import com.lidroid.xutils.db.annotation.Finder;
import com.lidroid.xutils.db.annotation.Table;
import com.lidroid.xutils.db.sqlite.FinderLazyLoader;

import java.util.Date;

/**
 * Author: wyouflf
 * Date: 13-7-25
 * Time: 涓嬪崍7:06
 */
// 寤鸿鍔犱笂娉ㄨВ锛�娣锋穯鍚庤〃鍚嶄笉鍙楀奖鍝�@Table(name = "parent", execAfterTableCreated = "CREATE UNIQUE INDEX index_name ON parent(name,email)")
public class Parent extends EntityBase {

    @Column(column = "name") // 寤鸿鍔犱笂娉ㄨВ锛�娣锋穯鍚庡垪鍚嶄笉鍙楀奖鍝�    
    public String name;

    @Column(column = "email")
    private String email;

    @Column(column = "isAdmin")
    private boolean isAdmin;

    @Column(column = "time")
    private Date time;

    @Column(column = "date")
    private java.sql.Date date;

    @Finder(valueColumn = "id", targetColumn = "parentId")
    public FinderLazyLoader<Child> children; // 鍏宠仈瀵硅薄澶氭椂寤鸿浣跨敤杩欑鏂瑰紡锛屽欢杩熷姞杞芥晥鐜囪緝楂樸�
    //@Finder(valueColumn = "id",targetColumn = "parentId")
    //public Child children;
    //@Finder(valueColumn = "id", targetColumn = "parentId")
    //private List<Child> children;

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public java.sql.Date getDate() {
        return date;
    }

    public void setDate(java.sql.Date date) {
        this.date = date;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "Parent{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", isAdmin=" + isAdmin +
                ", time=" + time +
                ", date=" + date +
                '}';
    }
}
