package com.example.patrick.kingdominoscorer;

import java.util.Comparator;
import org.opencv.core.Point;

public class SortByX implements Comparator<Point> {
    public int compare(Point a, Point b) {
        if (a.x < b.x) {
            return -1;
        } else if (a.x == b.x) {
            return 0;
        } else {
            return 1;
        }
    }
}
