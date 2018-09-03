package com.example.patrick.kingdominoscorer;

import org.opencv.core.Point;
import org.opencv.core.Size;

public class Match {
    Point topLeft;
    Size size;
    int rotation;

    public Match() {
        topLeft = null;
        size = null;
        rotation = 0;
    }
}
