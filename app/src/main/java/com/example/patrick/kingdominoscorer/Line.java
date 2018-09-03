package com.example.patrick.kingdominoscorer;
import org.opencv.core.Point;

public class Line {
    public Point p1;
    public Point p2;
    public int gid;
    //public Group group;

    public Line(double[] line) {
        p1 = new Point(line[0], line[1]);
        p2 = new Point(line[2], line[3]);
        //group = null;
        gid = -1;
    }

    public Line(Point p1, Point p2) {
        this.p1 = p1;
        this.p2 = p2;
        gid = -1;
    }
}
