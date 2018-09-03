package com.example.patrick.kingdominoscorer;
import java.util.ArrayList;
import org.opencv.core.Point;

public class Group {
    public ArrayList<Line> lines;
    public ArrayList<Point> corners;

    public Group() {
        lines = new ArrayList<>();
        corners = new ArrayList<>();
    }

    public void destroy() {
        lines.clear();
        corners.clear();
    }
}
