package dev.keyval.kvshop.frontend;

import java.util.Date;

public class Ad {
    private int id;
    private String title;
    private String description;
    private float price;
    private Date postedDate;

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public float getPrice() {
        return price;
    }

    public Date getPostedDate() {
        return postedDate;
    }
}
