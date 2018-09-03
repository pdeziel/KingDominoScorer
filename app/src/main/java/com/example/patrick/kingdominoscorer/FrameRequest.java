package com.example.patrick.kingdominoscorer;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.ArrayList;

public class FrameRequest {
    public Mat frame;
    public ArrayList<ArrayList<Point>> rects;
    public ArrayList<Line> lines;
    public double cannyThreshold;
    public double linesThreshold;
    public double minLineLength;
    public double maxLineGap;
    public double blocksize;
    public double c;
    public double r;
    public State state;
    public Match match;

    public enum State {
        INVALID,
        PENDING,
        COMPLETED,
    }

    public FrameRequest() {
        state = FrameRequest.State.INVALID;
        frame = null;
        rects = new ArrayList<>();
        lines = new ArrayList<>();
        match = null;
    }
}
